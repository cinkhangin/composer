package composer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * Minimal Kotlin syntax highlighter (IntelliJ Darcula / Light palettes) producing
 * an [AnnotatedString]. Tokenizes by hand — no dependency. Good enough for the
 * generated, well-formed code we display; not a full Kotlin lexer.
 */
private class SyntaxColors(
    val default: Color,
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val annotation: Color,
    val function: Color,
    val type: Color,
)

private val DarkColors = SyntaxColors(
    default = Color(0xFFA9B7C6),
    keyword = Color(0xFFCC7832),
    string = Color(0xFF6A8759),
    number = Color(0xFF6897BB),
    comment = Color(0xFF808080),
    annotation = Color(0xFFBBB529),
    function = Color(0xFFFFC66D),
    type = Color(0xFFA9B7C6),
)

private val LightColors = SyntaxColors(
    default = Color(0xFF080808),
    keyword = Color(0xFF0033B3),
    string = Color(0xFF067D17),
    number = Color(0xFF1750EB),
    comment = Color(0xFF8C8C8C),
    annotation = Color(0xFF9E880D),
    function = Color(0xFF00627A),
    type = Color(0xFF000000),
)

private val keywords = setOf(
    "import", "package", "fun", "val", "var", "return", "if", "else", "when", "is", "in",
    "for", "while", "do", "class", "object", "interface", "true", "false", "null", "this",
    "super", "by", "get", "set", "as", "typealias", "companion", "private", "public",
    "internal", "override", "data", "sealed", "enum", "const",
)

fun highlightKotlin(code: String, dark: Boolean): AnnotatedString {
    val c = if (dark) DarkColors else LightColors
    val n = code.length
    return buildAnnotatedString {
        var i = 0
        while (i < n) {
            val ch = code[i]
            when {
                ch == '/' && i + 1 < n && code[i + 1] == '/' -> {
                    var j = i
                    while (j < n && code[j] != '\n') j++
                    withStyle(SpanStyle(color = c.comment)) { append(code.substring(i, j)) }
                    i = j
                }

                ch == '"' -> {
                    var j = i + 1
                    while (j < n && code[j] != '"') {
                        if (code[j] == '\\') j++
                        j++
                    }
                    if (j < n) j++ // include closing quote
                    withStyle(SpanStyle(color = c.string)) { append(code.substring(i, j.coerceAtMost(n))) }
                    i = j
                }

                ch == '@' -> {
                    var j = i + 1
                    while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                    withStyle(SpanStyle(color = c.annotation)) { append(code.substring(i, j)) }
                    i = j
                }

                ch.isDigit() -> {
                    val j = readNumber(code, i, n)
                    withStyle(SpanStyle(color = c.number)) { append(code.substring(i, j)) }
                    i = j
                }

                ch.isLetter() || ch == '_' -> {
                    var j = i
                    while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                    val word = code.substring(i, j)
                    var k = j
                    while (k < n && (code[k] == ' ' || code[k] == '\t')) k++
                    val color = when {
                        word in keywords -> c.keyword
                        k < n && code[k] == '(' -> c.function
                        word.first().isUpperCase() -> c.type
                        else -> c.default
                    }
                    withStyle(SpanStyle(color = color)) { append(word) }
                    i = j
                }

                else -> {
                    var j = i
                    while (j < n && !isTokenStart(code[j])) j++
                    if (j == i) j = i + 1
                    withStyle(SpanStyle(color = c.default)) { append(code.substring(i, j)) }
                    i = j
                }
            }
        }
    }
}

private fun isTokenStart(ch: Char): Boolean =
    ch.isLetterOrDigit() || ch == '_' || ch == '"' || ch == '@' || ch == '/'

private fun readNumber(code: String, start: Int, n: Int): Int {
    var j = start
    if (code[j] == '0' && j + 1 < n && (code[j + 1] == 'x' || code[j + 1] == 'X')) {
        j += 2
        while (j < n && (code[j].isDigit() || code[j] in 'a'..'f' || code[j] in 'A'..'F')) j++
        return j
    }
    while (j < n && code[j].isDigit()) j++
    if (j < n && code[j] == '.' && j + 1 < n && code[j + 1].isDigit()) {
        j++
        while (j < n && code[j].isDigit()) j++
    }
    if (j < n && (code[j] == 'f' || code[j] == 'F' || code[j] == 'L')) j++
    return j
}
