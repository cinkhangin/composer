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
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import composer.idea.ComposerNotifications
import composer.idea.bridge.BridgeMsg
import composer.idea.bridge.DesignerHost
import composer.idea.bridge.DesignerHosts
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

/**
 * The whole-app designer tool window: one designer per project showing the
 * aggregated design assembled by [ComposerAppService]. The empty state offers
 * enablement (adopt an existing MainActivity); the designer is an in-process
 * Compose panel provided by [DesignerHosts].
 */
class ComposerAppToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        thisLogger().info("Composer createToolWindowContent")
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
    private val enableButton = JButton("Enable App Designer").apply {
        addActionListener { this@ComposerAppPanel.enableAppDesigner() }
    }
    private val retryButton = JButton("Retry").apply {
        isVisible = false
        addActionListener { this@ComposerAppPanel.retry() }
    }
    private val warningBanner = JBLabel("", SwingConstants.CENTER).apply {
        border = JBUI.Borders.empty(6)
        isVisible = false
    }
    private val designerContainer = JPanel(BorderLayout())
    private var host: DesignerHost? = null
    private var rev = 0
    private var lastIncomingRev = 0
    private var lastSelectionId: String? = null
    private var suppressCaretEvents = false
    private var caretSyncInstalled = false
    private val selectionQueue = MergingUpdateQueue("composer-app-selection", 200, true, null, this)

    private val service get() = ComposerAppService.getInstance(project)
    private val settings get() = project.service<ComposerAppSettings>()

    init {
        log.info("Composer panel init (enabled=${settings.state.enabled}, url=${settings.state.mainActivityUrl})")
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
        designerContainer.add(warningBanner, BorderLayout.NORTH)
        cards.add(statusPanel, CARD_STATUS)
        cards.add(designerContainer, CARD_DESIGNER)
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
            autoAdopt()
        }
    }

    /**
     * Adopt silently when the project already has a NavDisplay MainActivity —
     * the designer should just appear; the button stays for the scaffold path.
     */
    private fun autoAdopt() {
        findAdoptableMainActivity { vf ->
            if (vf != null && host == null) {
                log.info("Composer auto-adopting ${vf.path}")
                settings.state.enabled = true
                settings.state.mainActivityUrl = vf.url
                ComposerAppScaffold.notifyDepsIfMissing(project)
                startWith(vf)
            }
        }
    }

    private fun findAdoptableMainActivity(onDone: (VirtualFile?) -> Unit) {
        ReadAction.nonBlocking<VirtualFile?> {
            FilenameIndex.getVirtualFilesByName("MainActivity.kt", GlobalSearchScope.projectScope(project))
                .firstOrNull { vf ->
                    runCatching {
                        com.intellij.openapi.fileEditor.impl.LoadTextUtil.loadText(vf).contains("NavDisplay")
                    }.getOrDefault(false)
                }
        }
            .inSmartMode(project) // FilenameIndex during indexing would fail the promise silently
            .expireWith(this)
            .finishOnUiThread(ModalityState.defaultModalityState(), onDone)
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun showStatus(text: String, enable: Boolean = false, retry: Boolean = false) {
        statusLabel.text = text
        enableButton.isVisible = enable
        retryButton.isVisible = retry
        (cards.layout as CardLayout).show(cards, CARD_STATUS)
    }

    /** Find a MainActivity with a NavDisplay and adopt it, else offer to scaffold. */
    private fun enableAppDesigner() {
        log.info("Composer enable clicked")
        showStatus("Looking for MainActivity.kt…")
        findAdoptableMainActivity { vf ->
            log.info("Composer adoption search finished: ${vf?.path ?: "none found"}")
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
    }

    private fun startWith(main: VirtualFile) {
        service.start(main)
        installCaretSync()
        ensureDesigner()
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
                            host?.send(BridgeMsg(type = "selectNode", rev = ++rev, nodeId = id))
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

    private fun ensureDesigner() {
        log.info("Composer ensureDesigner (host=${host != null})")
        if (host != null) return
        val h = when (val r = DesignerHosts.create(this)) {
            is DesignerHosts.Result.Failed -> {
                log.warn("Designer host unavailable: ${r.message}")
                showStatus(r.message, retry = r.retryable)
                return
            }
            is DesignerHosts.Result.Ok -> r.host
        }
        log.info("Composer designer host: ${h.javaClass.simpleName}")
        host = h
        h.onMessage = ::onBridgeMessage
        h.onUndecodable = {
            ComposerNotifications.warnOnce(project, "composer.app.bridge", "Composer App Designer received an undecodable message.")
        }
        service.pushDesign = { json ->
            host?.send(BridgeMsg(type = "loadDesign", rev = ++rev, design = json, appMode = true))
        }
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(LafManagerListener.TOPIC, LafManagerListener { pushTheme() })
        designerContainer.add(h.component, BorderLayout.CENTER)
        component.revalidate()
        (cards.layout as CardLayout).show(cards, CARD_DESIGNER)
        h.load()
    }

    private fun teardownDesigner() {
        service.pushDesign = null
        host?.let {
            designerContainer.remove(it.component)
            Disposer.dispose(it)
        }
        host = null
        lastIncomingRev = 0
    }

    private fun retry() {
        teardownDesigner()
        ensureDesigner()
    }

    private fun pushTheme() {
        host?.send(BridgeMsg(type = "setTheme", dark = !JBColor.isBright()))
    }

    private fun onBridgeMessage(msg: BridgeMsg) {
        log.info("Composer bridge message: ${msg.type}")
        if (msg.type == "ready") {
            lastIncomingRev = 0
            (cards.layout as CardLayout).show(cards, CARD_DESIGNER)
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
        teardownDesigner()
        service.pushDesign = null
        service.onStatus = null
    }

    private companion object {
        const val CARD_STATUS = "status"
        const val CARD_DESIGNER = "designer"
    }
}
