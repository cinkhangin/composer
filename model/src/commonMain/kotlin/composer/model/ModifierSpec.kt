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

/** Direction of a two-color gradient fill ([ModifierSpec.Background.colorEnd] non-null). */
@Serializable
enum class GradientDirection { Vertical, Horizontal, Diagonal, Radial }

/**
 * Unit of a `corner` value: absolute [Dp], or [Percent] of the shape's smaller
 * side (Compose's `RoundedCornerShape(percent)` — 50 = pill/circle at any size).
 */
@Serializable
enum class CornerUnit { Dp, Percent }

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
     * `Modifier.background(…, shape)`. Solid [color] fill, or a multi-stop gradient
     * when [colors] has 2+ entries (`Brush.verticalGradient(listOf(…))` etc. per
     * [direction]; [colors] is the full stop list, [color] is ignored while active).
     * [corner] is the corner radius (0 = sharp rectangle) in [cornerUnit] units.
     * New fields sit after [corner] so `Background(color, corner)` calls and old
     * saved JSON keep working.
     */
    @Serializable
    @SerialName("background")
    data class Background(
        val color: Long,
        val corner: Int = 0,
        val colors: List<Long> = emptyList(),
        val direction: GradientDirection = GradientDirection.Vertical,
        val cornerUnit: CornerUnit = CornerUnit.Dp,
    ) : ModifierSpec {
        /** Gradient stop list when active (2+ stops), or empty for a solid fill. */
        fun gradientStops(): List<Long> = if (colors.size >= 2) colors else emptyList()
    }

    /** `Modifier.weight(value)` — only valid inside a Row/Column (else ignored). */
    @Serializable
    @SerialName("weight")
    data class Weight(val value: Float) : ModifierSpec

    /** `Modifier.aspectRatio(width.f / height.f)` — constrains the w:h ratio (e.g. 1:1 square, 16:9). */
    @Serializable
    @SerialName("aspectRatio")
    data class AspectRatio(val width: Int = 1, val height: Int = 1) : ModifierSpec

    /** `Modifier.clip(RoundedCornerShape(…))` — clips to rounded corners ([cornerUnit]; 50% = circle). */
    @Serializable
    @SerialName("clip")
    data class Clip(val corner: Int, val cornerUnit: CornerUnit = CornerUnit.Dp) : ModifierSpec

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
    data class Border(
        val width: Int,
        val color: Long,
        val corner: Int = 0,
        val cornerUnit: CornerUnit = CornerUnit.Dp,
    ) : ModifierSpec

    /**
     * `Modifier.dropShadow(shape, Shadow(…))` — a Figma-style drop shadow behind the
     * component (Compose UI 1.9+ in generated code; the editor preview emulates it in
     * Skia). [radius] is the blur radius (dp), [spread] grows/shrinks the shadow
     * geometry, [offsetX]/[offsetY] shift it, [corner] is the shape's corner radius
     * (keep it in sync with the background/clip corner).
     */
    @Serializable
    @SerialName("dropShadow")
    data class DropShadow(
        val radius: Int = 8,
        val color: Long = 0x40000000,
        val offsetX: Int = 0,
        val offsetY: Int = 2,
        val spread: Int = 0,
        val corner: Int = 0,
        val cornerUnit: CornerUnit = CornerUnit.Dp,
    ) : ModifierSpec

    /**
     * `Modifier.innerShadow(shape, Shadow(…))` — the inverse of [DropShadow]: the
     * component looks recessed/pressed into the surface. Same parameters.
     */
    @Serializable
    @SerialName("innerShadow")
    data class InnerShadow(
        val radius: Int = 8,
        val color: Long = 0x40000000,
        val offsetX: Int = 0,
        val offsetY: Int = 2,
        val spread: Int = 0,
        val corner: Int = 0,
        val cornerUnit: CornerUnit = CornerUnit.Dp,
    ) : ModifierSpec

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
