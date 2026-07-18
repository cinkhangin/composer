package composer.codeparse

import composer.model.DesignTheme
import composer.model.NamedTheme

internal data class ParsedThemes(val themes: List<NamedTheme>, val active: Int)

/**
 * Annotation-free, module-wide Material theme extraction.
 *
 * This recognizes ordinary top-level `Color(...)` constants and
 * `lightColorScheme` / `darkColorScheme` properties, then prefers schemes
 * referenced by a composable that calls `MaterialTheme`. Resolution is
 * deliberately syntactic and conservative: duplicate property names are
 * treated as ambiguous, while unresolved roles keep the Material baseline.
 */
internal object ThemeParser {
    private data class Property(val name: String, val initializer: KExpr)
    private data class Scheme(val sourceName: String, val named: NamedTheme)

    fun parse(files: List<SourceFile>): ParsedThemes {
        val scanned = files.map { it to scanSource(it.text) }
        val properties = scanned.flatMap { (file, source) ->
            source.declarations
                .filterIsInstance<KOtherDecl>()
                .filter { it.kind == OtherKind.Property }
                .mapNotNull { parseProperty(file.text, it) }
        }

        // Only globally unambiguous simple names participate in cross-file
        // resolution. This mirrors the module composable-name policy.
        val uniqueProperties = properties.groupBy { it.name }
            .filterValues { it.size == 1 }
            .mapValues { (_, values) -> values.single() }

        val colors = mutableMapOf<String, Long>()
        var changed: Boolean
        do {
            changed = false
            for ((name, property) in uniqueProperties) {
                if (name in colors) continue
                resolveColor(property.initializer, colors)?.let { value ->
                    colors[name] = value
                    changed = true
                }
            }
        } while (changed)

        val schemes = uniqueProperties.values.mapNotNull { property ->
            val call = property.initializer.unparen() as? KCall ?: return@mapNotNull null
            val builder = callName(call) ?: return@mapNotNull null
            if (builder != "lightColorScheme" && builder != "darkColorScheme") return@mapNotNull null
            var theme = DesignTheme(dark = builder == "darkColorScheme")
            for (arg in call.args) {
                val token = arg.name ?: continue
                if (token !in DesignTheme.TOKENS) continue
                resolveColor(arg.expr, colors)?.let { theme = theme.set(token, it) }
            }
            Scheme(property.name, NamedTheme(displayName(property.name), theme))
        }
        if (schemes.isEmpty()) return ParsedThemes(emptyList(), 0)

        val wrappers = scanned.flatMap { (file, source) ->
            source.declarations.filterIsInstance<KFunctionDecl>().mapNotNull { fn ->
                val body = fn.bodyBlock ?: return@mapNotNull null
                val tokens = body.statements.flatMap { it.tokens }
                if ("Composable" !in fn.annotationNames || tokens.none { it.text == "MaterialTheme" }) {
                    return@mapNotNull null
                }
                // Body references identify the schemes owned by this wrapper.
                // A generated AppTheme mentions only its active scheme in a
                // default parameter but still owns every declared scheme, so
                // parameter names must not narrow this set.
                val names = tokens.filter { it.kind == TokKind.IDENT }.mapTo(linkedSetOf()) { it.text }
                val bodyText = file.text.substring(body.bodyRange.first, body.bodyRange.last + 1)
                ThemeWrapper(fn.paramListText, bodyText, names)
            }
        }
        val referencedNames = wrappers.flatMapTo(linkedSetOf()) { it.names }
        val selected = schemes.filter { it.sourceName in referencedNames }.ifEmpty { schemes }
        val activeName = findActiveScheme(selected, wrappers)
        val active = selected.indexOfFirst { it.sourceName == activeName }.takeIf { it >= 0 }
            ?: selected.indexOfFirst { !it.named.theme.dark }.coerceAtLeast(0)
        return ParsedThemes(selected.map { it.named }, active)
    }

    private data class ThemeWrapper(
        val params: String,
        val body: String,
        val names: Set<String>,
    )

    private fun findActiveScheme(schemes: List<Scheme>, wrappers: List<ThemeWrapper>): String? {
        for (wrapper in wrappers) {
            for (scheme in schemes) {
                if (Regex("""=\s*${Regex.escape(scheme.sourceName)}\b""").containsMatchIn(wrapper.params)) {
                    return scheme.sourceName
                }
                if (Regex("""colorScheme\s*=\s*${Regex.escape(scheme.sourceName)}\b""").containsMatchIn(wrapper.body)) {
                    return scheme.sourceName
                }
            }
            val darkDefault = Regex("""darkTheme\s*:\s*Boolean\s*=\s*(true|false)""")
                .find(wrapper.params)?.groupValues?.get(1)?.toBooleanStrictOrNull()
            if (darkDefault != null) {
                return schemes.firstOrNull { it.named.theme.dark == darkDefault }?.sourceName
            }
        }
        return null
    }

    private fun parseProperty(text: String, declaration: KOtherDecl): Property? {
        val declarationText = text.substring(declaration.range.first, declaration.range.last + 1)
        val firstLex = lex(declarationText)
        val valToken = firstLex.tokens.firstOrNull {
            it.kind == TokKind.IDENT && !it.backticked && (it.text == "val" || it.text == "var")
        } ?: return null
        val propertyText = declarationText.substring(valToken.start)
        val lexed = lex(propertyText)
        if (lexed.tokens.isEmpty()) return null
        val block = parseBlockSpan(
            ParseInput(propertyText, lexed.tokens, lexed.comments),
            0,
            lexed.tokens.size,
            propertyText.indices,
        )
        val property = block.statements.singleOrNull() as? KPropertyStatement ?: return null
        val name = property.name ?: return null
        return Property(name, property.initializer ?: return null)
    }

    private fun resolveColor(expr: KExpr?, colors: Map<String, Long>): Long? {
        colorValue(expr)?.let { return it }
        val value = expr?.unparen() ?: return null
        nameOf(value)?.let { colors[it]?.let { color -> return color } }
        val dot = value.asDot() ?: return null
        if (nameOf(dot.receiver) != "Color") return null
        return when (nameOf(dot.selector)) {
            "Black" -> 0xFF000000
            "DarkGray" -> 0xFF444444
            "Gray" -> 0xFF888888
            "LightGray" -> 0xFFCCCCCC
            "White" -> 0xFFFFFFFF
            "Red" -> 0xFFFF0000
            "Green" -> 0xFF00FF00
            "Blue" -> 0xFF0000FF
            "Yellow" -> 0xFFFFFF00
            "Cyan" -> 0xFF00FFFF
            "Magenta" -> 0xFFFF00FF
            "Transparent" -> 0x00000000
            else -> null
        }
    }

    private fun displayName(propertyName: String): String = when {
        propertyName.endsWith("ColorScheme") -> propertyName.removeSuffix("ColorScheme")
        propertyName.endsWith("Colors") -> propertyName.removeSuffix("Colors")
        propertyName.endsWith("Scheme") -> propertyName.removeSuffix("Scheme")
        else -> propertyName
    }.ifEmpty { propertyName }
}
