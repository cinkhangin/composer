package composer

/** Small host seams shared by the browser app and in-process IDE designer. */

// ---- Preferences (small key→value strings via java.util.prefs) ----

expect fun prefGet(key: String): String?

expect fun prefSet(key: String, value: String)

expect fun copyToClipboard(text: String)

expect fun downloadText(filename: String, content: String, mime: String)

expect fun importTextFile(accept: String, onText: (String) -> Unit)

expect fun openUrl(url: String)

expect fun dismissBootLoader()

expect fun registerUnloadFlush(flush: () -> Unit): () -> Unit

/**
 * Set the pointer cursor on the editor surface by CSS name ("ew-resize",
 * "grabbing", "default", …) — Compose's PointerIcon set has no resize
 * cursors, so the canvas drives the host cursor directly.
 */
expect fun setCanvasCursor(cursor: String)

expect fun focusComposeCanvas()

expect fun pathIsEdit(): Boolean

expect fun pathId(): String?

expect fun pushPath(path: String)
