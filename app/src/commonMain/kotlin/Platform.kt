package composer

/**
 * JVM host seams used by the in-process Android Studio designer.
 */

// ---- Preferences (small key→value strings via java.util.prefs) ----

expect fun prefGet(key: String): String?

expect fun prefSet(key: String, value: String)

/**
 * Set the pointer cursor on the editor surface by CSS name ("ew-resize",
 * "grabbing", "default", …) — Compose's PointerIcon set has no resize
 * cursors, so the canvas drives the host cursor directly.
 */
expect fun setCanvasCursor(cursor: String)
