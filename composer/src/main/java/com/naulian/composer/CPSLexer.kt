package com.naulian.composer

private const val CPS_END_CHAR = Char.MIN_VALUE
private const val CPS_SYMBOL_CHARS = "\"</>&|#{%}[~]\\`(*)_\n"
private const val CPS_WHITESPACES = " \n"

class CPSLexer(input: String) {
    private var cursor = 0
    private val source = input

    private val Char.isNotSpaceChar get() = this != ' '
    private val Char.isNotEndChar get() = this != CPS_END_CHAR
    private val Char.isNotSymbols get() = this !in CPS_SYMBOL_CHARS

    private val charNotEndChar get() = char() != CPS_END_CHAR

    private fun char() = source.getOrElse(cursor) { Char.MIN_VALUE }

    fun tokenize(): List<CPSNode> {
        val tokens = mutableListOf<CPSNode>()
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

    fun next(): CPSNode {
        //println(char())
        return when (val char = char()) {
            in CPS_WHITESPACES -> createWhiteSpaceToken()
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
                CPSNode(IElementType.IGNORE, value.toString())
            }

            '%' -> createBlockToken(IElementType.DATETIME, char)
            '(' -> createLinkToken()
            '{' -> createCodeToken()
            '#' -> createHeaderToken()
            '*' -> createElementToken()
            '\\' -> createEscapedToken()
            in CPS_SYMBOL_CHARS -> createSymbolToken(IElementType.TEXT) //prevent memory leak
            Char.MIN_VALUE -> CPSNode.EOF
            else -> createTextToken()
        }
    }

    private fun createWhiteSpaceToken(): CPSNode {
        val start = cursor
        while (char().isWhitespace()) {
            advance()
        }
        val end = cursor
        val literal = source.subSequence(start, end)
        return when {
            literal.contains("\n") -> CPSNode.create(IElementType.NEWLINE, literal)
            else -> CPSNode.create(IElementType.WHITESPACE, literal)
        }
    }

    private fun createHeaderToken(): CPSNode {
        advance()
        if (char() == CPS_END_CHAR) {
            // early return
            return CPSNode(IElementType.TEXT, "#")
        }

        val start = cursor
        advanceWhile { it.isNotSpaceChar && it.isNotEndChar && it.isNotSymbols }
        return when (val value = source.subSequence(start, cursor)) {
            in "123456" -> CPSNode(IElementType.HEADER, "#$value")
            else -> CPSNode(IElementType.COLOR_HEX, "#$value")
        }
    }

    private fun createEscapedToken(): CPSNode {
        advance()
        if (char() == CPS_END_CHAR) {
            return CPSNode.EOF
        }
        val literal = char().toString()
        advance()
        return CPSNode(IElementType.ESCAPE, literal)
    }

    private fun createCodeToken(): CPSNode {
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
        return CPSNode(IElementType.CODE, code)
    }

    private fun createLinkToken(): CPSNode {
        advance() //skip opening parenthesis
        val start = cursor
        while (char() != ')' && charNotEndChar) {
            advance()
        }

        val value = source.subSequence(start, cursor).str()
        advance() //skip closing parenthesis

        if (!value.contains("http")) {
            return CPSNode(IElementType.TEXT, "($value)")
        }

        if (value.contains("@")) {
            val index = value.indexOf("@")
            val hyper = value.take(index)
            val link = value.str().replace("$hyper@", "")

            return when (hyper) {
                "img" -> CPSNode(IElementType.IMAGE, link)
                "ytb" -> CPSNode(IElementType.YOUTUBE, link)
                "vid" -> CPSNode(IElementType.VIDEO, link)
                else -> CPSNode(IElementType.HYPER_LINK, value)
            }
        }

        return CPSNode(IElementType.LINK, value)
    }

    private fun createElementToken(): CPSNode {
        advance()
        if (char() == CPS_END_CHAR) {
            // early return
            return CPSNode(IElementType.TEXT, "*")
        }

        val start = cursor
        advanceWhile { it.isNotSpaceChar && it.isNotEndChar && it.isNotSymbols }
        val value = source.subSequence(start, cursor)
        return CPSNode(IElementType.ELEMENT, "*$value")
    }

    private fun createTextToken(): CPSNode {
        val start = cursor
        advanceWhile { it.isNotSymbols && it.isNotEndChar }
        val literal = source.subSequence(start, cursor).toString()
        return CPSNode.create(IElementType.TEXT, literal)
    }

    private fun createSymbolToken(type: String): CPSNode {
        val value = char().toString()
        advance()
        return CPSNode(type, value)
    }

    private fun createBlockToken(type: String, char: Char): CPSNode {
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
                    CPSNode(type, value)
                } catch (e: Exception) {
                    CPSNode(IElementType.TEXT, blockValue)
                }
            }

            else -> CPSNode(type, blockValue)
        }
    }

    private fun advanceWhile(condition: (Char) -> Boolean) {
        while (condition(char())) advance()
    }
}