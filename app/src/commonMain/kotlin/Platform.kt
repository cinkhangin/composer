package composer

/**
 * Platform seams for the editor UI (J1). The editor compiles for two hosts:
 * wasmJs (the standalone web app — actuals in `Browser*.kt`) and jvm (the
 * in-process IDE designer — actuals in `jvmMain/.../PlatformJvm.kt`). Keep
 * these tiny and imperative; everything stateful stays in common code.
 */

// ---- Preferences (small key→value strings; localStorage / java.util.prefs) ----

expect fun prefGet(key: String): String?

expect fun prefSet(key: String, value: String)

// ---- Clipboard / files / navigation ----

/** Copy [text] to the system clipboard. */
expect fun copyToClipboard(text: String)

/** Save [content] as [filename] (browser download / native save dialog). */
expect fun downloadText(filename: String, content: String, mime: String)

/** Open a file picker and deliver the chosen file's text to [onText]. */
expect fun importTextFile(accept: String, onText: (String) -> Unit)

/** Open [url] externally (new browser tab / system browser). */
expect fun openUrl(url: String)

// ---- Host chrome ----

/** Fade out the web boot loader; no-op on hosts without one. */
expect fun dismissBootLoader()

/**
 * Run [flush] when the host is about to discard the editor (browser tab
 * close). Returns an unregister function. Hosts with an explicit lifecycle
 * (the IDE panel) no-op — the bridge owns persistence there.
 */
expect fun registerUnloadFlush(flush: () -> Unit): () -> Unit

/**
 * Set the pointer cursor on the editor surface by CSS name ("ew-resize",
 * "grabbing", "default", …) — Compose's PointerIcon set has no resize
 * cursors, so the canvas drives the host cursor directly.
 */
expect fun setCanvasCursor(cursor: String)

/** Give the editor surface HOST focus (DOM focus on web) so Compose focus requests take effect. */
expect fun focusComposeCanvas()

// ---- URL routing (web only; the IDE designer has no routes) ----

/** True when the current location is a `/{id}/edit` (or `/edit`) path. */
expect fun pathIsEdit(): Boolean

/** The id segment from a `/{id}/edit` path, or null. */
expect fun pathId(): String?

/** Push [path] into the host's history/location, if it has one. */
expect fun pushPath(path: String)
