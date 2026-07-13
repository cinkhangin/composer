package composer.idea.editor

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
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
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import composer.idea.ComposerNotifications
import composer.idea.bridge.BridgeMsg
import composer.idea.bridge.DesignerHost
import composer.idea.bridge.DesignerHosts
import composer.idea.sync.DesignSyncController
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * The visual half of the Code | Split | Design editor. A dedicated in-process
 * ComposePanel is created lazily when the Design pane is first shown. Each pane
 * owns an independent host/session, so multiple files and the app tool window
 * can remain open together.
 */
class ComposerPreviewEditor(
    private val project: Project,
    private val file: VirtualFile,
    private val textEditor: TextEditor? = null,
) : UserDataHolderBase(), FileEditor {
    private val panel = JPanel(BorderLayout())
    private val cards = JPanel(CardLayout())
    private val statusLabel = JBLabel("Loading designer…", SwingConstants.CENTER)
    private val noScreensBanner = JBLabel(
        "No previewable composables — screens are top-level @Composable functions.",
        SwingConstants.CENTER,
    ).apply {
        border = JBUI.Borders.empty(6)
        isVisible = false
    }
    private val designerContainer = JPanel(BorderLayout())
    private var host: DesignerHost? = null
    private var sync: DesignSyncController? = null
    private var rev = 0
    private var lastIncomingRev = 0
    private var lastSelectionId: String? = null
    private var suppressCaretEvents = false
    private var caretSyncInstalled = false
    private val selectionQueue = MergingUpdateQueue("composer-selection-sync", 200, true, null, this)

    private val showListener = HierarchyListener { e ->
        if ((e.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong()) != 0L && panel.isShowing) {
            ensureDesigner()
        }
    }

    init {
        val statusPanel = JPanel(GridBagLayout()).apply {
            add(statusLabel, GridBagConstraints())
        }
        designerContainer.add(noScreensBanner, BorderLayout.NORTH)
        cards.add(statusPanel, CARD_STATUS)
        cards.add(designerContainer, CARD_DESIGNER)
        panel.add(cards, BorderLayout.CENTER)
        panel.addHierarchyListener(showListener)
    }

    private fun showStatus(text: String) {
        statusLabel.text = text
        (cards.layout as CardLayout).show(cards, CARD_STATUS)
    }

    private fun showDesigner() {
        (cards.layout as CardLayout).show(cards, CARD_DESIGNER)
    }

    private fun ensureDesigner() {
        if (host != null) return
        panel.removeHierarchyListener(showListener)
        showStatus("Loading designer…")
        val h = when (val result = DesignerHosts.create(this)) {
            is DesignerHosts.Result.Failed -> {
                showStatus(result.message)
                return
            }
            is DesignerHosts.Result.Ok -> result.host
        }
        host = h
        h.onMessage = ::onHostMessage
        h.onUndecodable = {
            ComposerNotifications.warnOnce(
                project,
                "composer.bridge.decode:${file.path}",
                "Composer received an undecodable designer message for ${file.name}.",
            )
        }
        val controller = DesignSyncController(
            project,
            file,
            push = { json -> host?.send(BridgeMsg(type = "loadDesign", rev = ++rev, design = json)) },
            onScreensChanged = { hasScreens -> noScreensBanner.isVisible = !hasScreens },
        )
        Disposer.register(this, controller)
        sync = controller
        installCaretSync()
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(LafManagerListener.TOPIC, LafManagerListener { pushTheme() })
        designerContainer.add(h.component, BorderLayout.CENTER)
        panel.revalidate()
        h.load()
    }

    private fun teardownDesigner() {
        sync?.let(Disposer::dispose)
        sync = null
        host?.let {
            designerContainer.remove(it.component)
            Disposer.dispose(it)
        }
        host = null
        noScreensBanner.isVisible = false
        lastIncomingRev = 0
        lastSelectionId = null
    }

    private fun pushTheme() {
        host?.send(BridgeMsg(type = "setTheme", dark = !JBColor.isBright()))
    }

    private fun onHostMessage(msg: BridgeMsg) {
        if (msg.type == "ready") {
            lastIncomingRev = 0
            showDesigner()
            pushTheme()
            sync?.start()
            return
        }
        if (msg.rev <= lastIncomingRev) return
        lastIncomingRev = msg.rev
        when (msg.type) {
            "designChanged" -> msg.design?.let { sync?.applyDesignerEdit(it) }
            "selectionChanged" -> onDesignerSelection(msg.nodeId)
        }
    }

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
        host?.send(BridgeMsg(type = "selectNode", rev = ++rev, nodeId = id))
    }

    private fun onDesignerSelection(nodeId: String?) {
        lastSelectionId = nodeId
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
    override fun getPreferredFocusedComponent(): JComponent = host?.component ?: panel
    override fun getName(): String = "Design"
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = file.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun getFile(): VirtualFile = file

    override fun dispose() {
        panel.removeHierarchyListener(showListener)
        teardownDesigner()
    }

    private companion object {
        const val CARD_STATUS = "status"
        const val CARD_DESIGNER = "designer"
    }
}
