package composer.idea.editor

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import composer.idea.ComposerNotifications
import composer.idea.bridge.BridgeMsg
import composer.idea.bridge.DesignerBridge
import composer.idea.sync.DesignSyncController
import composer.idea.web.ComposerWebServer
import composer.idea.web.ComposerWebServerStartException
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.beans.PropertyChangeListener
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.Timer

/**
 * The visual-designer half of the split editor: a JCEF browser running the
 * bundled Composer web app in embedded mode, wired to the IDE over
 * [DesignerBridge]. The browser is created lazily on first SHOW (JCEF + a wasm
 * boot per open file would be too heavy eagerly) and torn down with the editor.
 *
 * A CardLayout switches between a status card ("Loading designer…", or an
 * error label + Retry when the server can't start / the designer never sends
 * `ready` within [BOOT_TIMEOUT_MS]) and the browser card, which carries a
 * top banner when the file parses to no screens.
 *
 * Selection sync: designer `selectionChanged` moves the paired text editor's
 * caret to the node's source range (without stealing focus); caret moves send
 * `selectNode` (debounced, deepest parsed node containing the offset).
 */
class ComposerPreviewEditor(
    private val project: Project,
    private val file: VirtualFile,
    private val textEditor: TextEditor? = null,
) : UserDataHolderBase(), FileEditor {
    private val log = thisLogger()
    private val panel = JPanel(BorderLayout())
    private val cards = JPanel(CardLayout())
    private val statusLabel = JBLabel("", SwingConstants.CENTER)
    private val retryButton = JButton("Retry").apply {
        isVisible = false
        addActionListener { retry() }
    }
    private val noScreensBanner = JBLabel(
        "No previewable composables — screens are zero-parameter @Composable functions.",
        SwingConstants.CENTER,
    ).apply {
        border = JBUI.Borders.empty(6)
        isVisible = false
    }
    private val browserContainer = JPanel(BorderLayout())
    private var browser: JBCefBrowser? = null
    private var bridge: DesignerBridge? = null
    private var sync: DesignSyncController? = null
    private var bootTimer: Timer? = null
    private var rev = 0

    /** Highest rev seen from the web side; stale (`<=`) messages are dropped. */
    private var lastIncomingRev = 0

    /** Last node id exchanged with the designer — don't echo the same selection. */
    private var lastSelectionId: String? = null
    private var suppressCaretEvents = false
    private var caretSyncInstalled = false
    private val selectionQueue = MergingUpdateQueue("composer-selection-sync", 200, true, null, this)

    private val showListener = HierarchyListener { e ->
        if ((e.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong()) != 0L && panel.isShowing) {
            ensureBrowser()
        }
    }

    init {
        val statusPanel = JPanel(GridBagLayout())
        val statusColumn = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            statusLabel.alignmentX = JComponent.CENTER_ALIGNMENT
            retryButton.alignmentX = JComponent.CENTER_ALIGNMENT
            add(statusLabel)
            add(Box.createVerticalStrut(JBUI.scale(8)))
            add(retryButton)
        }
        statusPanel.add(statusColumn, GridBagConstraints())
        browserContainer.add(noScreensBanner, BorderLayout.NORTH)
        cards.add(statusPanel, CARD_STATUS)
        cards.add(browserContainer, CARD_BROWSER)
        panel.add(cards, BorderLayout.CENTER)
        panel.addHierarchyListener(showListener)
    }

    private fun showStatus(text: String, retry: Boolean) {
        statusLabel.text = text
        retryButton.isVisible = retry
        (cards.layout as CardLayout).show(cards, CARD_STATUS)
    }

    private fun showBrowser() {
        (cards.layout as CardLayout).show(cards, CARD_BROWSER)
    }

    private fun ensureBrowser() {
        if (browser != null) return
        panel.removeHierarchyListener(showListener)
        if (!JBCefApp.isSupported()) {
            showStatus("The Composer designer needs JCEF, which this IDE runtime doesn't provide.", retry = false)
            return
        }
        val url = try {
            service<ComposerWebServer>().baseUrl
        } catch (e: ComposerWebServerStartException) {
            log.warn(e)
            showStatus(e.message ?: "Composer's local web server failed to start.", retry = true)
            return
        }
        showStatus("Loading designer…", retry = false)
        // Windowed (non-OSR) rendering: best canvas/Skia throughput for the wasm app.
        val b = JBCefBrowser.createBuilder().setOffScreenRendering(false).build()
        b.setErrorPage(JBCefBrowserBase.ErrorPage.DEFAULT) // load failures get a reload page
        Disposer.register(this, b)
        val br = DesignerBridge(b) // must exist before loadURL (JSQuery requirement)
        Disposer.register(this, br)
        br.onMessage = ::onBridgeMessage
        br.onUndecodable = {
            ComposerNotifications.warnOnce(
                project,
                "composer.bridge.decode:${file.path}",
                "Composer received an undecodable designer message for ${file.name}.",
            )
        }
        browser = b
        bridge = br
        val s = DesignSyncController(
            project,
            file,
            push = { json -> bridge?.send(BridgeMsg(type = "loadDesign", rev = ++rev, design = json)) },
            onScreensChanged = { hasScreens -> noScreensBanner.isVisible = !hasScreens },
        )
        Disposer.register(this, s)
        sync = s
        installCaretSync()
        // Designer chrome follows the IDE look-and-feel, live.
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(LafManagerListener.TOPIC, LafManagerListener { pushTheme() })
        browserContainer.add(b.component, BorderLayout.CENTER)
        panel.revalidate()
        b.loadURL(url + "?embedded=1")
        bootTimer = Timer(BOOT_TIMEOUT_MS) { onBootTimeout() }.apply {
            isRepeats = false
            start()
        }
    }

    /** Tear down the browser + bridge + sync so [ensureBrowser] can start fresh. */
    private fun teardownBrowser() {
        bootTimer?.stop()
        bootTimer = null
        sync?.let(Disposer::dispose)
        sync = null
        bridge?.let(Disposer::dispose)
        bridge = null
        browser?.let {
            browserContainer.remove(it.component)
            Disposer.dispose(it)
        }
        browser = null
        noScreensBanner.isVisible = false
        lastIncomingRev = 0
        lastSelectionId = null
    }

    private fun retry() {
        teardownBrowser()
        ensureBrowser()
    }

    private fun onBootTimeout() {
        if (browser == null) return
        log.warn("Composer designer didn't report ready within ${BOOT_TIMEOUT_MS}ms for ${file.name}")
        teardownBrowser()
        showStatus("The Composer designer failed to load.", retry = true)
    }

    private fun pushTheme() {
        bridge?.send(BridgeMsg(type = "setTheme", dark = !JBColor.isBright()))
    }

    private fun onBridgeMessage(msg: BridgeMsg) {
        // The designer is up — theme it like the IDE, then parse the open file
        // and start following the document. A reload restarts the web side's
        // rev counter, so the staleness tracker resets with it.
        if (msg.type == "ready") {
            lastIncomingRev = 0
            bootTimer?.stop()
            bootTimer = null
            showBrowser()
            pushTheme()
            sync?.start()
            return
        }
        if (msg.rev <= lastIncomingRev) return // stale (out-of-order) message
        lastIncomingRev = msg.rev
        when (msg.type) {
            // Designer edit → regenerate only the changed functions in the document.
            "designChanged" -> msg.design?.let { sync?.applyDesignerEdit(it) }
            // Designer selection → caret on the node's source (no focus steal).
            "selectionChanged" -> onDesignerSelection(msg.nodeId)
        }
    }

    // ---- selection sync ---------------------------------------------------------

    private fun installCaretSync() {
        if (caretSyncInstalled) return
        val editor = textEditor?.editor ?: return
        editor.caretModel.addCaretListener(
            object : CaretListener {
                override fun caretPositionChanged(event: CaretEvent) {
                    if (suppressCaretEvents) return
                    selectionQueue.queue(Update.create("caret") { sendCaretSelection() })
                }
            },
            this,
        )
        caretSyncInstalled = true
    }

    private fun sendCaretSelection() {
        val editor = textEditor?.editor ?: return
        if (editor.isDisposed) return
        val id = sync?.nodeIdAt(editor.caretModel.offset) ?: return
        if (id == lastSelectionId) return
        lastSelectionId = id
        bridge?.send(BridgeMsg(type = "selectNode", rev = ++rev, nodeId = id))
    }

    private fun onDesignerSelection(nodeId: String?) {
        lastSelectionId = nodeId // never echo the designer's own selection back
        if (nodeId == null) return
        val editor = textEditor?.editor ?: return
        if (editor.isDisposed) return
        val range = sync?.rangeOf(nodeId) ?: return
        val offset = range.first.coerceIn(0, editor.document.textLength)
        suppressCaretEvents = true
        try {
            editor.caretModel.moveToOffset(offset)
            editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
        } finally {
            suppressCaretEvents = false
        }
    }

    override fun getComponent(): JComponent = panel
    override fun getPreferredFocusedComponent(): JComponent = browser?.component ?: panel
    override fun getName(): String = "Design"
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = file.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun getFile(): VirtualFile = file

    override fun dispose() {
        bootTimer?.stop()
        bootTimer = null
        panel.removeHierarchyListener(showListener)
        // Browser + bridge + sync are Disposer children of this editor.
    }

    private companion object {
        const val CARD_STATUS = "status"
        const val CARD_BROWSER = "browser"
        const val BOOT_TIMEOUT_MS = 15_000
    }
}
