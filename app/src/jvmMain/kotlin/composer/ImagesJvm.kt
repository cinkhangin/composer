package composer

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.Base64
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import org.jetbrains.skia.Image as SkiaImage

/**
 * Image loading on the JVM: http(s) URLs stream over the network, `data:` URLs
 * decode inline; both decode with Skiko exactly like the web actual.
 */
actual object LocalImages {
    actual val loaded: SnapshotStateMap<String, ImageBitmap> = mutableStateMapOf()

    private val failedUrls = mutableStateMapOf<String, Unit>()

    actual fun isFailed(url: String): Boolean = failedUrls.containsKey(url)

    actual suspend fun load(url: String) {
        if (url.isEmpty() || loaded.containsKey(url) || isFailed(url)) return
        try {
            val bytes = withContext(Dispatchers.IO) { readUrlBytes(url) }
            loaded[url] = SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
        } catch (t: Throwable) {
            failedUrls[url] = Unit // 404 / bad data / unreachable — broken-image state
        }
    }
}

private fun readUrlBytes(url: String): ByteArray =
    if (url.startsWith("data:")) decodeDataUrl(url)
    else URI(url).toURL().openStream().use { it.readBytes() }

private fun decodeDataUrl(url: String): ByteArray {
    val comma = url.indexOf(',')
    require(comma > 0) { "not a data: URL" }
    val payload = url.substring(comma + 1)
    return if (";base64" in url.substring(0, comma)) Base64.getDecoder().decode(payload)
    else payload.toByteArray()
}

/** Open a file picker and return the chosen image as a `data:` URL, or null if cancelled. */
actual suspend fun pickImageFile(): String? {
    val chooser = JFileChooser().apply {
        fileFilter = FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "gif", "webp", "bmp")
    }
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return null
    val file = chooser.selectedFile?.takeIf { it.isFile } ?: return null
    val mime = when (file.extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        else -> "image/png"
    }
    return "data:$mime;base64," + Base64.getEncoder().encodeToString(file.readBytes())
}
