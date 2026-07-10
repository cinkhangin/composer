package composer

import androidx.compose.runtime.remember
import androidx.compose.ui.awt.ComposePanel
import composer.ui.AppTheme
import javax.swing.JComponent

/**
 * The in-process IDE designer (J2): the same editor the web app runs, hosted
 * in a Swing [ComposePanel] — no wasm, no JCEF, no web server. The host talks
 * the SAME JSON envelope as the JCEF bridge ([BridgeMsg]): call [deliver] for
 * host→designer messages (loadDesign / selectNode / setTheme) and receive
 * designer→host ones (ready / designChanged / selectionChanged) via
 * [onMessage] — all existing plugin-side handlers and guards apply verbatim.
 *
 * One designer instance per process for now: the transport and [EmbeddedBridge]
 * are singletons (matching the one-per-page web model), so composing a second
 * panel takes the connection over from the first.
 */
class DesignerConnection internal constructor(private val panel: ComposePanel) {
    /** The Swing component to embed. */
    val component: JComponent get() = panel

    /** Deliver one host→designer envelope. */
    fun deliver(json: String) = DesignerHostTransport.deliver(json)
}

/**
 * Create the designer panel. [onMessage] receives designer→host envelopes
 * (starting with `ready` once the editor composes and pumps its outbox).
 */
fun createDesignerPanel(onMessage: (String) -> Unit): DesignerConnection {
    // Order matters: activate before the first EmbeddedBridge.active read (a lazy).
    DesignerHostTransport.activate()
    DesignerHostTransport.hostSink = onMessage
    val panel = ComposePanel()
    registerCursorHost(panel)
    panel.setContent {
        AppTheme { EditorScreen(remember { Workspace() }, embedded = true) }
    }
    return DesignerConnection(panel)
}
