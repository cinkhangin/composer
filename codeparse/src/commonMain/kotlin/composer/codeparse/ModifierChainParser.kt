package composer.codeparse

import composer.model.BoxAlignment
import composer.model.CornerUnit
import composer.model.GradientDirection
import composer.model.HAlignment
import composer.model.ModifierSpec
import composer.model.PaddingMode
import composer.model.VAlignment
import kotlin.math.roundToInt

/**
 * `Modifier.padding(16.dp).fillMaxWidth()…` → ordered [ModifierSpec] list — the
 * exact inverse of CodeGen.modifierExpr. An unrecognized call/argument returns
 * null; ComponentParser then retains the entire expression as an opaque
 * `ModifierSpec.External`. The canvas ignores that chain without hiding the
 * component, while codegen preserves it and its significant order.
 *
 * [scopeParam] is the enclosing Scaffold content lambda's parameter. A
 * first `padding(<scopeParam>)` call is the canonical prefix codegen adds and is
 * therefore implicit. The same call later in the chain becomes an ordered
 * [ModifierSpec.ScaffoldPadding] marker so authored suffix padding is not moved.
 */
internal fun parseModifierChain(expr: KExpr, scopeParam: String? = null): List<ModifierSpec>? {
    return parseModifierChain(expr, scopeParam, skipUnknown = false)
}

/**
 * Recover the statically evaluable calls from a runtime-dependent chain. The
 * owning [ModifierSpec.External] remains the write-back authority; this list is
 * only a safe preview projection, so unknown calls are deliberate no-ops.
 */
internal fun parseModifierPreview(expr: KExpr, scopeParam: String? = null): List<ModifierSpec> =
    parseModifierChain(expr, scopeParam, skipUnknown = true).orEmpty()

private fun parseModifierChain(
    expr: KExpr,
    scopeParam: String?,
    skipUnknown: Boolean,
): List<ModifierSpec>? {
    val (root, calls) = chainCalls(expr.unparen()) ?: return null
    val specs = mutableListOf<ModifierSpec>()
    if (!skipUnknown && root != "Modifier") specs += ModifierSpec.External(root)
    val scopePaddingCount = if (scopeParam == null) 0 else calls.count { isScopePadding(it, scopeParam) }
    calls.forEachIndexed { index, call ->
        if (scopeParam != null && isScopePadding(call, scopeParam)) {
            // A single leading call is codegen's canonical implicit prefix. If
            // the source deliberately contains the scope padding more than once,
            // retain every occurrence so suppressing the implicit prefix cannot
            // silently drop one.
            if (index == 0 && scopePaddingCount == 1) return@forEachIndexed
            specs += ModifierSpec.ScaffoldPadding(scopeParam)
            return@forEachIndexed
        }
        val parsed = parseModifierCall(call)
        if (parsed != null) specs += parsed
        else if (!skipUnknown) return null
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
            shape.positional.isEmpty() && shape.named.keys == setOf("width", "height") -> {
                val w = dpInt(shape.named.getValue("width")) ?: return null
                val h = dpInt(shape.named.getValue("height")) ?: return null
                ModifierSpec.Size(w, h)
            }
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
            val (x, y) = when {
                shape.named.isEmpty() && shape.positional.size == 2 -> {
                    val x = dpInt(shape.positional[0]) ?: return null
                    val y = dpInt(shape.positional[1]) ?: return null
                    x to y
                }
                shape.positional.isEmpty() && shape.named.keys.all { it in setOf("x", "y") } -> {
                    val x = shape.named["x"]?.let { dpInt(it) ?: return null } ?: 0
                    val y = shape.named["y"]?.let { dpInt(it) ?: return null } ?: 0
                    x to y
                }
                else -> return null
            }
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
    val fill: KExpr
    val shapeExpr: KExpr?
    when {
        shape.named.isEmpty() && shape.positional.size in 1..2 -> {
            fill = shape.positional[0].unparen()
            shapeExpr = shape.positional.getOrNull(1)
        }
        shape.positional.isEmpty() && shape.named.keys.all { it in setOf("color", "shape") } && "color" in shape.named -> {
            fill = shape.named.getValue("color").unparen()
            shapeExpr = shape.named["shape"]
        }
        else -> return null
    }
    val (corner, unit) = shapeExpr?.let { shapeCorner(it) ?: return null } ?: (0 to CornerUnit.Dp)
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
    val arg = single(shape)?.unparen() ?: return null
    val ratio = if (arg is KBinary && arg.op == "/") {
        val width = floatLit(arg.left) ?: return null
        val height = floatLit(arg.right) ?: return null
        if (height == 0f) return null
        if (width == width.toInt().toFloat() && height == height.toInt().toFloat()) {
            return ModifierSpec.AspectRatio(width.toInt(), height.toInt())
        }
        width / height
    } else {
        floatLit(arg) ?: return null
    }
    if (!ratio.isFinite() || ratio <= 0f) return null

    // AspectRatio stores a rational pair so it remains deterministic and easy
    // to edit. Approximate decimal source ratios (1.2f, 1f / 1.2f) closely with
    // a small denominator; common values resolve exactly (6:5 and 5:6).
    var bestWidth = 1
    var bestHeight = 1
    var bestError = kotlin.math.abs(ratio - 1f)
    for (height in 1..100) {
        val width = (ratio * height).roundToInt().coerceAtLeast(1)
        val error = kotlin.math.abs(ratio - width.toFloat() / height)
        if (error < bestError) {
            bestWidth = width
            bestHeight = height
            bestError = error
        }
        if (error < 0.00001f) break
    }
    return ModifierSpec.AspectRatio(bestWidth, bestHeight)
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
