package composer.codeparse

import composer.model.CornerUnit
import composer.model.ThemeColorRef

/**
 * Literal/expression readers for the syntactic (no-resolve) parser. Every reader
 * returns null on anything it doesn't recognize — the caller then falls back to
 * [composer.model.Node.RawCode] for the whole statement. These are the exact
 * inverses of what CodeGen emits; the accepted grammar is deliberately no wider
 * than "codegen output plus trivially-equivalent forms".
 */

internal fun KExpr.unparen(): KExpr {
    var e: KExpr = this
    while (e is KParen) e = e.inner ?: return e
    return e
}

/** A plain (non-safe) qualified expression — `a?.b` is never codegen's shape. */
internal fun KExpr?.asDot(): KDot? = (this as? KDot)?.takeIf { !it.safe }

/** The called name of a plain `Name(...)` call, or null. */
internal fun callName(call: KCall): String? = (call.callee as? KName)?.name

/** A plain identifier reference (`innerPadding`, `Modifier`), or null. */
internal fun nameOf(expr: KExpr?): String? = (expr?.unparen() as? KName)?.name

/** `A.b` → "A" to "b" for simple two-part dotted names, or null. */
internal fun dottedName(expr: KExpr?): Pair<String, String>? {
    val dot = expr?.unparen().asDot() ?: return null
    val receiver = nameOf(dot.receiver) ?: return null
    val selector = nameOf(dot.selector) ?: return null
    return receiver to selector
}

/** Plain (optionally negative) integer literal. */
internal fun intLit(expr: KExpr?): Int? {
    val e = expr?.unparen() ?: return null
    if (e is KPrefix) {
        if (e.op != "-") return null
        return intLit(e.base)?.let { -it }
    }
    val c = e as? KConst ?: return null
    return c.text.toIntOrNull()
}

/** `N.dp` / `N.sp` (optionally negative: `-8.dp` parses as `-(8.dp)`). */
internal fun unitInt(expr: KExpr?, unit: String): Int? {
    val e = expr?.unparen() ?: return null
    if (e is KPrefix) {
        if (e.op != "-") return null
        return unitInt(e.base, unit)?.let { -it }
    }
    val dot = e.asDot() ?: return null
    if (nameOf(dot.selector) != unit) return null
    return intLit(dot.receiver)
}

internal fun dpInt(expr: KExpr?): Int? = unitInt(expr, "dp")
internal fun spInt(expr: KExpr?): Int? = unitInt(expr, "sp")

/** Float literal with codegen's `f` suffix (`0.5f`, `16f`), optionally negative. */
internal fun floatLit(expr: KExpr?): Float? {
    val e = expr?.unparen() ?: return null
    if (e is KPrefix) {
        if (e.op != "-") return null
        return floatLit(e.base)?.let { -it }
    }
    val c = e as? KConst ?: return null
    val t = c.text
    if (!t.endsWith("f") && !t.endsWith("F")) return null
    return t.dropLast(1).toFloatOrNull()
}

internal fun boolLit(expr: KExpr?): Boolean? = when ((expr?.unparen() as? KConst)?.text) {
    "true" -> true
    "false" -> false
    else -> null
}

/**
 * A compile-time string: a non-raw template with only literal/escape entries
 * (the exact inverse of CodeGen.esc). Any interpolation or `"""` → null.
 */
internal fun stringLit(expr: KExpr?): String? {
    val s = expr?.unparen() as? KString ?: return null
    if (s.isRaw) return null
    val sb = StringBuilder()
    for (entry in s.entries) {
        when (entry) {
            is KStringEntry.Literal -> sb.append(entry.text)
            is KStringEntry.Escape -> sb.append(entry.unescaped ?: return null)
            is KStringEntry.Interpolation -> return null // interpolation — not a literal
        }
    }
    return sb.toString()
}

/**
 * A safe canvas preview for a string template. Interpolations are represented
 * by their source names/expressions; Composer never tries to execute user code.
 */
internal fun stringPreview(expr: KExpr?): String? {
    val s = expr?.unparen() as? KString ?: return null
    val sb = StringBuilder()
    for (entry in s.entries) {
        when (entry) {
            is KStringEntry.Literal -> sb.append(entry.text)
            is KStringEntry.Escape -> sb.append(entry.unescaped ?: return null)
            is KStringEntry.Interpolation -> {
                val source = entry.source
                val value = when {
                    source.startsWith("\${") && source.endsWith("}") -> source.substring(2, source.length - 1)
                    source.length >= 3 && source[0] == '$' && source[1] == '`' && source.last() == '`' ->
                        source.substring(2, source.length - 1)
                    source.firstOrNull() == '$' -> source.drop(1)
                    else -> source
                }
                sb.append(value)
            }
        }
    }
    return sb.toString()
}

/** `null` literal or a literal string; Some-like wrapper distinguishing absent from failed. */
internal fun stringOrNullLit(expr: KExpr?): Result<String?>? {
    val e = expr?.unparen() ?: return null
    if (e is KConst && e.text == "null") return Result.success(null)
    return stringLit(e)?.let { Result.success(it) }
}

/**
 * A color expression: `Color(0xAARRGGBB)` → ARGB Long, or
 * `MaterialTheme.colorScheme.<token>` → the [ThemeColorRef] reference value.
 */
internal fun colorValue(expr: KExpr?): Long? {
    val e = expr?.unparen() ?: return null
    if (e is KCall && callName(e) == "Color") {
        val arg = e.singlePositionalArg() ?: return null
        val c = arg.unparen() as? KConst ?: return null
        val t = c.text.replace("_", "")
        return when {
            t.startsWith("0x") || t.startsWith("0X") -> t.substring(2).toULongOrNull(16)?.toLong()
            else -> t.toULongOrNull()?.toLong()
        }
    }
    e.asDot()?.let { dot ->
        // MaterialTheme.colorScheme.token
        val token = nameOf(dot.selector) ?: return null
        val inner = dot.receiver.unparen().asDot() ?: return null
        if (nameOf(inner.receiver) != "MaterialTheme") return null
        if (nameOf(inner.selector) != "colorScheme") return null
        return ThemeColorRef.token(token)
    }
    return null
}

/**
 * A shape expression: `RoundedCornerShape(N.dp)` → (N, Dp),
 * `RoundedCornerShape(P)` → (P, Percent), `RectangleShape` → (0, Dp).
 */
internal fun shapeCorner(expr: KExpr?): Pair<Int, CornerUnit>? {
    val e = expr?.unparen() ?: return null
    if (nameOf(e) == "RectangleShape") return 0 to CornerUnit.Dp
    val call = e as? KCall ?: return null
    if (callName(call) != "RoundedCornerShape") return null
    val arg = call.singlePositionalArg() ?: return null
    dpInt(arg)?.let { return it to CornerUnit.Dp }
    intLit(arg)?.let { return it to CornerUnit.Percent }
    return null
}

/** The single positional argument of a call, or null (wrong count / named / lambda). */
internal fun KCall.singlePositionalArg(): KExpr? {
    if (trailingLambdas.isNotEmpty()) return null
    val v = args.singleOrNull() ?: return null
    if (v.name != null || v.isSpread) return null
    return v.expr
}

/** An empty lambda `{}` (no parameters, no statements). */
internal fun isEmptyLambda(expr: KExpr?): Boolean {
    val l = expr?.unparen() as? KLambda ?: return false
    return l.params.isEmpty() && l.body.statements.isEmpty()
}
