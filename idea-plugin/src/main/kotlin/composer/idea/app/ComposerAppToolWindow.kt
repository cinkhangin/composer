package composer.idea.app

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import composer.idea.ComposerNotifications
import composer.idea.bridge.BridgeMsg
import composer.idea.bridge.DesignerBridge
import composer.idea.web.ComposerWebServer
import composer.idea.web.ComposerWebServerStartException
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.Timer

/**
 * The whole-app designer tool window: one JCEF designer per project showing
 * the aggregated design assembled by [ComposerAppService]. The empty state
 * offers enablement (adopt an existing MainActivity); the browser boots the
 * same bundled web app as the split editor, in embedded+app mode.
 */
class ComposerAppToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = ComposerAppPanel(project)
        val content = toolWindow.contentManager.factory.createContent(panel.component, "", false)
        Disposer.register(content, panel)
        toolWindow.contentManager.addContent(content)
    }
}

class ComposerAppPanel(private val project: Project) : com.intellij.openapi.Disposable {
    private val log = thisLogger()
    val component = JPanel(BorderLayout())
    private val cards = JPanel(CardLayout())
    private val statusLabel = JBLabel("", SwingConstants.CENTER)
    private val enableButton = JButton("Enable App Designer").apply { addActionListener { enable() } }
    private val retryButton = JButton("Retry").apply {
        isVisible = false
        addActionListener { retry() }
    }
    private val warningBanner = JBLabel("", SwingConstants.CENTER).apply {
        border = JBUI.Borders.empty(6)
        isVisible = false
    }
    private val browserContainer = JPanel(BorderLayout())
    private var browser: JBCefBrowser? = null
    private var bridge: DesignerBridge? = null
    private var bootTimer: Timer? = null
    private var rev = 0
    private var lastIncomingRev = 0
    private var lastSelectionId: String? = null
    private var suppressCaretEvents = false
    private var caretSyncInstalled = false
    private val selectionQueue = MergingUpdateQueue("composer-app-selection", 200, true, null, this)

    private val service get() = ComposerAppService.getInstance(project)
    private val settings get() = project.service<ComposerAppSettings>()

    init {
        val statusPanel = JPanel(GridBagLayout())
        val column = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            statusLabel.alignmentX = JComponent.CENTER_ALIGNMENT
            enableButton.alignmentX = JComponent.CENTER_ALIGNMENT
            retryButton.alignmentX = JComponent.CENTER_ALIGNMENT
            add(statusLabel)
            add(Box.createVerticalStrut(JBUI.scale(8)))
            add(enableButton)
            add(retryButton)
        }
        statusPanel.add(column, GridBagConstraints())
        browserContainer.add(warningBanner, BorderLayout.NORTH)
        cards.add(statusPanel, CARD_STATUS)
        cards.add(browserContainer, CARD_BROWSER)
        component.add(cards, BorderLayout.CENTER)

        service.onStatus = { msg ->
            warningBanner.text = msg ?: ""
            warningBanner.isVisible = msg != null
        }

        if (settings.state.enabled && settings.state.mainActivityUrl.isNotEmpty()) {
            val vf = VirtualFileManager.getInstance().findFileByUrl(settings.state.mainActivityUrl)
            if (vf != null && vf.isValid) {
                startWith(vf)
            } else {
                showStatus("The configured MainActivity is gone — enable again.", enable = true)
            }
        } else {
            showStatus("Design your whole app: screens, ViewModels, and navigation.", enable = true)
        }
    }

    private fun showStatus(text: String, enable: Boolean = false, retry: Boolean = false) {
        statusLabel.text = text
        enableButton.isVisible = enable
        retryButton.isVisible = retry
        (cards.layout as CardLayout).show(cards, CARD_STATUS)
    }

    /** Find a MainActivity with a NavDisplay and adopt it (scaffolding comes later). */
    private fun enable() {
        showStatus("Looking for MainActivity.kt…")
        ReadAction.nonBlocking<VirtualFile?> {
            FilenameIndex.getVirtualFilesByName("MainActivity.kt", GlobalSearchScope.projectScope(project))
                .firstOrNull { vf ->
                    runCatching {
                        com.intellij.openapi.fileEditor.impl.LoadTextUtil.loadText(vf).contains("NavDisplay")
                    }.getOrDefault(false)
                }
        }
            .expireWith(this)
            .finishOnUiThread(ModalityState.defaultModalityState()) { vf ->
                if (vf == null) {
                    // Nothing to adopt — offer to scaffold a fresh app skeleton.
                    val created = ComposerAppScaffold.scaffold(project)
                    if (created == null) {
                        showStatus("Design your whole app: screens, ViewModels, and navigation.", enable = true)
                    } else {
                        settings.state.enabled = true
                        settings.state.mainActivityUrl = created.url
                        startWith(created)
                    }
                } else {
                    settings.state.enabled = true
                    settings.state.mainActivityUrl = vf.url
                    ComposerAppScaffold.notifyDepsIfMissing(project)
                    startWith(vf)
                }
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun startWith(main: VirtualFile) {
        service.start(main)
        installCaretSync()
        ensureBrowser()
    }

    /** IDE caret in an owned file → designer selection (debounced, deduped). */
    private fun installCaretSync() {
        if (caretSyncInstalled) return
        caretSyncInstalled = true
        EditorFactory.getInstance().eventMulticaster.addCaretListener(
            object : CaretListener {
                override fun caretPositionChanged(event: CaretEvent) {
                    if (suppressCaretEvents) return
                    val editor = event.editor
                    if (editor.project != project) return
                    val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
                    if (!service.ownsFile(file)) return
                    selectionQueue.queue(
                        Update.create("caret") {
                            val id = service.nodeIdAt(file, editor.caretModel.offset) ?: return@create
                            if (id == lastSelectionId) return@create
                            lastSelectionId = id
                            bridge?.send(BridgeMsg(type = "selectNode", rev = ++rev, nodeId = id))
                        },
                    )
                }
            },
            this,
        )
    }

    /** Designer selection → open the owning file and place the caret (no focus steal). */
    private fun onDesignerSelection(nodeId: String?) {
        lastSelectionId = nodeId
        if (nodeId == null) return
        val (vf, range) = service.sourceRangeOf(nodeId) ?: return
        suppressCaretEvents = true
        try {
            FileEditorManager.getInstance(project)
                .openTextEditor(OpenFileDescriptor(project, vf, range.first), false)
        } finally {
            suppressCaretEvents = false
        }
    }

    private fun ensureBrowser() {
        if (browser != null) return
        if (!JBCefApp.isSupported()) {
            showStatus("The Composer designer needs JCEF, which this IDE runtime doesn't provide.")
            return
        }
        val url = try {
            service<ComposerWebServer>().baseUrl
        } catch (e: ComposerWebServerStartException) {
            log.warn(e)
            showStatus(e.message ?: "Composer's local web server failed to start.", retry = true)
            return
        }
        showStatus("Loading designer…")
        val b = JBCefBrowser.createBuilder().setOffScreenRendering(false).build()
        b.setErrorPage(JBCefBrowserBase.ErrorPage.DEFAULT)
        Disposer.register(this, b)
        val br = DesignerBridge(b)
        Disposer.register(this, br)
        br.onMessage = ::onBridgeMessage
        br.onUndecodable = {
            ComposerNotifications.warnOnce(project, "composer.app.bridge", "Composer App Designer received an undecodable message.")
        }
        browser = b
        bridge = br
        service.pushDesign = { json ->
            bridge?.send(BridgeMsg(type = "loadDesign", rev = ++rev, design = json, appMode = true))
        }
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(LafManagerListener.TOPIC, LafManagerListener { pushTheme() })
        browserContainer.add(b.component, BorderLayout.CENTER)
        component.revalidate()
        b.loadURL(url + "?embedded=1")
        bootTimer = Timer(BOOT_TIMEOUT_MS) { onBootTimeout() }.apply {
            isRepeats = false
            start()
        }
    }

    private fun teardownBrowser() {
        bootTimer?.stop()
        bootTimer = null
        service.pushDesign = null
        bridge?.let(Disposer::dispose)
        bridge = null
        browser?.let {
            browserContainer.remove(it.component)
            Disposer.dispose(it)
        }
        browser = null
        lastIncomingRev = 0
    }

    private fun retry() {
        teardownBrowser()
        ensureBrowser()
    }

    private fun onBootTimeout() {
        if (browser == null) return
        teardownBrowser()
        showStatus("The Composer designer failed to load.", retry = true)
    }

    private fun pushTheme() {
        bridge?.send(BridgeMsg(type = "setTheme", dark = !JBColor.isBright()))
    }

    private fun onBridgeMessage(msg: BridgeMsg) {
        if (msg.type == "ready") {
            lastIncomingRev = 0
            bootTimer?.stop()
            bootTimer = null
            (cards.layout as CardLayout).show(cards, CARD_BROWSER)
            pushTheme()
            service.repushForNewDesigner()
            return
        }
        if (msg.rev <= lastIncomingRev) return
        lastIncomingRev = msg.rev
        when (msg.type) {
            "designChanged" -> msg.design?.let { service.applyDesignerEdit(it) }
            "selectionChanged" -> onDesignerSelection(msg.nodeId)
        }
    }

    override fun dispose() {
        teardownBrowser()
        service.pushDesign = null
        service.onStatus = null
    }

    private companion object {
        const val CARD_STATUS = "status"
        const val CARD_BROWSER = "browser"
        const val BOOT_TIMEOUT_MS = 15_000
    }
}
