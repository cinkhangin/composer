package composer

import androidx.compose.ui.awt.ComposePanel
import composer.ui.AppTheme
import javax.swing.JComponent

/**
 * The Android Studio designer, hosted in a Swing [ComposePanel]. The host talks
 * the existing JSON envelope: call [deliver] for
 * host→designer messages (loadDesign / selectNode / setTheme) and receive
 * designer→host ones (ready / designChanged / selectionChanged) via
 * [onMessage] — all existing plugin-side handlers and guards apply verbatim.
 *
 * Each panel owns its own session, so tool windows from multiple projects can
 * run concurrently in one IDE process.
 */
class DesignerConnection internal constructor(
    private val panel: ComposePanel,
    private val session: DesignerSession,
) {
    /** The Swing component to embed. */
    val component: JComponent get() = panel

    /** Deliver one host→designer envelope. */
    fun deliver(json: String) = session.deliver(json)

    /** Deliver one native IDE trackpad pinch delta to the canvas. */
    fun magnifyCanvas(delta: Float) = session.magnifyCanvas(delta)

    /** Detach the host wiring; the panel itself dies with its Swing hierarchy. */
    fun dispose() {
        session.dispose()
        unregisterCursorHost(panel)
    }
}

/**
 * Create the designer panel. [onMessage] receives designer→host envelopes
 * (starting with `ready` once the editor composes and pumps its outbox).
 */
fun createDesignerPanel(onMessage: (String) -> Unit): DesignerConnection {
    val session = DesignerSession(onMessage)
    val panel = ComposePanel()
    registerCursorHost(panel)
    panel.setContent {
        AppTheme { EditorScreen(session) }
    }
    return DesignerConnection(panel, session)
}
