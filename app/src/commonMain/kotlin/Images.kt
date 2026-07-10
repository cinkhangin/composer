package composer

import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Loads images for the canvas preview — `http(s)` URLs and picked-local
 * `data:` URLs. The generated code uses Coil's `AsyncImage` instead; this is
 * editor-only.
 */
expect object LocalImages {
    /** url (or data: URL) → decoded bitmap. Snapshot-backed so the canvas updates on load. */
    val loaded: SnapshotStateMap<String, ImageBitmap>

    /** True when [url] failed to fetch/decode (CORS, 404, bad data). */
    fun isFailed(url: String): Boolean

    /** Decode [url] (http(s) or data:) into a Compose bitmap, cached by url. */
    suspend fun load(url: String)
}

/** Open a file picker and return the chosen image as a `data:` URL, or null if cancelled. */
expect suspend fun pickImageFile(): String?
