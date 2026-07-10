package composer

import java.awt.Component
import java.awt.Cursor
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.util.prefs.Preferences
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

/**
 * JVM (in-process IDE designer) actuals for the [Platform.kt] seams. The
 * designer panel registers its Swing component via [registerCursorHost] so the
 * canvas can drive the AWT cursor; file pickers use plain Swing choosers.
 */

private val prefs: Preferences = Preferences.userRoot().node("composer-designer")

actual fun prefGet(key: String): String? = prefs.get(key, null)

actual fun prefSet(key: String, value: String) = prefs.put(key, value)

actual fun copyToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

actual fun downloadText(filename: String, content: String, mime: String) {
    val chooser = JFileChooser().apply { selectedFile = java.io.File(filename) }
    if (chooser.showSaveDialog(cursorHost) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile?.writeText(content)
    }
}

actual fun importTextFile(accept: String, onText: (String) -> Unit) {
    val chooser = JFileChooser()
    if (chooser.showOpenDialog(cursorHost) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile?.takeIf { it.isFile }?.let { onText(it.readText()) }
    }
}

actual fun openUrl(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }
}

/** No web boot loader on the JVM host. */
actual fun dismissBootLoader() {}

/** The IDE panel has an explicit lifecycle; the bridge owns persistence. */
actual fun registerUnloadFlush(flush: () -> Unit): () -> Unit = {}

// ---- Cursor / focus on the hosting ComposePanel ----

private var cursorHost: Component? = null

/** Called by the designer panel factory so [setCanvasCursor] has a target. */
fun registerCursorHost(component: Component) {
    cursorHost = component
}

actual fun setCanvasCursor(cursor: String) {
    val host = cursorHost ?: return
    val awt = when (cursor) {
        "ew-resize" -> Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)
        "ns-resize" -> Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR)
        "nwse-resize" -> Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR)
        "nesw-resize" -> Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR)
        "grabbing", "grab" -> Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
        else -> Cursor.getDefaultCursor()
    }
    SwingUtilities.invokeLater { host.cursor = awt }
}

actual fun focusComposeCanvas() {
    cursorHost?.let { SwingUtilities.invokeLater { it.requestFocusInWindow() } }
}

// ---- URL routing: the IDE designer has no routes. ----

actual fun pathIsEdit(): Boolean = false

actual fun pathId(): String? = null

actual fun pushPath(path: String) {}
