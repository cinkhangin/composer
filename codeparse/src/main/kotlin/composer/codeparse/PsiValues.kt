package composer.codeparse

import composer.model.CornerUnit
import composer.model.ThemeColorRef
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument

/**
 * Literal/expression readers for the syntactic (no-resolve) parser. Every reader
 * returns null on anything it doesn't recognize — the caller then falls back to
 * [composer.model.Node.RawCode] for the whole statement. These are the exact
 * inverses of what CodeGen emits; the accepted grammar is deliberately no wider
 * than "codegen output plus trivially-equivalent forms".
 */

internal fun KtExpression.unparen(): KtExpression {
    var e: KtExpression = this
    while (e is KtParenthesizedExpression) e = e.expression ?: return e
    return e
}

/** The called name of a plain `Name(...)` call, or null. */
internal fun callName(call: KtCallExpression): String? =
    (call.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()

/** A plain identifier reference (`innerPadding`, `Modifier`), or null. */
internal fun nameOf(expr: KtExpression?): String? =
    (expr?.unparen() as? KtNameReferenceExpression)?.getReferencedName()

/** `A.b` → "A" to "b" for simple two-part dotted names, or null. */
internal fun dottedName(expr: KtExpression?): Pair<String, String>? {
    val dot = expr?.unparen() as? KtDotQualifiedExpression ?: return null
    val receiver = nameOf(dot.receiverExpression) ?: return null
    val selector = nameOf(dot.selectorExpression) ?: return null
    return receiver to selector
}

/** Plain (optionally negative) integer literal. */
internal fun intLit(expr: KtExpression?): Int? {
    val e = expr?.unparen() ?: return null
    if (e is KtPrefixExpression) {
        if (e.operationReference.getReferencedName() != "-") return null
        return intLit(e.baseExpression)?.let { -it }
    }
    val c = e as? KtConstantExpression ?: return null
    return c.text.toIntOrNull()
}

/** `N.dp` / `N.sp` (optionally negative: `-8.dp` parses as `-(8.dp)`). */
internal fun unitInt(expr: KtExpression?, unit: String): Int? {
    val e = expr?.unparen() ?: return null
    if (e is KtPrefixExpression) {
        if (e.operationReference.getReferencedName() != "-") return null
        return unitInt(e.baseExpression, unit)?.let { -it }
    }
    val dot = e as? KtDotQualifiedExpression ?: return null
    if (nameOf(dot.selectorExpression) != unit) return null
    return intLit(dot.receiverExpression)
}

internal fun dpInt(expr: KtExpression?): Int? = unitInt(expr, "dp")
internal fun spInt(expr: KtExpression?): Int? = unitInt(expr, "sp")

/** Float literal with codegen's `f` suffix (`0.5f`, `16f`), optionally negative. */
internal fun floatLit(expr: KtExpression?): Float? {
    val e = expr?.unparen() ?: return null
    if (e is KtPrefixExpression) {
        if (e.operationReference.getReferencedName() != "-") return null
        return floatLit(e.baseExpression)?.let { -it }
    }
    val c = e as? KtConstantExpression ?: return null
    val t = c.text
    if (!t.endsWith("f") && !t.endsWith("F")) return null
    return t.dropLast(1).toFloatOrNull()
}

internal fun boolLit(expr: KtExpression?): Boolean? = when ((expr?.unparen() as? KtConstantExpression)?.text) {
    "true" -> true
    "false" -> false
    else -> null
}

/**
 * A compile-time string: a non-raw template with only literal/escape entries
 * (the exact inverse of CodeGen.esc). Any interpolation or `"""` → null.
 */
internal fun stringLit(expr: KtExpression?): String? {
    val s = expr?.unparen() as? KtStringTemplateExpression ?: return null
    if (s.text.startsWith("\"\"\"")) return null
    val sb = StringBuilder()
    for (entry in s.entries) {
        when (entry) {
            is KtLiteralStringTemplateEntry -> sb.append(entry.text)
            is KtEscapeStringTemplateEntry -> sb.append(entry.unescapedValue)
            else -> return null // interpolation — not a literal
        }
    }
    return sb.toString()
}

/** `null` literal or a literal string; Some-like wrapper distinguishing absent from failed. */
internal fun stringOrNullLit(expr: KtExpression?): Result<String?>? {
    val e = expr?.unparen() ?: return null
    if (e is KtConstantExpression && e.text == "null") return Result.success(null)
    return stringLit(e)?.let { Result.success(it) }
}

/**
 * A color expression: `Color(0xAARRGGBB)` → ARGB Long, or
 * `MaterialTheme.colorScheme.<token>` → the [ThemeColorRef] reference value.
 */
internal fun colorValue(expr: KtExpression?): Long? {
    val e = expr?.unparen() ?: return null
    if (e is KtCallExpression && callName(e) == "Color") {
        val arg = e.singlePositionalArg() ?: return null
        val c = arg.unparen() as? KtConstantExpression ?: return null
        val t = c.text.replace("_", "")
        return when {
            t.startsWith("0x") || t.startsWith("0X") -> t.substring(2).toULongOrNull(16)?.toLong()
            else -> t.toULongOrNull()?.toLong()
        }
    }
    if (e is KtDotQualifiedExpression) {
        // MaterialTheme.colorScheme.token
        val token = nameOf(e.selectorExpression) ?: return null
        val inner = e.receiverExpression.unparen() as? KtDotQualifiedExpression ?: return null
        if (nameOf(inner.receiverExpression) != "MaterialTheme") return null
        if (nameOf(inner.selectorExpression) != "colorScheme") return null
        return ThemeColorRef.token(token)
    }
    return null
}

/**
 * A shape expression: `RoundedCornerShape(N.dp)` → (N, Dp),
 * `RoundedCornerShape(P)` → (P, Percent), `RectangleShape` → (0, Dp).
 */
internal fun shapeCorner(expr: KtExpression?): Pair<Int, CornerUnit>? {
    val e = expr?.unparen() ?: return null
    if (nameOf(e) == "RectangleShape") return 0 to CornerUnit.Dp
    val call = e as? KtCallExpression ?: return null
    if (callName(call) != "RoundedCornerShape") return null
    val arg = call.singlePositionalArg() ?: return null
    dpInt(arg)?.let { return it to CornerUnit.Dp }
    intLit(arg)?.let { return it to CornerUnit.Percent }
    return null
}

/** The single positional argument of a call, or null (wrong count / named / lambda). */
internal fun KtCallExpression.singlePositionalArg(): KtExpression? {
    if (lambdaArguments.isNotEmpty()) return null
    val args = valueArguments.filterNot { it is org.jetbrains.kotlin.psi.KtLambdaArgument }
    val v = args.singleOrNull() as? KtValueArgument ?: return null
    if (v.getArgumentName() != null) return null
    return v.getArgumentExpression()
}

/** An empty lambda `{}` (no parameters, no statements). */
internal fun isEmptyLambda(expr: KtExpression?): Boolean {
    val l = expr?.unparen() as? KtLambdaExpression ?: return false
    return l.valueParameters.isEmpty() && (l.bodyExpression?.statements ?: emptyList()).isEmpty()
}
