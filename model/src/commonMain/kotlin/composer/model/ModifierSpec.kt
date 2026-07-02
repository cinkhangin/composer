package composer.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A single entry in a node's modifier chain. Order is significant and is
 * preserved by both the renderer and the code generator.
 *
 * Pure data — no Compose dependency. Serializable for persistence (M6); the
 * `@SerialName`s become the `"type"` discriminator in the saved JSON.
 */
/** How a [ModifierSpec.Padding] is expressed: uniform, horizontal+vertical, or per-side. */
@Serializable
enum class PaddingMode { All, Symmetric, Sides }

@Serializable
sealed interface ModifierSpec {
    /**
     * `Modifier.padding(…)`. [mode] selects the form: [PaddingMode.All] → `padding(all.dp)`,
     * [PaddingMode.Symmetric] → `padding(horizontal, vertical)`, [PaddingMode.Sides] →
     * `padding(start, top, end, bottom)`. `all` is kept first so `Padding(16)` and old saved
     * JSON (which only had `all`) still mean uniform padding.
     */
    @Serializable
    @SerialName("padding")
    data class Padding(
        val all: Int = 0,
        val horizontal: Int = 0,
        val vertical: Int = 0,
        val start: Int = 0,
        val top: Int = 0,
        val end: Int = 0,
        val bottom: Int = 0,
        val mode: PaddingMode = PaddingMode.All,
    ) : ModifierSpec

    /** `Modifier.size(width.dp, height.dp)` — fixes both axes. */
    @Serializable
    @SerialName("size")
    data class Size(val width: Int, val height: Int) : ModifierSpec

    /** `Modifier.width(width.dp)` — fixes the width only; height stays content-driven. */
    @Serializable
    @SerialName("width")
    data class Width(val width: Int) : ModifierSpec

    /** `Modifier.height(height.dp)` — fixes the height only; width stays content-driven. */
    @Serializable
    @SerialName("height")
    data class Height(val height: Int) : ModifierSpec

    /** `Modifier.offset(x.dp, y.dp)` — visual translation (x/y may be negative). */
    @Serializable
    @SerialName("offset")
    data class Offset(val x: Int, val y: Int) : ModifierSpec

    /**
     * `Modifier.background(Color(0xAARRGGBB), shape)`. [corner] is the corner
     * radius in dp (0 = sharp rectangle; a large value rounds a square to a circle).
     */
    @Serializable
    @SerialName("background")
    data class Background(val color: Long, val corner: Int = 0) : ModifierSpec

    /** `Modifier.weight(value)` — only valid inside a Row/Column (else ignored). */
    @Serializable
    @SerialName("weight")
    data class Weight(val value: Float) : ModifierSpec

    /** `Modifier.aspectRatio(width.f / height.f)` — constrains the w:h ratio (e.g. 1:1 square, 16:9). */
    @Serializable
    @SerialName("aspectRatio")
    data class AspectRatio(val width: Int = 1, val height: Int = 1) : ModifierSpec

    /** `Modifier.clip(RoundedCornerShape(corner.dp))` — clips to rounded corners (large value = circle). */
    @Serializable
    @SerialName("clip")
    data class Clip(val corner: Int) : ModifierSpec

    /** `Modifier.alpha(value)` — opacity 0f (transparent) … 1f (opaque). */
    @Serializable
    @SerialName("alpha")
    data class Alpha(val value: Float) : ModifierSpec

    /**
     * `Modifier.border(width.dp, Color(0xAARRGGBB), shape)` — a stroke. [corner] is the
     * corner radius in dp (0 = sharp rectangle), matching [Background].
     */
    @Serializable
    @SerialName("border")
    data class Border(val width: Int, val color: Long, val corner: Int = 0) : ModifierSpec

    @Serializable
    @SerialName("fillMaxWidth")
    data object FillMaxWidth : ModifierSpec

    @Serializable
    @SerialName("fillMaxHeight")
    data object FillMaxHeight : ModifierSpec

    @Serializable
    @SerialName("fillMaxSize")
    data object FillMaxSize : ModifierSpec
}
