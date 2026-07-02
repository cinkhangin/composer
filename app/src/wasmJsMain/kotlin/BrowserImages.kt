package composer

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.await
import org.jetbrains.skia.Image as SkiaImage
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import kotlin.js.Promise

/**
 * Loads images for the canvas preview — `http(s)` URLs and picked-local `data:` URLs
 * both go through the browser `fetch` (which handles data URIs), decoded with Skiko.
 * The generated code uses Coil's `AsyncImage` instead; this is editor-only.
 */
private fun fetchArrayBuffer(url: String): Promise<ArrayBuffer> =
    js("fetch(url).then(function (r) { return r.arrayBuffer(); })")

private fun pickImageDataUrl(): Promise<JsString?> = js(
    """
    new Promise(function (resolve) {
        var input = document.createElement('input');
        input.type = 'file';
        input.accept = 'image/*';
        input.onchange = function () {
            var file = input.files && input.files[0];
            if (!file) { resolve(null); return; }
            var reader = new FileReader();
            reader.onload = function () { resolve(reader.result); };
            reader.onerror = function () { resolve(null); };
            reader.readAsDataURL(file);
        };
        input.click();
    })
    """
)

object LocalImages {
    /** url (or data: URL) → decoded bitmap. Snapshot-backed so the canvas updates on load. */
    val loaded = mutableStateMapOf<String, ImageBitmap>()
    private val failed = mutableSetOf<String>()

    /** Decode [url] (http(s) or data:) into a Compose bitmap, cached by url. */
    suspend fun load(url: String) {
        if (url.isEmpty() || loaded.containsKey(url) || url in failed) return
        try {
            val bytes = fetchArrayBuffer(url).await<ArrayBuffer>().toByteArray()
            loaded[url] = SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
        } catch (t: Throwable) {
            failed.add(url) // e.g. CORS-blocked URL — show the placeholder instead
        }
    }
}

/** Open a file picker and return the chosen image as a `data:` URL, or null if cancelled. */
suspend fun pickImageFile(): String? = pickImageDataUrl().await<JsString?>()?.toString()

private fun ArrayBuffer.toByteArray(): ByteArray {
    val view = Int8Array(this)
    return ByteArray(view.length) { view[it] }
}
