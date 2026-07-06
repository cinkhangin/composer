package composer.codeparse

import composer.model.CornerUnit
import composer.model.GradientDirection
import composer.model.ModifierSpec
import composer.model.PaddingMode
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

/**
 * `Modifier.padding(16.dp).fillMaxWidth()…` → ordered [ModifierSpec] list — the
 * exact inverse of CodeGen.modifierExpr. Any unrecognized call/argument in the
 * chain fails the WHOLE node (→ RawCode): a modifier the canvas can't execute
 * would silently lie about layout, and chain order is significant.
 *
 * [scopeParam] is the enclosing Scaffold content lambda's parameter: a FIRST
 * chain call `padding(<scopeParam>)` is the scope-imposed prefix codegen
 * prepends, not model data — it's stripped (and re-imposed on regeneration).
 */
internal fun parseModifierChain(expr: KtExpression, scopeParam: String? = null): List<ModifierSpec>? {
    val calls = chainCalls(expr.unparen()) ?: return null
    val specs = mutableListOf<ModifierSpec>()
    calls.forEachIndexed { i, call ->
        if (i == 0 && scopeParam != null && isScopePadding(call, scopeParam)) return@forEachIndexed
        specs += parseModifierCall(call) ?: return null
    }
    return specs
}

/** The chain's calls in application order; null unless rooted at bare `Modifier`. */
private fun chainCalls(expr: KtExpression): List<KtCallExpression>? {
    if (expr is KtNameReferenceExpression && expr.getReferencedName() == "Modifier") return emptyList()
    val calls = ArrayDeque<KtCallExpression>()
    var cur: KtExpression = expr
    while (cur is KtDotQualifiedExpression) {
        val sel = cur.selectorExpression?.unparen() as? KtCallExpression ?: return null
        calls.addFirst(sel)
        cur = cur.receiverExpression.unparen()
    }
    val root = cur as? KtNameReferenceExpression ?: return null
    if (root.getReferencedName() != "Modifier") return null
    return calls.toList()
}

private fun isScopePadding(call: KtCallExpression, scopeParam: String): Boolean =
    callName(call) == "padding" && nameOf(call.singlePositionalArg()) == scopeParam

private fun parseModifierCall(call: KtCallExpression): ModifierSpec? {
    val shape = callShape(call) ?: return null
    if (shape.trailingLambda != null) return null
    return when (shape.name) {
        "padding" -> parsePadding(shape)
        "size" -> when {
            shape.named.isNotEmpty() -> null
            shape.positional.size == 2 -> {
                val w = dpInt(shape.positional[0]) ?: return null
                val h = dpInt(shape.positional[1]) ?: return null
                ModifierSpec.Size(w, h)
            }
            shape.positional.size == 1 -> dpInt(shape.positional[0])?.let { ModifierSpec.Size(it, it) }
            else -> null
        }
        "width" -> single(shape)?.let { dpInt(it) }?.let { ModifierSpec.Width(it) }
        "height" -> single(shape)?.let { dpInt(it) }?.let { ModifierSpec.Height(it) }
        "offset" -> {
            if (shape.named.isNotEmpty() || shape.positional.size != 2) return null
            val x = dpInt(shape.positional[0]) ?: return null
            val y = dpInt(shape.positional[1]) ?: return null
            ModifierSpec.Offset(x, y)
        }
        "background" -> parseBackground(shape)
        "weight" -> single(shape)?.let { floatLit(it) }?.let { ModifierSpec.Weight(it) }
        "aspectRatio" -> parseAspectRatio(shape)
        "clip" -> single(shape)?.let { shapeCorner(it) }?.let { (c, u) -> ModifierSpec.Clip(c, u) }
        "alpha" -> single(shape)?.let { floatLit(it) }?.let { ModifierSpec.Alpha(it) }
        "border" -> parseBorder(shape)
        "dropShadow" -> parseShadow(shape) { r, c, x, y, s, corner, unit ->
            ModifierSpec.DropShadow(r, c, x, y, s, corner, unit)
        }
        "innerShadow" -> parseShadow(shape) { r, c, x, y, s, corner, unit ->
            ModifierSpec.InnerShadow(r, c, x, y, s, corner, unit)
        }
        "fillMaxWidth" -> if (noArgs(shape)) ModifierSpec.FillMaxWidth else null
        "fillMaxHeight" -> if (noArgs(shape)) ModifierSpec.FillMaxHeight else null
        "fillMaxSize" -> if (noArgs(shape)) ModifierSpec.FillMaxSize else null
        else -> null
    }
}

private fun noArgs(shape: CallShape): Boolean = shape.positional.isEmpty() && shape.named.isEmpty()

private fun single(shape: CallShape): KtExpression? =
    if (shape.named.isEmpty()) shape.positional.singleOrNull() else null

private fun parsePadding(shape: CallShape): ModifierSpec.Padding? {
    if (shape.positional.size == 1 && shape.named.isEmpty()) {
        val all = dpInt(shape.positional[0]) ?: return null
        return ModifierSpec.Padding(all = all, mode = PaddingMode.All)
    }
    if (shape.positional.isNotEmpty()) return null
    val names = shape.named.keys
    if (names.isEmpty()) return null
    if (names.all { it in setOf("horizontal", "vertical") }) {
        val h = shape.named["horizontal"]?.let { dpInt(it) ?: return null } ?: 0
        val v = shape.named["vertical"]?.let { dpInt(it) ?: return null } ?: 0
        return ModifierSpec.Padding(horizontal = h, vertical = v, mode = PaddingMode.Symmetric)
    }
    if (names.all { it in setOf("start", "top", "end", "bottom") }) {
        val s = shape.named["start"]?.let { dpInt(it) ?: return null } ?: 0
        val t = shape.named["top"]?.let { dpInt(it) ?: return null } ?: 0
        val e = shape.named["end"]?.let { dpInt(it) ?: return null } ?: 0
        val b = shape.named["bottom"]?.let { dpInt(it) ?: return null } ?: 0
        return ModifierSpec.Padding(start = s, top = t, end = e, bottom = b, mode = PaddingMode.Sides)
    }
    return null
}

private fun parseBackground(shape: CallShape): ModifierSpec.Background? {
    if (shape.named.isNotEmpty() || shape.positional.isEmpty() || shape.positional.size > 2) return null
    val (corner, unit) = shape.positional.getOrNull(1)
        ?.let { shapeCorner(it) ?: return null }
        ?: (0 to CornerUnit.Dp)
    val fill = shape.positional[0].unparen()
    colorValue(fill)?.let { solid ->
        return ModifierSpec.Background(color = solid, corner = corner, cornerUnit = unit)
    }
    // Brush.<builder>(listOf(c1, c2, …))
    val dot = fill as? KtDotQualifiedExpression ?: return null
    if (nameOf(dot.receiverExpression) != "Brush") return null
    val brushCall = dot.selectorExpression?.unparen() as? KtCallExpression ?: return null
    val direction = when (callName(brushCall)) {
        "verticalGradient" -> GradientDirection.Vertical
        "horizontalGradient" -> GradientDirection.Horizontal
        "linearGradient" -> GradientDirection.Diagonal
        "radialGradient" -> GradientDirection.Radial
        else -> return null
    }
    val listCall = brushCall.singlePositionalArg()?.unparen() as? KtCallExpression ?: return null
    if (callName(listCall) != "listOf" || listCall.lambdaArguments.isNotEmpty()) return null
    val stops = listCall.valueArguments.map { arg ->
        val v = arg as? org.jetbrains.kotlin.psi.KtValueArgument ?: return null
        if (v.getArgumentName() != null || v.isSpread) return null
        colorValue(v.getArgumentExpression()) ?: return null
    }
    if (stops.size < 2) return null
    return ModifierSpec.Background(
        color = stops.first(), // canonical: solid field mirrors the first stop while a gradient is active
        corner = corner,
        colors = stops,
        direction = direction,
        cornerUnit = unit,
    )
}

private fun parseAspectRatio(shape: CallShape): ModifierSpec.AspectRatio? {
    val arg = single(shape)?.unparen() as? KtBinaryExpression ?: return null
    if (arg.operationReference.getReferencedName() != "/") return null
    val w = floatLit(arg.left) ?: return null
    val h = floatLit(arg.right) ?: return null
    // The model stores ints; only integral ratios are representable.
    if (w != w.toInt().toFloat() || h != h.toInt().toFloat()) return null
    return ModifierSpec.AspectRatio(w.toInt(), h.toInt())
}

private fun parseBorder(shape: CallShape): ModifierSpec.Border? {
    if (shape.named.isNotEmpty() || shape.positional.size !in 2..3) return null
    val width = dpInt(shape.positional[0]) ?: return null
    val color = colorValue(shape.positional[1]) ?: return null
    val (corner, unit) = shape.positional.getOrNull(2)
        ?.let { shapeCorner(it) ?: return null }
        ?: (0 to CornerUnit.Dp)
    return ModifierSpec.Border(width, color, corner, unit)
}

private fun parseShadow(
    shape: CallShape,
    build: (radius: Int, color: Long, offsetX: Int, offsetY: Int, spread: Int, corner: Int, unit: CornerUnit) -> ModifierSpec,
): ModifierSpec? {
    if (shape.named.isNotEmpty() || shape.positional.size != 2) return null
    val (corner, unit) = shapeCorner(shape.positional[0]) ?: return null
    val shadowCall = shape.positional[1].unparen() as? KtCallExpression ?: return null
    if (callName(shadowCall) != "Shadow") return null
    val s = callShape(shadowCall) ?: return null
    if (s.trailingLambda != null || s.positional.isNotEmpty()) return null
    if (!s.named.keys.all { it in setOf("radius", "color", "spread", "offset") }) return null
    val radius = dpInt(s.named["radius"] ?: return null) ?: return null
    val color = colorValue(s.named["color"] ?: return null) ?: return null
    val spread = s.named["spread"]?.let { dpInt(it) ?: return null } ?: 0
    var offsetX = 0
    var offsetY = 0
    s.named["offset"]?.let { off ->
        val call = off.unparen() as? KtCallExpression ?: return null
        if (callName(call) != "DpOffset") return null
        val os = callShape(call) ?: return null
        if (os.named.isNotEmpty() || os.positional.size != 2 || os.trailingLambda != null) return null
        offsetX = dpInt(os.positional[0]) ?: return null
        offsetY = dpInt(os.positional[1]) ?: return null
    }
    return build(radius, color, offsetX, offsetY, spread, corner, unit)
}
