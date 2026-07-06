package composer.codeparse

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtValueArgument

/**
 * A composable call's arguments in parseable form: positional expressions, named
 * expressions, and the trailing lambda. Null when the call uses anything the
 * mapping tables don't model (spread, duplicate names, non-trailing lambda body).
 */
internal class CallShape(
    val name: String,
    val positional: List<KtExpression>,
    val named: Map<String, KtExpression>,
    val trailingLambda: KtLambdaExpression?,
) {
    /** True when every named argument is in [allowed] and positional count ≤ [maxPositional]. */
    fun argsWithin(allowed: Set<String>, maxPositional: Int = 0): Boolean =
        positional.size <= maxPositional && named.keys.all { it in allowed }
}

internal fun callShape(call: KtCallExpression): CallShape? {
    val name = callName(call) ?: return null
    if (call.typeArguments.isNotEmpty()) return null
    val positional = mutableListOf<KtExpression>()
    val named = mutableMapOf<String, KtExpression>()
    for (arg in call.valueArguments) {
        if (arg is KtLambdaArgument) continue // handled below
        val v = arg as? KtValueArgument ?: return null
        if (v.isSpread) return null
        val expr = v.getArgumentExpression() ?: return null
        val argName = v.getArgumentName()?.asName?.identifier
        if (argName == null) {
            if (named.isNotEmpty()) return null // positional after named — not codegen's shape
            positional += expr
        } else {
            if (named.put(argName, expr) != null) return null
        }
    }
    val trailing = call.lambdaArguments.singleOrNull()?.getLambdaExpression()
    if (call.lambdaArguments.size > 1) return null
    return CallShape(name, positional, named, trailing)
}

/** The named argument as a lambda (for slot args like `topBar = { … }`). */
internal fun CallShape.lambdaArg(name: String): KtLambdaExpression? =
    named[name]?.unparen() as? KtLambdaExpression
