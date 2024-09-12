package com.naulian.composer

import org.junit.Assert.assertEquals
import org.junit.Test

class LexerTest {

    @Test
    fun plainTextTest() {
        val source = """
            Lorem ipsum dolor sit amet, consectetur adipiscing elit.
    
            Lorem ipsum dolor sit amet, consectetur adipiscing elit.
        """.trimIndent()
        val actual = Lexer(source).tokenize()
        val expected = listOf(
            Node(
                type = IElementType.TEXT,
                literal = "Lorem ipsum dolor sit amet, consectetur adipiscing elit."
            ),
            Node(type = IElementType.NEWLINE, literal = "\n\n"),
            Node(
                type = IElementType.TEXT,
                literal = "Lorem ipsum dolor sit amet, consectetur adipiscing elit."
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun styledTextTest() {
        val source = """
            this is &bold& text
            this is /italic/ text
            this is _underline_ text
            this is ~strikethrough~ text
        """.trimIndent()
        val actual = Lexer(source).tokenize()
        val expected = listOf(
            Node(type = IElementType.TEXT, literal = "this is "),
            Node(type = IElementType.BLOCK_SYMBOL, literal = "&"),
            Node(type = IElementType.TEXT, literal = "bold"),
            Node(type = IElementType.BLOCK_SYMBOL, literal = "&"),
            Node(type = IElementType.WHITESPACE, literal = " "),
            Node(type = IElementType.TEXT, literal = "text"),
            Node(type = IElementType.NEWLINE, literal = "\n"),

            Node(type = IElementType.TEXT, literal = "this is "),
            Node(type = IElementType.BLOCK_SYMBOL, literal = "/"),
            Node(type = IElementType.TEXT, literal = "italic"),
            Node(type = IElementType.BLOCK_SYMBOL, literal = "/"),
            Node(type = IElementType.WHITESPACE, literal = " "),
            Node(type = IElementType.TEXT, literal = "text"),
            Node(type = IElementType.NEWLINE, literal = "\n"),

            Node(type = IElementType.TEXT, literal = "this is "),
            Node(type = IElementType.BLOCK_SYMBOL, literal = "_"),
            Node(type = IElementType.TEXT, literal = "underline"),
            Node(type = IElementType.BLOCK_SYMBOL, literal = "_"),
            Node(type = IElementType.WHITESPACE, literal = " "),
            Node(type = IElementType.TEXT, literal = "text"),
            Node(type = IElementType.NEWLINE, literal = "\n"),

            Node(type = IElementType.TEXT, literal = "this is "),
            Node(type = IElementType.BLOCK_SYMBOL, literal = "~"),
            Node(type = IElementType.TEXT, literal = "strikethrough"),
            Node(type = IElementType.BLOCK_SYMBOL, literal = "~"),
            Node(type = IElementType.WHITESPACE, literal = " "),
            Node(type = IElementType.TEXT, literal = "text")
        )
        assertEquals(expected, actual)
    }

    @Test
    fun dividerTest() {
        val source = """
            =line=
        """.trimIndent()
        val actual = Lexer(source).tokenize()
        val expected = listOf(
            Node(type = IElementType.DIVIDER, literal = "line")
        )
        assertEquals(expected, actual)
    }

    @Test
    fun ignoreTest() {
        val source = """
            `ignore ~syntax~ here`
        """.trimIndent()
        val actual = Lexer(source).tokenize()
        val expected = listOf(
            Node(type = IElementType.IGNORE, literal = "ignore ~syntax~ here")
        )
        assertEquals(expected, actual)
    }

    @Test
    fun colorTest() {
        val source = """
            <color this text#FF0000>
        """.trimIndent()
        val actual = Lexer(source).tokenize()
        val expected = listOf(
            Node(type = IElementType.COLOR_START, literal = "<"),
            Node(type = IElementType.TEXT, literal = "color this text"),
            Node(type = IElementType.COLOR_HEX, literal = "#FF0000"),
            Node(type = IElementType.COLOR_END, literal = ">")
        )
        assertEquals(expected, actual)
    }

    @Test
    fun linkTest() {
        val source = """
            (https://www.google.com)
            Search (here@http://www.google.com)
        """.trimIndent()
        val actual = Lexer(source).tokenize()
        val expected = listOf(
            Node(type = IElementType.LINK, literal = "https://www.google.com"),
            Node(type = IElementType.NEWLINE, literal = "\n"),
            Node(type = IElementType.TEXT, literal = "Search "),
            Node(type = IElementType.HYPER_LINK, literal = "here@http://www.google.com")
        )
        assertEquals(expected, actual)
    }

    @Test
    fun imageAndYoutubeTest() {
        val source = """
            (img@https://picsum.photos/id/67/300/200)
            (ytb@https://www.youtube.com/watch?v=dQw4w9WgXcQ)
        """.trimIndent()
        val actual = Lexer(source).tokenize()
        val expected = listOf(
            Node(type = IElementType.IMAGE, literal = "https://picsum.photos/id/67/300/200"),
            Node(type = IElementType.NEWLINE, literal = "\n"),
            Node(
                type = IElementType.YOUTUBE,
                literal = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun escapedTest() {
        val source = """
            \"this should not show as quote\"
        """.trimIndent()
        val actual = Lexer(source).tokenize()
        val expected = listOf(
            Node(type = IElementType.ESCAPE, literal = "\""),
            Node(type = IElementType.TEXT, literal = "this should not show as quote"),
            Node(type = IElementType.ESCAPE, literal = "\"")
        )
        assertEquals(expected, actual)
    }

    @Test
    fun headingTest() {
        val source = """
            #1 heading 1
            #2 heading 2
        """.trimIndent()
        val actual = Lexer(source).tokenize()
        val expected = listOf(
            Node(type = IElementType.HEADER, literal = "#1"),
            Node(type = IElementType.WHITESPACE, literal = " "),
            Node(type = IElementType.TEXT, literal = "heading 1"),
            Node(type = IElementType.NEWLINE, literal = "\n"),
            Node(type = IElementType.HEADER, literal = "#2"),
            Node(type = IElementType.WHITESPACE, literal = " "),
            Node(type = IElementType.TEXT, literal = "heading 2")
        )
        assertEquals(expected, actual)
    }

    @Test
    fun codeBlockTest() {
        val source = """
            {
            .py
            def main():
                print("Hello World!")
                
            if __name__ == '__main__':
                main()
            }
        """.trimIndent()
        val actual = Lexer(source).tokenize()
        val expected = listOf(
            Node(
                type = IElementType.CODE,
                literal = """
                    .py
                    def main():
                        print("Hello World!")
                        
                    if __name__ == '__main__':
                        main()
                """.trimIndent()
            ),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun quoteBlockTest() {
        val source = """
            "this is quote text -author"
        """.trimIndent()
        val actual = Lexer(source).tokenize()
        val expected = listOf(
            Node(type = IElementType.BLOCK_SYMBOL, literal = "\""),
            Node(
                type = IElementType.TEXT,
                literal = "this is quote text -author".trimIndent()
            ),
            Node(type = IElementType.BLOCK_SYMBOL, literal = "\"")
        )
        assertEquals(expected, actual)
    }

    @Test
    fun listTest() {
        val source = """
            * unordered item
            *o unchecked item
            *x checked item
        """.trimIndent()
        val actual = Lexer(source).tokenize()
        val expected = listOf(
            Node(type = IElementType.ELEMENT, literal = "*"),
            Node(type = IElementType.WHITESPACE, literal = " "),
            Node(type = IElementType.TEXT, literal = "unordered item"),
            Node(type = IElementType.NEWLINE, literal = "\n"),
            Node(type = IElementType.ELEMENT, literal = "*o"),
            Node(type = IElementType.WHITESPACE, literal = " "),
            Node(type = IElementType.TEXT, literal = "unchecked item"),
            Node(type = IElementType.NEWLINE, literal = "\n"),
            Node(type = IElementType.ELEMENT, literal = "*x"),
            Node(type = IElementType.WHITESPACE, literal = " "),
            Node(type = IElementType.TEXT, literal = "checked item"),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun tableTest() {
        val source = """
            [
            a    |b    |result
            true |true |true
            ]
        """.trimIndent()
        val actual = Lexer(source).tokenize()
        val expected = listOf(
            Node(type = IElementType.TABLE_START, literal = "["),
            Node(type = IElementType.NEWLINE, literal = "\n"),
            Node(type = IElementType.TEXT, literal = "a    "),
            Node(type = IElementType.PIPE, literal = "|"),
            Node(type = IElementType.TEXT, literal = "b    "),
            Node(type = IElementType.PIPE, literal = "|"),
            Node(type = IElementType.TEXT, literal = "result"),
            Node(type = IElementType.NEWLINE, literal = "\n"),
            Node(type = IElementType.TEXT, literal = "true "),
            Node(type = IElementType.PIPE, literal = "|"),
            Node(type = IElementType.TEXT, literal = "true "),
            Node(type = IElementType.PIPE, literal = "|"),
            Node(type = IElementType.TEXT, literal = "true"),
            Node(type = IElementType.NEWLINE, literal = "\n"),
            Node(type = IElementType.TABLE_END, literal = "]"),
        )
        assertEquals(expected, actual)
    }
}