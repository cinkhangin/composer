package composer.codeparse

/**
 * Statement splitting + expression parsing over the token stream.
 *
 * Extents come FIRST (token-level splitting with depth tracking and Kotlin's
 * newline-continuation rules), parsing second — so a statement's extent never
 * depends on whether its expression parses. A statement that fails to parse
 * (or doesn't consume all its tokens) becomes [KUnknownStatement]; the mapping
 * layer preserves it verbatim as RawCode. Over-merging or over-splitting is
 * therefore non-destructive by construction.
 *
 * The expression grammar is deliberately no wider than codegen's output
 * (literals, dotted names, calls with named args and trailing lambdas,
 * `Modifier` chains, `state = it`, `!v`, `==`, `/`); everything else fails
 * closed to Unknown — the same RawCode outcome PSI-based mapping produced.
 */
internal class ParseInput(val text: String, val tokens: List<Token>, val comments: List<KComment>)

// ---- statement splitting -----------------------------------------------------

/** Previous-token kinds after which a newline does NOT end the statement. */
private val DANGLING_PUNCT = setOf(
    ".", "?.", "::", ",", "=", "==", "!=", "===", "!==", "<", "<=", ">", ">=",
    "&&", "||", "+", "-", "*", "/", "%", "+=", "-=", "*=", "/=", "%=",
    "->", "?:", "..", "..<", "!", "?", "@", "(", "[", "{",
)
private val DANGLING_IDENT = setOf("else", "try", "catch", "finally", "do", "in", "is", "as")

/** Next-token kinds that glue to the previous line (Kotlin continuation rules). */
private val CONTINUATION_PUNCT = setOf(".", "?.", "?:", "{")
private val CONTINUATION_IDENT = setOf("else", "catch", "finally")

private fun Token.isIdent(text: String): Boolean = kind == TokKind.IDENT && !backticked && this.text == text

/**
 * Exclusive end index of the statement starting at [from]. Stops AT a `;`
 * (the semicolon belongs to neither statement, like PSI's sibling leaf).
 *
 * A brace-less control header (`if (x)` / `for (…)` / `do` with no `{`)
 * consumes its body as one nested statement-unit via recursion, so the unit's
 * newlines can't end the outer statement and a completed same-line unit can't
 * leak suppression onto the NEXT statement.
 */
internal fun statementEnd(tokens: List<Token>, from: Int, to: Int): Int {
    var i = from
    var depth = 0
    var controlPending = false
    while (i < to) {
        val t = tokens[i]
        if (i > from && depth == 0 && t.newlineBefore) {
            val prev = tokens[i - 1]
            val dangling = (prev.kind == TokKind.PUNCT && prev.text in DANGLING_PUNCT) ||
                (prev.kind == TokKind.IDENT && !prev.backticked && prev.text in DANGLING_IDENT)
            val continuation = (t.kind == TokKind.PUNCT && t.text in CONTINUATION_PUNCT) ||
                (t.kind == TokKind.IDENT && !t.backticked && t.text in CONTINUATION_IDENT)
            if (!dangling && !continuation && !isAnnotationRun(tokens, from, i)) return i
        }
        if (t.kind == TokKind.PUNCT) {
            when (t.text) {
                "(", "[", "{" -> {
                    if (controlPending && t.text == "{" && depth == 0) controlPending = false
                    depth++
                }
                ")", "]", "}" -> {
                    depth--
                    if (depth < 0) return i
                    if (controlPending && depth == 0) {
                        controlPending = false
                        val next = tokens.getOrNull(i + 1)
                        if (next != null && i + 1 < to && !(next.kind == TokKind.PUNCT && next.text == "{")) {
                            i = statementEnd(tokens, i + 1, to) // brace-less body unit
                            continue
                        }
                    }
                }
                ";" -> if (depth == 0 && i > from) return i
            }
        } else if (t.kind == TokKind.IDENT && !t.backticked && depth == 0) {
            when (t.text) {
                // `while` only in statement-head position — mid-statement it's a
                // do-while tail, which has no body unit of its own.
                "if", "for", "when" -> controlPending = true
                "while" -> if (i == from) controlPending = true
                "do" -> {
                    val next = tokens.getOrNull(i + 1)
                    if (next != null && i + 1 < to && !(next.kind == TokKind.PUNCT && next.text == "{")) {
                        i = statementEnd(tokens, i + 1, to)
                        continue
                    }
                }
            }
        }
        i++
    }
    return to
}

/** `@Anno(...)` runs on their own lines glue to the annotated statement below. */
private fun isAnnotationRun(tokens: List<Token>, from: Int, to: Int): Boolean {
    var j = from
    if (j >= to || !(tokens[j].kind == TokKind.PUNCT && tokens[j].text == "@")) return false
    while (j < to) {
        if (!(tokens[j].kind == TokKind.PUNCT && tokens[j].text == "@")) return false
        j++
        if (j < to && tokens[j].kind == TokKind.IDENT && j + 1 < to &&
            tokens[j + 1].kind == TokKind.PUNCT && tokens[j + 1].text == ":"
        ) j += 2 // use-site target
        if (j >= to || tokens[j].kind != TokKind.IDENT) return false
        j++
        while (j + 1 < to && tokens[j].text == "." && tokens[j + 1].kind == TokKind.IDENT) j += 2
        if (j < to && tokens[j].kind == TokKind.PUNCT && tokens[j].text == "(" && !tokens[j].newlineBefore) {
            j = matchBracket(tokens, j, to)
            if (j == -1) return false
            j++
        }
    }
    return true
}

/** Index of the bracket matching [open] (combined (), [], {} depth), or -1. */
internal fun matchBracket(tokens: List<Token>, open: Int, to: Int): Int {
    var depth = 0
    var i = open
    while (i < to) {
        val t = tokens[i]
        if (t.kind == TokKind.PUNCT) {
            when (t.text) {
                "(", "[", "{" -> depth++
                ")", "]", "}" -> { depth--; if (depth == 0) return i }
            }
        }
        i++
    }
    return -1
}

// ---- block parsing -------------------------------------------------------------

/** Split and parse the statements in a block body's token span. */
internal fun parseBlockSpan(input: ParseInput, tokFrom: Int, tokTo: Int, bodyRange: IntRange): KBlock {
    val statements = mutableListOf<KStatement>()
    var i = tokFrom
    while (i < tokTo) {
        if (input.tokens[i].kind == TokKind.PUNCT && input.tokens[i].text == ";") { i++; continue }
        val end = statementEnd(input.tokens, i, tokTo)
        if (end <= i) break // defensive: never loop on malformed input
        statements += parseStatement(input, i, end)
        i = end
    }
    return KBlock(statements, bodyRange)
}

private fun parseStatement(input: ParseInput, from: Int, to: Int): KStatement {
    val tokens = input.tokens
    val span = tokens.subList(from, to)
    val range = tokens[from].start until tokens[to - 1].end
    val first = tokens[from]
    if (first.kind == TokKind.IDENT && !first.backticked && (first.text == "val" || first.text == "var")) {
        return parseProperty(input, from, to, range, span) ?: KUnknownStatement(range, span)
    }
    val p = ExprParser(input, from, to)
    val expr = p.parseExpression(1)
    return if (expr != null && p.pos == to) KExprStatement(expr, range, span) else KUnknownStatement(range, span)
}

private fun parseProperty(input: ParseInput, from: Int, to: Int, range: IntRange, span: List<Token>): KPropertyStatement? {
    val tokens = input.tokens
    val isVar = tokens[from].text == "var"
    var j = from + 1
    var name: String? = null
    var hasReceiver = false
    var hasType = false
    if (j < to && tokens[j].kind == TokKind.PUNCT && tokens[j].text == "(") {
        // Destructuring declaration — representable, but never a state decl.
        j = matchBracket(tokens, j, to).takeIf { it != -1 }?.plus(1) ?: return null
    } else {
        if (j >= to || tokens[j].kind != TokKind.IDENT) return null
        name = tokens[j].text
        j++
        // Receiver chain (`val A<B>.x`): the last identifier is the property name.
        while (j < to && tokens[j].kind == TokKind.PUNCT && (tokens[j].text == "<" || tokens[j].text == ".")) {
            if (tokens[j].text == "<") {
                j = matchAngle(tokens, j, to) ?: return null
                continue
            }
            hasReceiver = true
            j++
            if (j >= to || tokens[j].kind != TokKind.IDENT) return null
            name = tokens[j].text
            j++
        }
    }
    if (j < to && tokens[j].kind == TokKind.PUNCT && tokens[j].text == ":") {
        hasType = true
        j++
        j = skipType(tokens, j, to)
    }
    if (j >= to) return KPropertyStatement(isVar, name, hasReceiver, hasType, null, null, range, span)
    val t = tokens[j]
    val isDelegate = t.isIdent("by")
    val isInit = t.kind == TokKind.PUNCT && t.text == "="
    if (!isDelegate && !isInit) return null
    val p = ExprParser(input, j + 1, to)
    val expr = p.parseExpression(2) ?: return null
    if (p.pos != to) return null
    return KPropertyStatement(
        isVar, name, hasReceiver, hasType,
        delegate = if (isDelegate) expr else null,
        initializer = if (isInit) expr else null,
        range = range, tokens = span,
    )
}

/** Index past the matching `>` of the `<` at [open], or null. */
private fun matchAngle(tokens: List<Token>, open: Int, to: Int): Int? {
    var depth = 0
    var i = open
    while (i < to) {
        val t = tokens[i]
        if (t.kind == TokKind.PUNCT) {
            when (t.text) {
                "<" -> depth++
                ">" -> { depth--; if (depth == 0) return i + 1 }
                ";", "{", ")" -> return null
            }
        }
        i++
    }
    return null
}

/** Consume a type annotation: tokens until `=`, `by`, or `,` at bracket depth 0. */
private fun skipType(tokens: List<Token>, from: Int, to: Int): Int {
    var depth = 0
    var i = from
    while (i < to) {
        val t = tokens[i]
        if (depth == 0 && (t.isIdent("by") || (t.kind == TokKind.PUNCT && (t.text == "=" || t.text == ",")))) return i
        if (t.kind == TokKind.PUNCT) {
            when (t.text) {
                "(", "[", "<" -> depth++
                ")", "]", ">" -> { if (depth == 0) return i; depth-- }
            }
        }
        i++
    }
    return to
}

// ---- expression parsing ----------------------------------------------------------

/** Binary operator precedence; assignment (1) is right-associative. */
private fun binaryPrec(t: Token): Int = if (t.kind != TokKind.PUNCT) 0 else when (t.text) {
    "=", "+=", "-=", "*=", "/=", "%=" -> 1
    "||" -> 2
    "&&" -> 3
    "==", "!=", "===", "!==" -> 4
    "<", ">", "<=", ">=" -> 5
    "?:" -> 6
    "..", "..<" -> 7
    "+", "-" -> 8
    "*", "/", "%" -> 9
    else -> 0
}

internal class ExprParser(private val input: ParseInput, var pos: Int, private val to: Int) {
    private val tokens get() = input.tokens

    private fun peek(): Token? = if (pos < to) tokens[pos] else null

    private fun peekIs(text: String): Boolean =
        peek()?.let { it.kind == TokKind.PUNCT && it.text == text } == true

    fun parseExpression(minPrec: Int): KExpr? {
        var left = parseUnary() ?: return null
        while (true) {
            val t = peek() ?: break
            val prec = binaryPrec(t)
            if (prec < minPrec) break
            pos++
            val right = parseExpression(if (prec == 1) prec else prec + 1) ?: return null
            left = KBinary(left, t.text, right, left.range.first until right.range.last + 1)
        }
        return left
    }

    private fun parseUnary(): KExpr? {
        val t = peek() ?: return null
        if (t.kind == TokKind.PUNCT && (t.text == "-" || t.text == "!" || t.text == "+")) {
            pos++
            val base = parseUnary() ?: return null
            return KPrefix(t.text, base, t.start until base.range.last + 1)
        }
        return parsePostfix()
    }

    private fun parsePostfix(): KExpr? {
        var expr = parsePrimary() ?: return null
        while (true) {
            val t = peek() ?: break
            when {
                t.kind == TokKind.PUNCT && (t.text == "." || t.text == "?.") -> {
                    pos++
                    val selTok = peek() ?: return null
                    if (selTok.kind != TokKind.IDENT) return null
                    pos++
                    val selName = KName(selTok.text, selTok.start until selTok.end)
                    val selector = parseCallSuffix(selName) ?: selName
                    expr = KDot(expr, selector, safe = t.text == "?.", range = expr.range.first until selector.range.last + 1)
                }
                t.kind == TokKind.PUNCT && (t.text == "(" || t.text == "{" || t.text == "<") -> {
                    expr = parseCallSuffix(expr) ?: break
                }
                else -> break
            }
        }
        return expr
    }

    /** Call parens/type-args/trailing lambdas on [callee], or null when it isn't a call. */
    private fun parseCallSuffix(callee: KExpr): KCall? {
        var hasTypeArgs = false
        var i = pos
        if (i < to && tokens[i].kind == TokKind.PUNCT && tokens[i].text == "<") {
            // Tentative: only a type-argument list when the balanced <…> leads into ( or {.
            val after = matchAngle(tokens, i, to) ?: return null
            val next = tokens.getOrNull(after)
            if (next == null || next.kind != TokKind.PUNCT || (next.text != "(" && next.text != "{")) return null
            hasTypeArgs = true
            i = after
        }
        val args = mutableListOf<KArg>()
        var end = callee.range.last + 1
        var hasParens = false
        if (i < to && tokens[i].kind == TokKind.PUNCT && tokens[i].text == "(") {
            hasParens = true
            pos = i + 1
            if (!peekIs(")")) {
                while (true) {
                    var argName: String? = null
                    val nameTok = peek() ?: return null
                    if (nameTok.kind == TokKind.IDENT && pos + 1 < to &&
                        tokens[pos + 1].kind == TokKind.PUNCT && tokens[pos + 1].text == "="
                    ) {
                        argName = nameTok.text
                        pos += 2
                    }
                    var spread = false
                    if (peekIs("*")) { spread = true; pos++ }
                    val expr = parseExpression(2) ?: return null
                    args += KArg(argName, spread, expr)
                    when {
                        peekIs(",") -> { pos++; if (peekIs(")")) break /* trailing comma */ }
                        else -> break
                    }
                }
            }
            if (!peekIs(")")) return null
            end = tokens[pos].end
            pos++
        } else {
            pos = i
        }
        val lambdas = mutableListOf<KLambda>()
        while (peekIs("{")) {
            val l = parseLambda() ?: return null
            lambdas += l
            end = l.range.last + 1
        }
        if (!hasParens && lambdas.isEmpty() && !hasTypeArgs) return null
        return KCall(callee, hasTypeArgs, args, lambdas, callee.range.first until end)
    }

    private fun parsePrimary(): KExpr? {
        val t = peek() ?: return null
        when (t.kind) {
            TokKind.NUMBER, TokKind.CHAR -> { pos++; return KConst(t.text, t.start until t.end) }
            TokKind.STRING -> { pos++; return KString(t.stringRaw, t.stringEntries ?: emptyList(), t.start until t.end) }
            TokKind.IDENT -> {
                if (!t.backticked && (t.text == "true" || t.text == "false" || t.text == "null")) {
                    pos++
                    return KConst(t.text, t.start until t.end)
                }
                pos++
                return KName(t.text, t.start until t.end)
            }
            TokKind.PUNCT -> when (t.text) {
                "(" -> {
                    pos++
                    val inner = parseExpression(1) ?: return null
                    if (!peekIs(")")) return null
                    val end = tokens[pos].end
                    pos++
                    return KParen(inner, t.start until end)
                }
                "{" -> return parseLambda()
                else -> return null
            }
        }
    }

    private fun parseLambda(): KLambda? {
        val lb = pos
        val rb = matchBracket(tokens, lb, to)
        if (rb == -1) return null
        // A `->` at brace depth 0 separates the parameter list from the body.
        var arrow = -1
        var depth = 0
        for (j in lb + 1 until rb) {
            val t = tokens[j]
            if (t.kind == TokKind.PUNCT) {
                when (t.text) {
                    "(", "[", "{" -> depth++
                    ")", "]", "}" -> depth--
                    "->" -> if (depth == 0) { arrow = j; break }
                }
            }
        }
        val params = mutableListOf<String?>()
        if (arrow != -1) {
            var j = lb + 1
            while (j < arrow) {
                val t = tokens[j]
                when {
                    t.kind == TokKind.IDENT -> { params += t.text; j++ }
                    t.kind == TokKind.PUNCT && t.text == "(" -> {
                        params += null // destructuring — a parameter, but not a simple name
                        j = matchBracket(tokens, j, arrow).takeIf { it != -1 }?.plus(1) ?: arrow
                    }
                    else -> { params += null; j++ } // malformed — non-empty params reject downstream
                }
                if (j < arrow && tokens[j].kind == TokKind.PUNCT && tokens[j].text == ":") {
                    j = skipType(tokens, j + 1, arrow)
                }
                if (j < arrow && tokens[j].kind == TokKind.PUNCT && tokens[j].text == ",") j++
            }
        }
        val bodyFrom = if (arrow != -1) arrow + 1 else lb + 1
        val bodyStart = if (arrow != -1) tokens[arrow].end else tokens[lb].end
        val bodyRange = bodyStart until tokens[rb].start
        val body = parseBlockSpan(input, bodyFrom, rb, bodyRange)
        pos = rb + 1
        return KLambda(params, body, tokens[lb].start until tokens[rb].end)
    }
}
