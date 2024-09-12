package com.naulian.composer

private const val MDX_END_CHAR = Char.MIN_VALUE
private const val MDX_SYMBOL_CHARS = "\"</>&|#{%}[~]\\`(*)_\n"
private const val MDX_WHITESPACES = " \n"

class Lexer(input: String) {
    private var cursor = 0
    private val source = input

    private val Char.isNotSpaceChar get() = this != ' '
    private val Char.isNotEndChar get() = this != MDX_END_CHAR
    private val Char.isNotSymbols get() = this !in MDX_SYMBOL_CHARS

    private val charNotEndChar get() = char() != MDX_END_CHAR

    private fun char() = source.getOrElse(cursor) { Char.MIN_VALUE }

    fun tokenize(): List<Node> {
        val tokens = mutableListOf<Node>()
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

    fun next(): Node {
        //println(char())
        return when (val char = char()) {
            in MDX_WHITESPACES -> createWhiteSpaceToken()
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
                Node(IElementType.IGNORE, value.toString())
            }

            '%' -> createBlockToken(IElementType.DATETIME, char)
            '(' -> createLinkToken()
            '{' -> createCodeToken()
            '#' -> createHeaderToken()
            '*' -> createElementToken()
            '\\' -> createEscapedToken()
            in MDX_SYMBOL_CHARS -> createSymbolToken(IElementType.TEXT) //prevent memory leak
            Char.MIN_VALUE -> Node.EOF
            else -> createTextToken()
        }
    }

    private fun createWhiteSpaceToken(): Node {
        val start = cursor
        while (char().isWhitespace()) {
            advance()
        }
        val end = cursor
        val literal = source.subSequence(start, end)
        return when {
            literal.contains("\n") -> Node.create(IElementType.NEWLINE, literal)
            else -> Node.create(IElementType.WHITESPACE, literal)
        }
    }

    private fun createHeaderToken(): Node {
        advance()
        if (char() == MDX_END_CHAR) {
            // early return
            return Node(IElementType.TEXT, "#")
        }

        val start = cursor
        advanceWhile { it.isNotSpaceChar && it.isNotEndChar && it.isNotSymbols }
        return when (val value = source.subSequence(start, cursor)) {
            in "123456" -> Node(IElementType.HEADER, "#$value")
            else -> Node(IElementType.COLOR_HEX, "#$value")
        }
    }

    private fun createEscapedToken(): Node {
        advance()
        if (char() == MDX_END_CHAR) {
            return Node.EOF
        }
        val literal = char().toString()
        advance()
        return Node(IElementType.ESCAPE, literal)
    }

    private fun createCodeToken(): Node {
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
        return Node(IElementType.CODE, code)
    }

    private fun createLinkToken(): Node {
        advance() //skip opening parenthesis
        val start = cursor
        while (char() != ')' && charNotEndChar) {
            advance()
        }

        val value = source.subSequence(start, cursor).str()
        advance() //skip closing parenthesis

        if (!value.contains("http")) {
            return Node(IElementType.TEXT, "($value)")
        }

        if (value.contains("@")) {
            val index = value.indexOf("@")
            val hyper = value.take(index)
            val link = value.str().replace("$hyper@", "")

            return when (hyper) {
                "img" -> Node(IElementType.IMAGE, link)
                "ytb" -> Node(IElementType.YOUTUBE, link)
                "vid" -> Node(IElementType.VIDEO, link)
                else -> Node(IElementType.HYPER_LINK, value)
            }
        }

        return Node(IElementType.LINK, value)
    }

    private fun createElementToken(): Node {
        advance()
        if (char() == MDX_END_CHAR) {
            // early return
            return Node(IElementType.TEXT, "*")
        }

        val start = cursor
        advanceWhile { it.isNotSpaceChar && it.isNotEndChar && it.isNotSymbols }
        val value = source.subSequence(start, cursor)
        return Node(IElementType.ELEMENT, "*$value")
    }

    private fun createTextToken(): Node {
        val start = cursor
        advanceWhile { it.isNotSymbols && it.isNotEndChar }
        val literal = source.subSequence(start, cursor).toString()
        return Node.create(IElementType.TEXT, literal)
    }

    private fun createSymbolToken(type: String): Node {
        val value = char().toString()
        advance()
        return Node(type, value)
    }

    private fun createBlockToken(type: String, char: Char): Node {
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
                    Node(type, value)
                } catch (e: Exception) {
                    Node(IElementType.TEXT, blockValue)
                }
            }

            else -> Node(type, blockValue)
        }
    }

    private fun advanceWhile(condition: (Char) -> Boolean) {
        while (condition(char())) advance()
    }
}