package composer

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Typeface
import kotlinx.coroutines.await
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import kotlin.js.Promise

/**
 * Device-installed fonts via the browser's **Local Font Access API**
 * (`window.queryLocalFonts()`). Chromium-only and permission-gated; on other
 * browsers [supported] is false and the picker is disabled. Loaded fonts are
 * registered as Compose [FontFamily]s so the canvas can preview them.
 */
private external interface JsFontData : JsAny {
    val family: JsString
    fun blob(): Promise<JsBlob>
}

private external interface JsBlob : JsAny {
    fun arrayBuffer(): Promise<ArrayBuffer>
}

private fun localFontsSupported(): Boolean =
    js("typeof window !== 'undefined' && typeof window.queryLocalFonts === 'function'")

private fun queryLocalFontsJs(): Promise<JsArray<JsFontData>> =
    js("window.queryLocalFonts()")

object LocalFonts {
    /** Installed font family names, populated by [query]. Snapshot-backed for UI. */
    val available = mutableStateListOf<String>()

    /** family name → loaded FontFamily, populated by [load]. */
    val loaded = mutableStateMapOf<String, FontFamily>()

    val supported: Boolean get() = localFontsSupported()

    /** Enumerate installed font families (prompts for permission on first use). */
    suspend fun query() {
        if (!supported) return
        val arr = queryLocalFontsJs().await<JsArray<JsFontData>>()
        val seen = LinkedHashSet<String>()
        for (i in 0 until arr.length) {
            val fd = arr[i] ?: continue
            seen.add(fd.family.toString())
        }
        available.clear()
        available.addAll(seen.sorted())
    }

    /** Load [family]'s bytes and register a FontFamily for the canvas preview. */
    suspend fun load(family: String) {
        if (family.isEmpty() || loaded.containsKey(family) || !supported) return
        val arr = queryLocalFontsJs().await<JsArray<JsFontData>>()
        for (i in 0 until arr.length) {
            val fd = arr[i] ?: continue
            if (fd.family.toString() == family) {
                val buffer = fd.blob().await<JsBlob>().arrayBuffer().await<ArrayBuffer>()
                val skia = FontMgr.default.makeFromData(Data.makeFromBytes(buffer.toByteArray())) ?: return
                loaded[family] = FontFamily(Typeface(skia))
                return
            }
        }
    }
}

private fun ArrayBuffer.toByteArray(): ByteArray {
    val view = Int8Array(this)
    return ByteArray(view.length) { view[it] }
}
