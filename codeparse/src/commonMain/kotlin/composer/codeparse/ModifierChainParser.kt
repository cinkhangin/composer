package composer.codeparse

import composer.model.BoxAlignment
import composer.model.CornerUnit
import composer.model.GradientDirection
import composer.model.HAlignment
import composer.model.ModifierSpec
import composer.model.PaddingMode
import composer.model.VAlignment

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
internal fun parseModifierChain(expr: KExpr, scopeParam: String? = null): List<ModifierSpec>? {
    val (root, calls) = chainCalls(expr.unparen()) ?: return null
    val specs = mutableListOf<ModifierSpec>()
    if (root != "Modifier") specs += ModifierSpec.External(root)
    calls.forEachIndexed { i, call ->
        if (i == 0 && scopeParam != null && isScopePadding(call, scopeParam)) return@forEachIndexed
        specs += parseModifierCall(call) ?: return null
    }
    return specs
}

/** The chain root and calls in application order; root must be a bare identifier. */
private fun chainCalls(expr: KExpr): Pair<String, List<KCall>>? {
    if (expr is KName) return expr.name to emptyList()
    val calls = ArrayDeque<KCall>()
    var cur: KExpr = expr
    while (true) {
        val dot = cur.asDot() ?: break
        val sel = dot.selector?.unparen() as? KCall ?: return null
        calls.addFirst(sel)
        cur = dot.receiver.unparen()
    }
    val root = cur as? KName ?: return null
    return root.name to calls.toList()
}

private fun isScopePadding(call: KCall, scopeParam: String): Boolean =
    callName(call) == "padding" && nameOf(call.singlePositionalArg()) == scopeParam

private fun parseModifierCall(call: KCall): ModifierSpec? {
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
        "rotate" -> single(shape)?.let { floatLit(it) }?.let { ModifierSpec.Rotate(it) }
        "scale" -> when {
            shape.named.isNotEmpty() -> null
            shape.positional.size == 1 -> floatLit(shape.positional[0])?.let { ModifierSpec.Scale(it, it) }
            shape.positional.size == 2 -> {
                val x = floatLit(shape.positional[0]) ?: return null
                val y = floatLit(shape.positional[1]) ?: return null
                ModifierSpec.Scale(x, y)
            }
            else -> null
        }
        "align" -> single(shape)?.let { alignSpec(it) }
        "zIndex" -> single(shape)?.let { floatLit(it) }?.let { ModifierSpec.ZIndex(it) }
        "blur" -> single(shape)?.let { dpInt(it) }?.let { ModifierSpec.Blur(it) }
        "fillMaxWidth" -> fillFraction(shape)?.let { ModifierSpec.FillMaxWidth(it) }
        "fillMaxHeight" -> fillFraction(shape)?.let { ModifierSpec.FillMaxHeight(it) }
        "fillMaxSize" -> fillFraction(shape)?.let { ModifierSpec.FillMaxSize(it) }
        else -> null
    }
}

/** `fillMax*()` → 1f, `fillMax*(0.5f)` → the fraction; anything else fails. */
private fun fillFraction(shape: CallShape): Float? = when {
    shape.named.isNotEmpty() -> null
    shape.positional.isEmpty() -> 1f
    shape.positional.size == 1 -> floatLit(shape.positional[0])
    else -> null
}

/**
 * `Alignment.<name>` → a scope-typed [ModifierSpec.Align]. The name alone decides
 * the scope (Row's Top/CenterVertically/Bottom, Column's Start/CenterHorizontally/End,
 * Box's 2D names) — the sets don't overlap.
 */
private fun alignSpec(expr: KExpr): ModifierSpec.Align? {
    val dot = expr.unparen().asDot() ?: return null
    if (nameOf(dot.receiver) != "Alignment") return null
    return when (val name = nameOf(dot.selector) ?: return null) {
        "Top" -> ModifierSpec.Align(vertical = VAlignment.Top)
        "CenterVertically" -> ModifierSpec.Align(vertical = VAlignment.Center)
        "Bottom" -> ModifierSpec.Align(vertical = VAlignment.Bottom)
        "Start" -> ModifierSpec.Align(horizontal = HAlignment.Start)
        "CenterHorizontally" -> ModifierSpec.Align(horizontal = HAlignment.Center)
        "End" -> ModifierSpec.Align(horizontal = HAlignment.End)
        else -> BoxAlignment.entries.firstOrNull { it.name == name }?.let { ModifierSpec.Align(box = it) }
    }
}

private fun single(shape: CallShape): KExpr? =
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
    val dot = fill.asDot() ?: return null
    if (nameOf(dot.receiver) != "Brush") return null
    val brushCall = dot.selector?.unparen() as? KCall ?: return null
    val direction = when (callName(brushCall)) {
        "verticalGradient" -> GradientDirection.Vertical
        "horizontalGradient" -> GradientDirection.Horizontal
        "linearGradient" -> GradientDirection.Diagonal
        "radialGradient" -> GradientDirection.Radial
        else -> return null
    }
    val listCall = brushCall.singlePositionalArg()?.unparen() as? KCall ?: return null
    if (callName(listCall) != "listOf" || listCall.trailingLambdas.isNotEmpty()) return null
    val stops = listCall.args.map { arg ->
        if (arg.name != null || arg.isSpread) return null
        colorValue(arg.expr) ?: return null
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
    val arg = single(shape)?.unparen() as? KBinary ?: return null
    if (arg.op != "/") return null
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
    val shadowCall = shape.positional[1].unparen() as? KCall ?: return null
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
        val call = off.unparen() as? KCall ?: return null
        if (callName(call) != "DpOffset") return null
        val os = callShape(call) ?: return null
        if (os.named.isNotEmpty() || os.positional.size != 2 || os.trailingLambda != null) return null
        offsetX = dpInt(os.positional[0]) ?: return null
        offsetY = dpInt(os.positional[1]) ?: return null
    }
    return build(radius, color, offsetX, offsetY, spread, corner, unit)
}
