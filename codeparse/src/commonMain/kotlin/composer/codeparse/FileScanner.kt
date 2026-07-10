package composer.codeparse

/**
 * File-level structure scan over the token stream: package header, imports,
 * and top-level declarations — robust on ARBITRARY lexically-valid Kotlin
 * (the IDE plugin feeds real user files). Only `fun` declarations are parsed
 * in detail; everything else is skipped as [KOtherDecl] with a balanced-brace
 * extent so member `@Composable` functions inside classes are never mistaken
 * for top-level screens. The scanner never throws: unmatched structure
 * degrades to statement-extent skips.
 *
 * Declaration ranges are PSI-compatible: first annotation/modifier (or an
 * immediately preceding KDoc block comment, which PSI owns as a child of the
 * declaration) through the closing brace / last body token.
 */
private val MODIFIER_KEYWORDS = setOf(
    "public", "private", "internal", "protected", "expect", "actual", "final",
    "open", "abstract", "sealed", "const", "external", "override", "lateinit",
    "tailrec", "vararg", "suspend", "inner", "enum", "annotation", "companion",
    "inline", "value", "infix", "operator", "data", "noinline", "crossinline", "reified",
)

internal fun scanSource(text: String): KSourceFile {
    val lexed = lex(text)
    return Scanner(text, lexed).scan()
}

private class Scanner(val text: String, lexed: LexResult) {
    val tokens = lexed.tokens
    val comments = lexed.comments
    var i = 0

    /** End of the previously consumed structure — leading comments never bind past it. */
    var prevEnd = 0

    fun scan(): KSourceFile {
        var packageEnd: Int? = null
        val imports = ArrayList<KImport>()
        val decls = ArrayList<KDeclaration>()
        while (i < tokens.size) {
            val t = tokens[i]
            when {
                t.isKeyword("package") -> {
                    packageEnd = skipDottedName(i + 1)
                    packageEnd?.let { prevEnd = it }
                }
                t.isKeyword("import") -> parseImport()?.let { imports += it; prevEnd = it.endOffset }
                isFileAnnotation() -> skipAnnotation()
                else -> {
                    val d = parseDeclaration()
                    decls += d
                    prevEnd = maxOf(prevEnd, d.range.last + 1)
                }
            }
        }
        return KSourceFile(text, packageEnd, imports, decls, comments)
    }

    fun Token.isKeyword(word: String): Boolean = kind == TokKind.IDENT && !backticked && text == word

    fun punctAt(idx: Int, op: String): Boolean =
        idx < tokens.size && tokens[idx].kind == TokKind.PUNCT && tokens[idx].text == op

    fun identAt(idx: Int): Boolean = idx < tokens.size && tokens[idx].kind == TokKind.IDENT

    /** Consume `a.b.c` starting at [from]; advances [i]; returns the end offset of the last segment. */
    fun skipDottedName(from: Int): Int? {
        var j = from
        if (!identAt(j)) { i = j; return null }
        var end = tokens[j].end
        j++
        while (punctAt(j, ".") && identAt(j + 1)) {
            end = tokens[j + 1].end
            j += 2
        }
        i = j
        return end
    }

    fun parseImport(): KImport? {
        var j = i + 1
        if (!identAt(j)) { i = j; return null }
        val path = StringBuilder(tokens[j].text)
        var end = tokens[j].end
        j++
        var allUnder = false
        while (punctAt(j, ".")) {
            if (punctAt(j + 1, "*")) {
                allUnder = true
                end = tokens[j + 1].end
                j += 2
                break
            }
            if (!identAt(j + 1)) break
            path.append('.').append(tokens[j + 1].text)
            end = tokens[j + 1].end
            j += 2
        }
        var alias: String? = null
        if (j < tokens.size && tokens[j].isKeyword("as") && identAt(j + 1) && !tokens[j].newlineBefore) {
            alias = tokens[j + 1].text
            end = tokens[j + 1].end
            j += 2
        }
        i = j
        return KImport(path.toString(), alias, allUnder, end)
    }

    fun isFileAnnotation(): Boolean =
        punctAt(i, "@") && i + 2 < tokens.size &&
            tokens[i + 1].isKeyword("file") && punctAt(i + 2, ":")

    /** Skip one `@[target:]Dotted.Name[(…)]`; returns the annotation's short name. */
    fun skipAnnotation(): String? {
        i++ // @
        if (identAt(i) && punctAt(i + 1, ":")) i += 2 // use-site target
        if (!identAt(i)) return null
        var short = tokens[i].text
        i++
        while (punctAt(i, ".") && identAt(i + 1)) {
            short = tokens[i + 1].text
            i += 2
        }
        // Arguments bind only when the paren opens on the same line.
        if (punctAt(i, "(") && !tokens[i].newlineBefore) {
            val close = matchBracket(tokens, i, tokens.size)
            i = if (close == -1) tokens.size else close + 1
        }
        return short
    }

    fun parseDeclaration(): KDeclaration {
        val declStartTok = i
        val annotations = ArrayList<String>()
        while (punctAt(i, "@")) skipAnnotation()?.let { annotations += it }
        while (i < tokens.size && tokens[i].kind == TokKind.IDENT && !tokens[i].backticked &&
            tokens[i].text in MODIFIER_KEYWORDS
        ) i++
        val declStart = declStart(declStartTok)
        if (i < tokens.size) {
            val t = tokens[i]
            when {
                t.isKeyword("fun") -> return parseFunction(declStart, annotations)
                t.isKeyword("val") || t.isKeyword("var") -> return skipProperty(declStart)
                t.isKeyword("class") || t.isKeyword("interface") || t.isKeyword("object") ->
                    return skipClassLike(declStart)
                t.isKeyword("typealias") -> return skipStatementLike(declStart, declStartTok)
            }
        }
        return skipStatementLike(declStart, declStartTok)
    }

    /**
     * Declaration start: first annotation/modifier token, extended over the
     * leading comments PSI binds to the declaration — the contiguous comment run
     * directly above (a blank line breaks the run), plus a KDoc even across
     * blank lines. Never crosses the previous declaration's end.
     */
    fun declStart(declStartTok: Int): Int {
        var start = tokens[declStartTok].start
        while (true) {
            val c = comments.lastOrNull { it.range.last < start && it.range.first >= prevEnd } ?: break
            val gap = text.substring(c.range.last + 1, start)
            if (gap.isNotBlank()) break
            val isKdoc = c.text.startsWith("/**") && c.text.length > 4
            if (!isKdoc && gap.count { it == '\n' } > 1) break
            start = c.range.first
        }
        return start
    }

    /** PSI binds a same-line trailing comment run to the declaration it follows. */
    fun extendOverTrailingComments(endOffset: Int): Int {
        var end = endOffset
        for (c in comments) {
            if (c.range.first < end) continue
            val gap = text.substring(end, c.range.first)
            if (gap.contains('\n') || gap.isNotBlank()) break
            end = c.range.last + 1
        }
        return end
    }

    fun parseFunction(declStart: Int, annotations: List<String>): KDeclaration {
        i++ // fun
        var hasTypeParams = false
        if (punctAt(i, "<")) {
            hasTypeParams = true
            i = matchAngleTokens(i) ?: return recoverDecl(declStart)
        }
        // Receiver + name: tokens up to the value-parameter `(` at angle depth 0.
        var hasReceiver = false
        var name: String? = null
        while (i < tokens.size) {
            val t = tokens[i]
            when {
                t.kind == TokKind.IDENT -> { name = t.text; i++ }
                t.kind == TokKind.PUNCT && t.text == "." -> { hasReceiver = true; i++ }
                t.kind == TokKind.PUNCT && t.text == "?" -> i++ // nullable receiver
                t.kind == TokKind.PUNCT && t.text == "<" -> {
                    hasReceiver = true // generic receiver type
                    i = matchAngleTokens(i) ?: return recoverDecl(declStart)
                }
                t.kind == TokKind.PUNCT && t.text == "(" -> break
                else -> return recoverDecl(declStart)
            }
        }
        if (!punctAt(i, "(")) return recoverDecl(declStart)
        val lparen = i
        val rparen = matchBracket(tokens, lparen, tokens.size)
        if (rparen == -1) return recoverDecl(declStart)
        val paramListText = text.substring(tokens[lparen].start, tokens[rparen].end)
        i = rparen + 1
        var hasReturnType = false
        if (punctAt(i, ":")) {
            hasReturnType = true
            i++
            skipTypeTokens()
        }
        if (i < tokens.size && tokens[i].isKeyword("where")) {
            i++
            skipTypeTokens()
        }
        var bodyBlock: KBlock? = null
        var endOffset = tokens[i - 1].end
        if (punctAt(i, "{")) {
            val lb = i
            val rb = matchBracket(tokens, lb, tokens.size)
            if (rb == -1) { i = tokens.size } else {
                bodyBlock = parseBlockSpan(
                    ParseInput(text, tokens, comments),
                    lb + 1, rb,
                    tokens[lb].end until tokens[rb].start,
                )
                endOffset = tokens[rb].end
                i = rb + 1
            }
        } else if (punctAt(i, "=")) {
            val end = statementEnd(tokens, i + 1, tokens.size)
            if (end > i + 1) endOffset = tokens[end - 1].end
            i = end
        }
        return KFunctionDecl(
            name, annotations, hasReceiver, hasTypeParams, hasReturnType,
            paramListText, bodyBlock, declStart until extendOverTrailingComments(endOffset),
        )
    }

    /** Consume type tokens until a depth-0 `{` / `=` / newline-separated next declaration. */
    fun skipTypeTokens() {
        var depth = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (depth == 0) {
                if (t.kind == TokKind.PUNCT && (t.text == "{" || t.text == "=")) return
                if (t.isKeyword("where")) return
                if (t.newlineBefore && !(t.kind == TokKind.PUNCT && (t.text == "." || t.text == "?" || t.text == "->"))) return
            }
            if (t.kind == TokKind.PUNCT) {
                when (t.text) {
                    "(", "[", "<" -> depth++
                    ")", "]", ">" -> { if (depth == 0) return; depth-- }
                }
            }
            i++
        }
    }

    fun skipProperty(declStart: Int): KDeclaration {
        // `val X = lightColorScheme(…)` / `darkColorScheme(…)` — regeneration
        // reproduces these from the design's themes; they're not foreign code.
        val themeArtifact = tokens[i].isKeyword("val") &&
            identAt(i + 1) && punctAt(i + 2, "=") &&
            i + 3 < tokens.size &&
            (tokens[i + 3].isKeyword("lightColorScheme") || tokens[i + 3].isKeyword("darkColorScheme")) &&
            punctAt(i + 4, "(")
        val end = statementEnd(tokens, i, tokens.size)
        i = end
        // Accessor clauses on following lines: [modifiers] get/set [(…)] [= expr | {…}]
        while (i < tokens.size) {
            var j = i
            while (j < tokens.size && tokens[j].kind == TokKind.IDENT && !tokens[j].backticked &&
                tokens[j].text in MODIFIER_KEYWORDS
            ) j++
            if (j >= tokens.size || tokens[j].kind != TokKind.IDENT ||
                (tokens[j].text != "get" && tokens[j].text != "set") || tokens[j].backticked
            ) break
            j++
            if (punctAt(j, "(")) {
                val close = matchBracket(tokens, j, tokens.size)
                if (close == -1) break
                j = close + 1
            }
            when {
                punctAt(j, "=") -> j = statementEnd(tokens, j + 1, tokens.size)
                punctAt(j, "{") -> {
                    val close = matchBracket(tokens, j, tokens.size)
                    if (close == -1) break
                    j = close + 1
                }
            }
            i = j
        }
        return KOtherDecl(
            declStart until extendOverTrailingComments(tokens[(i - 1).coerceAtLeast(0)].end),
            themeArtifact = themeArtifact,
        )
    }

    fun skipClassLike(declStart: Int): KDeclaration {
        i++ // class/interface/object keyword
        // Header: constructor parens, supertype calls, type params — until the body brace
        // or a newline that starts something new.
        while (i < tokens.size) {
            val t = tokens[i]
            if (t.kind == TokKind.PUNCT) {
                when (t.text) {
                    "{" -> {
                        val close = matchBracket(tokens, i, tokens.size)
                        i = if (close == -1) tokens.size else close + 1
                        return KOtherDecl(declStart until extendOverTrailingComments(tokens[i - 1].end))
                    }
                    "(", "[", "<" -> {
                        val close = if (t.text == "<") matchAngleTokens(i) else
                            matchBracket(tokens, i, tokens.size).takeIf { it != -1 }?.plus(1)
                        if (close == null) return recoverDecl(declStart)
                        i = close
                        continue
                    }
                }
            }
            if (t.newlineBefore && i > 0) {
                val prev = tokens[i - 1]
                val headerContinues = prev.kind == TokKind.PUNCT &&
                    prev.text in setOf(":", ",", ".", "(", "<", "by")
                val glue = t.kind == TokKind.PUNCT && (t.text == ":" || t.text == "," || t.text == "{" || t.text == ".")
                if (!headerContinues && !glue) break // body-less declaration
            }
            i++
        }
        return KOtherDecl(declStart until extendOverTrailingComments(tokens[(i - 1).coerceAtLeast(0)].end))
    }

    /** Recovery: skip one statement-extent from the declaration start. */
    fun recoverDecl(declStart: Int): KDeclaration {
        val restart = tokens.indexOfFirst { it.start >= declStart }.coerceAtLeast(0)
        val end = statementEnd(tokens, maxOf(restart, 0), tokens.size).coerceAtLeast(i + 1)
        i = maxOf(end, i + 1).coerceAtMost(tokens.size)
        return KOtherDecl(declStart until extendOverTrailingComments(tokens[(i - 1).coerceAtLeast(0)].end))
    }

    fun skipStatementLike(declStart: Int, declStartTok: Int): KDeclaration {
        val end = statementEnd(tokens, declStartTok, tokens.size)
        i = maxOf(end, declStartTok + 1)
        return KOtherDecl(declStart until extendOverTrailingComments(tokens[(i - 1).coerceAtLeast(0)].end))
    }

    /** Index past the matching `>`, or null (mirrors [matchAngle] for scanner use). */
    fun matchAngleTokens(open: Int): Int? {
        var depth = 0
        var j = open
        while (j < tokens.size) {
            val t = tokens[j]
            if (t.kind == TokKind.PUNCT) {
                when (t.text) {
                    "<" -> depth++
                    ">" -> { depth--; if (depth == 0) return j + 1 }
                    ";", "{" -> return null
                }
            }
            j++
        }
        return null
    }
}
