package composer.model

import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

/** Text styling options that map to Compose's FontWeight / FontFamily. */

@Serializable
enum class TextWeight { Normal, Light, Medium, SemiBold, Bold, ExtraBold, Black }

@Serializable
enum class TextFontFamily { Default, SansSerif, Serif, Monospace, Cursive }

/** Horizontal text alignment → Compose `TextAlign`. `Start` is the default (emitted as nothing). */
@Serializable
enum class TextAlignment { Start, Center, End, Justify }

/**
 * The line height (sp) actually used: the explicit [Node.Text.lineHeight] if set,
 * else `fontSize × 1.2` (rounded) when a font size is set, else 0 (= inherit).
 */
fun Node.Text.effectiveLineHeight(): Int = when {
    lineHeight > 0 -> lineHeight
    fontSize > 0 -> (fontSize * 1.2f).roundToInt()
    else -> 0
}
