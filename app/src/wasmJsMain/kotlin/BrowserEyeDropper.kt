package composer

import kotlinx.coroutines.await
import kotlin.js.Promise

/**
 * Screen color sampling via the browser's **EyeDropper API**. Chromium-only;
 * on other browsers [ScreenEyeDropper.supported] is false and the picker
 * button is hidden. The browser shows its own magnifier loupe and the user
 * can sample any pixel on screen (even outside the tab).
 */
private external interface JsEyeDropResult : JsAny {
    val sRGBHex: JsString // "#rrggbb"
}

private fun eyeDropperSupported(): Boolean =
    js("typeof EyeDropper === 'function'")

private fun openEyeDropperJs(): Promise<JsEyeDropResult> =
    js("new EyeDropper().open()")

object ScreenEyeDropper {
    val supported: Boolean get() = eyeDropperSupported()

    /**
     * Open the eyedropper and return the sampled color as opaque `0xFFRRGGBB`,
     * or null when unsupported or the user cancels (Esc rejects the promise).
     */
    suspend fun pick(): Long? {
        if (!supported) return null
        return try {
            val hex = openEyeDropperJs().await<JsEyeDropResult>().sRGBHex.toString()
            val rgb = hex.removePrefix("#").toLongOrNull(16) ?: return null
            0xFF000000L or rgb
        } catch (_: Throwable) {
            null // cancelled (AbortError) or blocked
        }
    }
}
