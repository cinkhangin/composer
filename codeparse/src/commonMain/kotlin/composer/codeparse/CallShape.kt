package composer.codeparse

/**
 * A composable call's arguments in parseable form: positional expressions, named
 * expressions, and the trailing lambda. Null when the call uses anything the
 * mapping tables don't model (spread, duplicate names, type arguments, more
 * than one trailing lambda).
 */
internal class CallShape(
    val name: String,
    val positional: List<KExpr>,
    val named: Map<String, KExpr>,
    val trailingLambda: KLambda?,
) {
    /** True when every named argument is in [allowed] and positional count ≤ [maxPositional]. */
    fun argsWithin(allowed: Set<String>, maxPositional: Int = 0): Boolean =
        positional.size <= maxPositional && named.keys.all { it in allowed }
}

internal fun callShape(call: KCall): CallShape? {
    val name = callName(call) ?: return null
    if (call.hasTypeArgs) return null
    val positional = mutableListOf<KExpr>()
    val named = mutableMapOf<String, KExpr>()
    for (arg in call.args) {
        if (arg.isSpread) return null
        if (arg.name == null) {
            if (named.isNotEmpty()) return null // positional after named — not codegen's shape
            positional += arg.expr
        } else {
            if (named.put(arg.name, arg.expr) != null) return null
        }
    }
    if (call.trailingLambdas.size > 1) return null
    return CallShape(name, positional, named, call.trailingLambdas.singleOrNull())
}

/** The named argument as a lambda (for slot args like `topBar = { … }`). */
internal fun CallShape.lambdaArg(name: String): KLambda? =
    named[name]?.unparen() as? KLambda
