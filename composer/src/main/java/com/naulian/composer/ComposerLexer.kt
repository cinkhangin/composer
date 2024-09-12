package com.naulian.composer

private const val COMPOSER_END_CHAR = Char.MIN_VALUE
private const val COMPOSER_SYMBOL_CHARS = "\"</>&|#{%}[~]\\`(*)_\n"
private const val COMPOSER_WHITESPACES = " \n"

class ComposerLexer(input: String) {
    private var cursor = 0
    private val source = input

    private val Char.isNotSpaceChar get() = this != ' '
    private val Char.isNotEndChar get() = this != COMPOSER_END_CHAR
    private val Char.isNotSymbols get() = this !in COMPOSER_SYMBOL_CHARS

    private val charNotEndChar get() = char() != COMPOSER_END_CHAR

    private fun char() = source.getOrElse(cursor) { Char.MIN_VALUE }

    fun tokenize(): List<ComposerNode> {
        val tokens = mutableListOf<ComposerNode>()
        var current = next()
        while (current.type != IElementType.EOF) {
            tokens.add(current)
            current = next()
        }
        return tokens
    }

    private fun advance(amount: Int = 1) {
        cursor += amount
    }

    fun next(): ComposerNode {
        //println(char())
        return when (val char = char()) {
            in COMPOSER_WHITESPACES -> createWhiteSpaceToken()
            '&' -> createSymbolToken(IElementType.BLOCK_SYMBOL)
            '/' -> createSymbolToken(IElementType.BLOCK_SYMBOL)
            '_' -> createSymbolToken(IElementType.BLOCK_SYMBOL)
            '"' -> createSymbolToken(IElementType.BLOCK_SYMBOL)
            '~' -> createSymbolToken(IElementType.BLOCK_SYMBOL)

            '[' -> createSymbolToken(IElementType.TABLE_START)
            ']' -> createSymbolToken(IElementType.TABLE_END)
            '|' -> createSymbolToken(IElementType.PIPE)

            '<' -> createSymbolToken(IElementType.COLOR_START)
            '>' -> createSymbolToken(IElementType.COLOR_END)

            '=' -> createBlockToken(IElementType.DIVIDER, char)
            '`' -> {
                advance()
                val start = cursor
                while (char() != '`' && charNotEndChar) advance()
                val end = cursor
                advance()
                val value = source.subSequence(start, end)
                ComposerNode(IElementType.IGNORE, value.toString())
            }

            '%' -> createBlockToken(IElementType.DATETIME, char)
            '(' -> createLinkToken()
            '{' -> createCodeToken()
            '#' -> createHeaderToken()
            '*' -> createElementToken()
            '\\' -> createEscapedToken()
            in COMPOSER_SYMBOL_CHARS -> createSymbolToken(IElementType.TEXT) //prevent memory leak
            Char.MIN_VALUE -> ComposerNode.EOF
            else -> createTextToken()
        }
    }

    private fun createWhiteSpaceToken(): ComposerNode {
        val start = cursor
        while (char().isWhitespace()) {
            advance()
        }
        val end = cursor
        val literal = source.subSequence(start, end)
        return when {
            literal.contains("\n") -> ComposerNode.create(IElementType.NEWLINE, literal)
            else -> ComposerNode.create(IElementType.WHITESPACE, literal)
        }
    }

    private fun createHeaderToken(): ComposerNode {
        advance()
        if (char() == COMPOSER_END_CHAR) {
            // early return
            return ComposerNode(IElementType.TEXT, "#")
        }

        val start = cursor
        advanceWhile { it.isNotSpaceChar && it.isNotEndChar && it.isNotSymbols }
        return when (val value = source.subSequence(start, cursor)) {
            in "123456" -> ComposerNode(IElementType.HEADER, "#$value")
            else -> ComposerNode(IElementType.COLOR_HEX, "#$value")
        }
    }

    private fun createEscapedToken(): ComposerNode {
        advance()
        if (char() == COMPOSER_END_CHAR) {
            return ComposerNode.EOF
        }
        val literal = char().toString()
        advance()
        return ComposerNode(IElementType.ESCAPE, literal)
    }

    private fun createCodeToken(): ComposerNode {
        advance() //skip opening bracket
        val start = cursor
        var level = 0
        while (charNotEndChar) {
            if (char() == '{') {
                level++
            }
            if (char() == '}') {
                if (level == 0) {
                    break
                } else level--
            }
            advance()
        }
        val end = cursor
        advance() //skip closing bracket
        val value = source.subSequence(start, end)

        var code = value.str()
        adhocMap.forEach {
            code = code.replace(it.key, it.value)
        }
        return ComposerNode(IElementType.CODE, code)
    }

    private fun createLinkToken(): ComposerNode {
        advance() //skip opening parenthesis
        val start = cursor
        while (char() != ')' && charNotEndChar) {
            advance()
        }

        val value = source.subSequence(start, cursor).str()
        advance() //skip closing parenthesis

        if (!value.contains("http")) {
            return ComposerNode(IElementType.TEXT, "($value)")
        }

        if (value.contains("@")) {
            val index = value.indexOf("@")
            val hyper = value.take(index)
            val link = value.str().replace("$hyper@", "")

            return when (hyper) {
                "img" -> ComposerNode(IElementType.IMAGE, link)
                "ytb" -> ComposerNode(IElementType.YOUTUBE, link)
                "vid" -> ComposerNode(IElementType.VIDEO, link)
                else -> ComposerNode(IElementType.HYPER_LINK, value)
            }
        }

        return ComposerNode(IElementType.LINK, value)
    }

    private fun createElementToken(): ComposerNode {
        advance()
        if (char() == COMPOSER_END_CHAR) {
            // early return
            return ComposerNode(IElementType.TEXT, "*")
        }

        val start = cursor
        advanceWhile { it.isNotSpaceChar && it.isNotEndChar && it.isNotSymbols }
        val value = source.subSequence(start, cursor)
        return ComposerNode(IElementType.ELEMENT, "*$value")
    }

    private fun createTextToken(): ComposerNode {
        val start = cursor
        advanceWhile { it.isNotSymbols && it.isNotEndChar }
        val literal = source.subSequence(start, cursor).toString()
        return ComposerNode.create(IElementType.TEXT, literal)
    }

    private fun createSymbolToken(type: String): ComposerNode {
        val value = char().toString()
        advance()
        return ComposerNode(type, value)
    }

    private fun createBlockToken(type: String, char: Char): ComposerNode {
        advance() //skip the opening char
        val start = cursor
        var prevChar = char()
        while (!(char() == char && prevChar != '\\') && charNotEndChar) {
            prevChar = char()
            advance()
        }

        val blockValue = source.subSequence(start, cursor).str()
        advance() //skip the closing char

        return when (type) {
            IElementType.DATETIME -> {
                try {
                    val value = formattedDateTime(blockValue)
                    ComposerNode(type, value)
                } catch (e: Exception) {
                    ComposerNode(IElementType.TEXT, blockValue)
                }
            }

            else -> ComposerNode(type, blockValue)
        }
    }

    private fun advanceWhile(condition: (Char) -> Boolean) {
        while (condition(char())) advance()
    }
}