package composer.render

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import composer.model.ModifierSpec
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect

/**
 * Editor-canvas emulation of the Compose UI 1.9 `dropShadow`/`innerShadow` modifiers
 * (the editor runs CMP 1.7.3, which lacks them). Drawn directly with Skia — a blurred
 * rounded rect behind the content (drop) or a blurred inverse ring clipped inside it
 * (inner) — so the preview matches what the generated 1.9 code renders.
 *
 * Blur uses `sigma = radius / 2` (the CSS/Skia convention for a blur *radius*).
 */
fun Modifier.dropShadowPreview(spec: ModifierSpec.DropShadow): Modifier = drawBehind {
    val blur = spec.radius.dp.toPx()
    val spread = spec.spread.dp.toPx()
    val dx = spec.offsetX.dp.toPx()
    val dy = spec.offsetY.dp.toPx()
    val rrect = RRect.makeXYWH(
        dx - spread,
        dy - spread,
        size.width + 2 * spread,
        size.height + 2 * spread,
        (spec.corner.dp.toPx() + spread).coerceAtLeast(0f),
    )
    val paint = shadowPaint(spec.color, blur)
    drawContext.canvas.nativeCanvas.drawRRect(rrect, paint)
    paint.close()
}

fun Modifier.innerShadowPreview(spec: ModifierSpec.InnerShadow): Modifier = drawWithContent {
    drawContent()
    val blur = spec.radius.dp.toPx()
    val spread = spec.spread.dp.toPx()
    val dx = spec.offsetX.dp.toPx()
    val dy = spec.offsetY.dp.toPx()
    val corner = spec.corner.dp.toPx()
    val canvas = drawContext.canvas.nativeCanvas
    canvas.save()
    // Everything stays inside the component's shape.
    canvas.clipRRect(RRect.makeXYWH(0f, 0f, size.width, size.height, corner), true)
    // The shadow is the (blurred) area BETWEEN a huge outer rect and the content
    // rect inset by spread and shifted by the offset — i.e. the light "leaks in"
    // from the edges the offset moves away from.
    val margin = blur + spread + maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) + 1f
    val outer = RRect.makeXYWH(-margin, -margin, size.width + 2 * margin, size.height + 2 * margin, 0f)
    val inner = RRect.makeXYWH(
        dx + spread,
        dy + spread,
        (size.width - 2 * spread).coerceAtLeast(0f),
        (size.height - 2 * spread).coerceAtLeast(0f),
        (corner - spread).coerceAtLeast(0f),
    )
    val paint = shadowPaint(spec.color, blur)
    canvas.drawDRRect(outer, inner, paint)
    paint.close()
    canvas.restore()
}

private fun shadowPaint(argb: Long, blurPx: Float): Paint = Paint().apply {
    color = argb.toInt()
    isAntiAlias = true
    if (blurPx > 0f) maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, blurPx / 2f)
}
