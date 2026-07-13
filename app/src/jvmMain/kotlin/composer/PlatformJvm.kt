package composer

import java.awt.Component
import java.awt.Cursor
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.util.prefs.Preferences
import java.util.Collections
import java.util.WeakHashMap
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

/**
 * JVM (in-process IDE designer) actuals for the [Platform.kt] seams. The
 * designer panel registers its Swing component via [registerCursorHost] so the
 * canvas can drive the AWT cursor.
 */

private val prefs: Preferences = Preferences.userRoot().node("composer-designer")

actual fun prefGet(key: String): String? = prefs.get(key, null)

actual fun prefSet(key: String, value: String) = prefs.put(key, value)

actual fun copyToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

actual fun downloadText(filename: String, content: String, mime: String) {
    val chooser = JFileChooser().apply { selectedFile = java.io.File(filename) }
    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile?.writeText(content)
    }
}

actual fun importTextFile(accept: String, onText: (String) -> Unit) {
    val chooser = JFileChooser()
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile?.takeIf { it.isFile }?.let { onText(it.readText()) }
    }
}

actual fun openUrl(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }
}

actual fun dismissBootLoader() = Unit

actual fun registerUnloadFlush(flush: () -> Unit): () -> Unit = {}

// ---- Cursor / focus on the hosting ComposePanel ----

private val cursorHosts: MutableSet<Component> =
    Collections.newSetFromMap(WeakHashMap<Component, Boolean>())

/** Called by the designer panel factory so [setCanvasCursor] has a target. */
fun registerCursorHost(component: Component) {
    cursorHosts += component
}

fun unregisterCursorHost(component: Component) {
    cursorHosts -= component
}

actual fun setCanvasCursor(cursor: String) {
    val awt = when (cursor) {
        "ew-resize" -> Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)
        "ns-resize" -> Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR)
        "nwse-resize" -> Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR)
        "nesw-resize" -> Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR)
        "grabbing", "grab" -> Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
        else -> Cursor.getDefaultCursor()
    }
    SwingUtilities.invokeLater {
        cursorHosts.toList().forEach { it.cursor = awt }
    }
}

actual fun focusComposeCanvas() = Unit

actual fun pathIsEdit(): Boolean = false

actual fun pathId(): String? = null

actual fun pushPath(path: String) = Unit
