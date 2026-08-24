package composer

import composer.model.Node
import composer.model.PreviewParameter

private val parameterReference = Regex("^\\$([A-Za-z_][A-Za-z0-9_]*)$")

/** True for Kotlin String types supported by the inspector's literal editor. */
internal fun isStringParameterType(type: String): Boolean {
    val normalized = type.trim().removeSuffix("?")
    return normalized == "String" || normalized == "kotlin.String"
}

/**
 * Present simple String literals as plain values in the inspector. Imported
 * expressions and interpolated strings stay verbatim so source is not lost.
 */
internal fun parameterValueForDisplay(type: String, expression: String): String {
    if (!isStringParameterType(type)) return expression
    return decodeSimpleKotlinString(expression) ?: expression
}

/** Turn an inspector value into a valid Kotlin argument/default expression. */
internal fun parameterValueForSource(type: String, value: String): String {
    if (!isStringParameterType(type)) return value.trim()
    if (type.trim().endsWith('?') && value.trim() == "null") return "null"
    return kotlinStringLiteral(value)
}

/** `$name` binds Text to a String parameter declared by the current composable. */
internal fun textParameterReference(value: String, parameters: List<PreviewParameter>): String? {
    val match = parameterReference.matchEntire(value) ?: return null
    val name = match.groupValues[1]
    return name.takeIf {
        parameters.any { parameter -> parameter.name == name && isStringParameterType(parameter.type) }
    }
}

/** Show a bound Text expression using the inspector's `$name` shorthand. */
internal fun textValueForDisplay(node: Node.Text, parameters: List<PreviewParameter>): String {
    val expression = node.textExpression.trim()
    val parameter = parameters.singleOrNull { it.name == expression }
    return if (parameter != null && isStringParameterType(parameter.type)) "${'$'}$expression" else node.text
}

private fun kotlinStringLiteral(value: String): String = buildString {
    append('"')
    for (character in value) {
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '$' -> append("\\$")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }
    append('"')
}

private fun decodeSimpleKotlinString(expression: String): String? {
    val source = expression.trim()
    if (source.length < 2 || source.first() != '"' || source.last() != '"') return null
    val result = StringBuilder()
    var index = 1
    while (index < source.lastIndex) {
        val character = source[index]
        if (character == '$') return null
        if (character != '\\') {
            result.append(character)
            index++
            continue
        }
        if (index + 1 >= source.lastIndex) return null
        when (val escaped = source[index + 1]) {
            '\\' -> result.append('\\')
            '"' -> result.append('"')
            '$' -> result.append('$')
            'n' -> result.append('\n')
            'r' -> result.append('\r')
            't' -> result.append('\t')
            else -> return null
        }
        index += 2
    }
    return result.toString()
}
