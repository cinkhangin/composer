package composer.codeparse

import composer.model.ComposablePreview
import composer.model.PreviewParameter

/** Source pairing between a real composable and the preview call that renders it. */
internal data class PreviewBinding(
    val targetName: String,
    val previewFunctionName: String,
    val previewFunctionRange: IntRange,
    val callRange: IntRange,
    val arguments: List<KArg>,
)

/**
 * Find block-body `@Preview @Composable` functions that contain one call to a
 * real top-level composable. A wrapper such as `AppTheme { Greeting("World") }`
 * is supported; ambiguous previews are ignored instead of guessed.
 */
internal fun previewBindings(file: KSourceFile, targetNames: Set<String>): Map<String, PreviewBinding> {
    val result = linkedMapOf<String, PreviewBinding>()
    for (preview in file.declarations.filterIsInstance<KFunctionDecl>()) {
        if ("Preview" !in preview.annotationNames || "Composable" !in preview.annotationNames) continue
        val previewName = preview.name ?: continue
        val calls = buildList {
            preview.bodyBlock?.statements?.forEach { statement ->
                statement.expressions().forEach { expression -> expression.collectTargetCalls(targetNames, this) }
            }
        }
        val targets = calls.mapNotNull(::callName).distinct()
        if (targets.size != 1) continue
        val target = targets.single()
        if (target in result) continue // multiple previews: keep the first deterministic pairing
        val call = calls.first { callName(it) == target }
        result[target] = PreviewBinding(target, previewName, preview.range, call.range, call.args)
    }
    return result
}

internal fun PreviewBinding.toModelPreview(source: String, paramList: String): ComposablePreview {
    val declarations = parsePreviewParameters(paramList)
    val positional = arguments.filter { it.name == null }.iterator()
    val named = arguments.filter { it.name != null }.associateBy { it.name }
    val parameters = declarations
        .filterNot { parameter ->
            (parameter.name == "onBack" || parameter.name.startsWith("onNavigateTo")) &&
                "-> Unit" in parameter.type
        }
        .map { parameter ->
        val argument = named[parameter.name] ?: if (positional.hasNext()) positional.next() else null
        PreviewParameter(
            name = parameter.name,
            type = parameter.type,
            expression = argument?.expr?.let { source.substring(it.range.first, it.range.last + 1) }
                ?: parameter.defaultExpression,
        )
    }
    return ComposablePreview(previewFunctionName, parameters)
}

private data class ParameterDecl(val name: String, val type: String, val defaultExpression: String)

/** Conservative value-parameter splitter; nested function/generic types remain intact. */
private fun parsePreviewParameters(paramList: String): List<ParameterDecl> {
    val inner = paramList.trim().removePrefix("(").removeSuffix(")")
    if (inner.isBlank()) return emptyList()
    return splitTopLevel(inner).mapNotNull { raw ->
        val colon = topLevelIndexOf(raw, ':')
        if (colon <= 0) return@mapNotNull null
        val name = raw.substring(0, colon).trim().substringAfterLast(' ').trim('`')
        if (!name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) return@mapNotNull null
        val equals = topLevelIndexOf(raw, '=', colon + 1)
        val type = raw.substring(colon + 1, if (equals < 0) raw.length else equals).trim()
        val default = if (equals < 0) "" else raw.substring(equals + 1).trim()
        ParameterDecl(name, type, default)
    }
}

private fun splitTopLevel(text: String): List<String> {
    val result = mutableListOf<String>()
    var start = 0
    var round = 0
    var square = 0
    var curly = 0
    var angle = 0
    var quote = '\u0000'
    var escaped = false
    for (i in text.indices) {
        val c = text[i]
        if (quote != '\u0000') {
            if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == quote) quote = '\u0000'
            continue
        }
        when (c) {
            '\'', '"' -> quote = c
            '(' -> round++
            ')' -> round--
            '[' -> square++
            ']' -> square--
            '{' -> curly++
            '}' -> curly--
            '<' -> angle++
            '>' -> if (angle > 0) angle--
            ',' -> if (round == 0 && square == 0 && curly == 0 && angle == 0) {
                result += text.substring(start, i).trim()
                start = i + 1
            }
        }
    }
    result += text.substring(start).trim()
    return result.filter { it.isNotEmpty() }
}

private fun topLevelIndexOf(text: String, needle: Char, from: Int = 0): Int {
    var round = 0
    var square = 0
    var curly = 0
    var angle = 0
    for (i in text.indices) {
        when (text[i]) {
            '(' -> round++
            ')' -> round--
            '[' -> square++
            ']' -> square--
            '{' -> curly++
            '}' -> curly--
            '<' -> angle++
            '>' -> if (angle > 0) angle--
            needle -> if (i >= from && round == 0 && square == 0 && curly == 0 && angle == 0) return i
        }
    }
    return -1
}

private fun KStatement.expressions(): List<KExpr> = when (this) {
    is KExprStatement -> listOf(expr)
    is KPropertyStatement -> listOfNotNull(delegate, initializer)
    is KUnknownStatement -> emptyList()
}

private fun KExpr.collectTargetCalls(targetNames: Set<String>, out: MutableList<KCall>) {
    when (this) {
        is KCall -> {
            if (callName(this) in targetNames) out += this
            args.forEach { it.expr.collectTargetCalls(targetNames, out) }
            trailingLambdas.forEach { lambda ->
                lambda.body.statements.forEach { statement ->
                    statement.expressions().forEach { it.collectTargetCalls(targetNames, out) }
                }
            }
        }
        is KDot -> {
            receiver.collectTargetCalls(targetNames, out)
            selector?.collectTargetCalls(targetNames, out)
        }
        is KLambda -> body.statements.forEach { statement ->
            statement.expressions().forEach { it.collectTargetCalls(targetNames, out) }
        }
        is KParen -> inner?.collectTargetCalls(targetNames, out)
        is KPrefix -> base?.collectTargetCalls(targetNames, out)
        is KBinary -> {
            left?.collectTargetCalls(targetNames, out)
            right?.collectTargetCalls(targetNames, out)
        }
        is KName, is KConst, is KSourceExpr, is KString -> Unit
    }
}
