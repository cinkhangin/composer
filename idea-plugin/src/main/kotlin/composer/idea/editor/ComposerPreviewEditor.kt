package composer.idea.editor

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import composer.idea.bridge.BridgeMsg
import composer.idea.bridge.DesignerBridge
import composer.idea.sync.DesignSyncController
import composer.idea.web.ComposerWebServer
import java.awt.BorderLayout
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * The visual-designer half of the split editor: a JCEF browser running the
 * bundled Composer web app in embedded mode, wired to the IDE over
 * [DesignerBridge]. The browser is created lazily on first SHOW (JCEF + a wasm
 * boot per open file would be too heavy eagerly) and torn down with the editor.
 *
 * P3 skeleton behavior: pushes a hardcoded sample design on `ready` and logs
 * `designChanged` round-trips. The real parse/write-back sync lands next.
 */
class ComposerPreviewEditor(
    @Suppress("unused") private val project: Project,
    private val file: VirtualFile,
) : UserDataHolderBase(), FileEditor {
    private val log = thisLogger()
    private val panel = JPanel(BorderLayout())
    private var browser: JBCefBrowser? = null
    private var bridge: DesignerBridge? = null
    private var sync: DesignSyncController? = null
    private var rev = 0

    private val showListener = HierarchyListener { e ->
        if ((e.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong()) != 0L && panel.isShowing) {
            ensureBrowser()
        }
    }

    init {
        panel.addHierarchyListener(showListener)
    }

    private fun ensureBrowser() {
        if (browser != null) return
        panel.removeHierarchyListener(showListener)
        if (!JBCefApp.isSupported()) {
            panel.add(
                JBLabel(
                    "The Composer designer needs JCEF, which this IDE runtime doesn't provide.",
                    SwingConstants.CENTER,
                ),
                BorderLayout.CENTER,
            )
            panel.revalidate()
            return
        }
        // Windowed (non-OSR) rendering: best canvas/Skia throughput for the wasm app.
        val b = JBCefBrowser.createBuilder().setOffScreenRendering(false).build()
        Disposer.register(this, b)
        val br = DesignerBridge(b) // must exist before loadURL (JSQuery requirement)
        Disposer.register(this, br)
        br.onMessage = ::onBridgeMessage
        browser = b
        bridge = br
        val s = DesignSyncController(project, file) { json ->
            bridge?.send(BridgeMsg(type = "loadDesign", rev = ++rev, design = json))
        }
        Disposer.register(this, s)
        sync = s
        panel.add(b.component, BorderLayout.CENTER)
        panel.revalidate()
        b.loadURL(service<ComposerWebServer>().baseUrl + "?embedded=1")
    }

    private fun onBridgeMessage(msg: BridgeMsg) {
        when (msg.type) {
            // The designer is up — parse the open file and start following the document.
            "ready" -> sync?.start()
            // Write-back lands next; for now designer edits are observed only.
            "designChanged" ->
                log.info("Composer designChanged rev=${msg.rev}, ${msg.design?.length ?: 0} chars from ${file.name}")
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
        panel.removeHierarchyListener(showListener)
        // Browser + bridge are Disposer children of this editor.
    }
}
