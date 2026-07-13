package composer.codeparse

/**
 * Hand-rolled Kotlin lexer (common-safe, no regex, no PSI). Produces the token
 * stream the file scanner and statement parser consume; comments go to a side
 * list (they're attached to statements by offset, never by token position).
 *
 * String templates are lexed as ONE atomic token each — `${…}` bodies are
 * skipped with full nested string/comment/brace balancing, so template braces
 * can never leak into the scanner's depth tracking. Raw strings and nested
 * block comments (Kotlin allows nesting) are handled. The lexer never throws:
 * malformed input (unterminated string, stray char) degrades to best-effort
 * tokens whose statements later fail to parse and become RawCode.
 */
internal enum class TokKind { IDENT, NUMBER, STRING, CHAR, PUNCT }

internal class Token(
    val kind: TokKind,
    val start: Int,
    val end: Int, // exclusive
    /** Identifier name (backticks stripped) / number text / operator text / raw source for strings+chars. */
    val text: String,
    /** A line break (incl. inside a block comment) separates this token from the previous one. */
    val newlineBefore: Boolean,
    val backticked: Boolean = false,
    val stringRaw: Boolean = false,
    val stringEntries: List<KStringEntry>? = null,
)

internal class LexResult(val tokens: List<Token>, val comments: List<KComment>)

private val PUNCT3 = listOf("===", "!==", "..<")
private val PUNCT2 = listOf(
    "?.", "?:", "::", "->", "==", "!=", "<=", ">=", "&&", "||",
    "++", "--", "+=", "-=", "*=", "/=", "%=", "..", "!!",
)

internal fun lex(text: String): LexResult {
    val tokens = ArrayList<Token>()
    val comments = ArrayList<KComment>()
    val n = text.length
    var i = 0
    var newline = false

    fun add(kind: TokKind, start: Int, end: Int, tokText: String, backticked: Boolean = false, raw: Boolean = false, entries: List<KStringEntry>? = null) {
        tokens += Token(kind, start, end, tokText, newline, backticked, raw, entries)
        newline = false
    }

    if (text.startsWith("#!")) while (i < n && text[i] != '\n') i++

    while (i < n) {
        val c = text[i]
        when {
            c == '\n' -> { newline = true; i++ }
            c.isWhitespace() -> i++
            c == '/' && i + 1 < n && text[i + 1] == '/' -> {
                val start = i
                while (i < n && text[i] != '\n') i++
                comments += KComment(text.substring(start, i), start until i)
            }
            c == '/' && i + 1 < n && text[i + 1] == '*' -> {
                val start = i
                i = skipBlockComment(text, i)
                comments += KComment(text.substring(start, i), start until i)
                if (text.indexOf('\n', start) in start until i) newline = true
            }
            c == '"' -> {
                val start = i
                val entries = ArrayList<KStringEntry>()
                val raw = text.startsWith("\"\"\"", i)
                i = scanString(text, i, entries)
                add(TokKind.STRING, start, i, text.substring(start, i), raw = raw, entries = entries)
            }
            c == '\'' -> {
                val start = i
                i = skipCharLiteral(text, i)
                add(TokKind.CHAR, start, i, text.substring(start, i))
            }
            c.isDigit() -> {
                val start = i
                i = scanNumber(text, i)
                add(TokKind.NUMBER, start, i, text.substring(start, i))
            }
            c == '`' -> {
                val start = i
                i++
                while (i < n && text[i] != '`' && text[i] != '\n') i++
                val name = text.substring(start + 1, i)
                if (i < n && text[i] == '`') i++
                add(TokKind.IDENT, start, i, name, backticked = true)
            }
            c.isLetter() || c == '_' -> {
                val start = i
                while (i < n && (text[i].isLetterOrDigit() || text[i] == '_')) i++
                add(TokKind.IDENT, start, i, text.substring(start, i))
            }
            else -> {
                val start = i
                val three = if (i + 3 <= n) text.substring(i, i + 3) else ""
                val two = if (i + 2 <= n) text.substring(i, i + 2) else ""
                val op = when {
                    three in PUNCT3 -> three
                    two in PUNCT2 -> two
                    else -> c.toString()
                }
                i += op.length
                add(TokKind.PUNCT, start, i, op)
            }
        }
    }
    return LexResult(tokens, comments)
}

/** From the opening `/*` past the matching `*/` — Kotlin block comments nest. */
private fun skipBlockComment(text: String, from: Int): Int {
    var i = from + 2
    var depth = 1
    while (i < text.length) {
        when {
            text.startsWith("/*", i) -> { depth++; i += 2 }
            text.startsWith("*/", i) -> { depth--; i += 2; if (depth == 0) return i }
            else -> i++
        }
    }
    return text.length
}

/**
 * From the opening quote past the closing quote, collecting template [entries].
 * Escaped strings end at an unescaped `"` (or, error-tolerantly, at a newline);
 * raw strings end at `"""` with any extra quotes counted as content.
 */
private fun scanString(text: String, from: Int, entries: MutableList<KStringEntry>): Int {
    val n = text.length
    if (text.startsWith("\"\"\"", from)) {
        var i = from + 3
        val lit = StringBuilder()
        fun flush() { if (lit.isNotEmpty()) { entries += KStringEntry.Literal(lit.toString()); lit.clear() } }
        while (i < n) {
            if (text.startsWith("\"\"\"", i)) {
                // Extra quotes before the terminator are content: """a"""" == a"
                var extra = 0
                while (i + 3 + extra < n && text[i + 3 + extra] == '"') extra++
                repeat(extra) { lit.append('"') }
                flush()
                return i + 3 + extra
            }
            if (text[i] == '$' && i + 1 < n && templateStarts(text[i + 1])) {
                flush()
                val start = i
                i = skipInterpolation(text, i)
                entries += KStringEntry.Interpolation(text.substring(start, i))
            } else {
                lit.append(text[i]); i++
            }
        }
        flush()
        return n
    }
    var i = from + 1
    val lit = StringBuilder()
    fun flush() { if (lit.isNotEmpty()) { entries += KStringEntry.Literal(lit.toString()); lit.clear() } }
    while (i < n) {
        when (val c = text[i]) {
            '"' -> { flush(); return i + 1 }
            '\n' -> { flush(); return i } // unterminated — recover at line end
            '\\' -> {
                flush()
                val (unescaped, next) = readEscape(text, i)
                entries += KStringEntry.Escape(unescaped)
                i = next
            }
            '$' -> {
                if (i + 1 < n && templateStarts(text[i + 1])) {
                    flush()
                    val start = i
                    i = skipInterpolation(text, i)
                    entries += KStringEntry.Interpolation(text.substring(start, i))
                } else {
                    lit.append(c); i++
                }
            }
            else -> { lit.append(c); i++ }
        }
    }
    flush()
    return n
}

private fun templateStarts(c: Char): Boolean = c == '{' || c == '`' || c == '_' || c.isLetter()

/** From the `$` past the interpolation (`$name` or a brace-balanced `${…}`). */
private fun skipInterpolation(text: String, from: Int): Int {
    val n = text.length
    var i = from + 1
    if (i < n && text[i] == '{') return skipTemplateExpr(text, i)
    if (i < n && text[i] == '`') {
        i++
        while (i < n && text[i] != '`' && text[i] != '\n') i++
        return if (i < n && text[i] == '`') i + 1 else i
    }
    while (i < n && (text[i].isLetterOrDigit() || text[i] == '_')) i++
    return i
}

/** From the `{` past the matching `}`, respecting nested strings/chars/comments. */
private fun skipTemplateExpr(text: String, from: Int): Int {
    val n = text.length
    var i = from
    var depth = 0
    while (i < n) {
        when {
            text[i] == '{' -> { depth++; i++ }
            text[i] == '}' -> { depth--; i++; if (depth == 0) return i }
            text[i] == '"' -> i = scanString(text, i, ArrayList())
            text[i] == '\'' -> i = skipCharLiteral(text, i)
            text.startsWith("//", i) -> while (i < n && text[i] != '\n') i++
            text.startsWith("/*", i) -> i = skipBlockComment(text, i)
            else -> i++
        }
    }
    return n
}

private fun skipCharLiteral(text: String, from: Int): Int {
    val n = text.length
    var i = from + 1
    if (i < n && text[i] == '\\') {
        i = readEscape(text, i).second
    } else if (i < n && text[i] != '\'' && text[i] != '\n') {
        i++
    }
    return if (i < n && text[i] == '\'') i + 1 else i
}

/** From the backslash: the decoded character (null = invalid escape) and the end index. */
private fun readEscape(text: String, from: Int): Pair<String?, Int> {
    val n = text.length
    val c = if (from + 1 < n) text[from + 1] else return null to n
    return when (c) {
        'n' -> "\n" to from + 2
        't' -> "\t" to from + 2
        'b' -> "\b" to from + 2
        'r' -> "\r" to from + 2
        '\'' -> "'" to from + 2
        '"' -> "\"" to from + 2
        '\\' -> "\\" to from + 2
        '$' -> "$" to from + 2
        'u' -> {
            if (from + 6 <= n) {
                val hex = text.substring(from + 2, from + 6)
                val code = hex.toIntOrNull(16)
                if (code != null) code.toChar().toString() to from + 6 else null to from + 2
            } else null to from + 2
        }
        else -> null to from + 2
    }
}

private fun scanNumber(text: String, from: Int): Int {
    val n = text.length
    var i = from
    if (text.startsWith("0x", i) || text.startsWith("0X", i) || text.startsWith("0b", i) || text.startsWith("0B", i)) {
        i += 2
        while (i < n && (text[i].isLetterOrDigit() || text[i] == '_')) i++
        return i
    }
    while (i < n && (text[i].isDigit() || text[i] == '_')) i++
    // Fraction: a '.' is part of the number only when a digit follows (1.dp / 1..2 stop here).
    if (i + 1 < n && text[i] == '.' && text[i + 1].isDigit()) {
        i++
        while (i < n && (text[i].isDigit() || text[i] == '_')) i++
    }
    if (i < n && (text[i] == 'e' || text[i] == 'E')) {
        var j = i + 1
        if (j < n && (text[j] == '+' || text[j] == '-')) j++
        if (j < n && text[j].isDigit()) {
            i = j
            while (i < n && text[i].isDigit()) i++
        }
    }
    while (i < n && text[i] in "fFlLuU") i++
    return i
}
