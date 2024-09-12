package com.naulian.composer

import org.junit.Assert.assertEquals
import org.junit.Test

class CPSLexerTest {

    @Test
    fun plainTextTest() {
        val source = """
            Lorem ipsum dolor sit amet, consectetur adipiscing elit.
    
            Lorem ipsum dolor sit amet, consectetur adipiscing elit.
        """.trimIndent()
        val actual = CPSLexer(source).tokenize()
        val expected = listOf(
            CPSNode(
                type = IElementType.TEXT,
                literal = "Lorem ipsum dolor sit amet, consectetur adipiscing elit."
            ),
            CPSNode(type = IElementType.NEWLINE, literal = "\n\n"),
            CPSNode(
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
        val actual = CPSLexer(source).tokenize()
        val expected = listOf(
            CPSNode(type = IElementType.TEXT, literal = "this is "),
            CPSNode(type = IElementType.BLOCK_SYMBOL, literal = "&"),
            CPSNode(type = IElementType.TEXT, literal = "bold"),
            CPSNode(type = IElementType.BLOCK_SYMBOL, literal = "&"),
            CPSNode(type = IElementType.WHITESPACE, literal = " "),
            CPSNode(type = IElementType.TEXT, literal = "text"),
            CPSNode(type = IElementType.NEWLINE, literal = "\n"),

            CPSNode(type = IElementType.TEXT, literal = "this is "),
            CPSNode(type = IElementType.BLOCK_SYMBOL, literal = "/"),
            CPSNode(type = IElementType.TEXT, literal = "italic"),
            CPSNode(type = IElementType.BLOCK_SYMBOL, literal = "/"),
            CPSNode(type = IElementType.WHITESPACE, literal = " "),
            CPSNode(type = IElementType.TEXT, literal = "text"),
            CPSNode(type = IElementType.NEWLINE, literal = "\n"),

            CPSNode(type = IElementType.TEXT, literal = "this is "),
            CPSNode(type = IElementType.BLOCK_SYMBOL, literal = "_"),
            CPSNode(type = IElementType.TEXT, literal = "underline"),
            CPSNode(type = IElementType.BLOCK_SYMBOL, literal = "_"),
            CPSNode(type = IElementType.WHITESPACE, literal = " "),
            CPSNode(type = IElementType.TEXT, literal = "text"),
            CPSNode(type = IElementType.NEWLINE, literal = "\n"),

            CPSNode(type = IElementType.TEXT, literal = "this is "),
            CPSNode(type = IElementType.BLOCK_SYMBOL, literal = "~"),
            CPSNode(type = IElementType.TEXT, literal = "strikethrough"),
            CPSNode(type = IElementType.BLOCK_SYMBOL, literal = "~"),
            CPSNode(type = IElementType.WHITESPACE, literal = " "),
            CPSNode(type = IElementType.TEXT, literal = "text")
        )
        assertEquals(expected, actual)
    }

    @Test
    fun dividerTest() {
        val source = """
            =line=
        """.trimIndent()
        val actual = CPSLexer(source).tokenize()
        val expected = listOf(
            CPSNode(type = IElementType.DIVIDER, literal = "line")
        )
        assertEquals(expected, actual)
    }

    @Test
    fun ignoreTest() {
        val source = """
            `ignore ~syntax~ here`
        """.trimIndent()
        val actual = CPSLexer(source).tokenize()
        val expected = listOf(
            CPSNode(type = IElementType.IGNORE, literal = "ignore ~syntax~ here")
        )
        assertEquals(expected, actual)
    }

    @Test
    fun colorTest() {
        val source = """
            <color this text#FF0000>
        """.trimIndent()
        val actual = CPSLexer(source).tokenize()
        val expected = listOf(
            CPSNode(type = IElementType.COLOR_START, literal = "<"),
            CPSNode(type = IElementType.TEXT, literal = "color this text"),
            CPSNode(type = IElementType.COLOR_HEX, literal = "#FF0000"),
            CPSNode(type = IElementType.COLOR_END, literal = ">")
        )
        assertEquals(expected, actual)
    }

    @Test
    fun linkTest() {
        val source = """
            (https://www.google.com)
            Search (here@http://www.google.com)
        """.trimIndent()
        val actual = CPSLexer(source).tokenize()
        val expected = listOf(
            CPSNode(type = IElementType.LINK, literal = "https://www.google.com"),
            CPSNode(type = IElementType.NEWLINE, literal = "\n"),
            CPSNode(type = IElementType.TEXT, literal = "Search "),
            CPSNode(type = IElementType.HYPER_LINK, literal = "here@http://www.google.com")
        )
        assertEquals(expected, actual)
    }

    @Test
    fun imageAndYoutubeTest() {
        val source = """
            (img@https://picsum.photos/id/67/300/200)
            (ytb@https://www.youtube.com/watch?v=dQw4w9WgXcQ)
        """.trimIndent()
        val actual = CPSLexer(source).tokenize()
        val expected = listOf(
            CPSNode(type = IElementType.IMAGE, literal = "https://picsum.photos/id/67/300/200"),
            CPSNode(type = IElementType.NEWLINE, literal = "\n"),
            CPSNode(
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
        val actual = CPSLexer(source).tokenize()
        val expected = listOf(
            CPSNode(type = IElementType.ESCAPE, literal = "\""),
            CPSNode(type = IElementType.TEXT, literal = "this should not show as quote"),
            CPSNode(type = IElementType.ESCAPE, literal = "\"")
        )
        assertEquals(expected, actual)
    }

    @Test
    fun headingTest() {
        val source = """
            #1 heading 1
            #2 heading 2
        """.trimIndent()
        val actual = CPSLexer(source).tokenize()
        val expected = listOf(
            CPSNode(type = IElementType.HEADER, literal = "#1"),
            CPSNode(type = IElementType.WHITESPACE, literal = " "),
            CPSNode(type = IElementType.TEXT, literal = "heading 1"),
            CPSNode(type = IElementType.NEWLINE, literal = "\n"),
            CPSNode(type = IElementType.HEADER, literal = "#2"),
            CPSNode(type = IElementType.WHITESPACE, literal = " "),
            CPSNode(type = IElementType.TEXT, literal = "heading 2")
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
        val actual = CPSLexer(source).tokenize()
        val expected = listOf(
            CPSNode(
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
        val actual = CPSLexer(source).tokenize()
        val expected = listOf(
            CPSNode(type = IElementType.BLOCK_SYMBOL, literal = "\""),
            CPSNode(
                type = IElementType.TEXT,
                literal = "this is quote text -author".trimIndent()
            ),
            CPSNode(type = IElementType.BLOCK_SYMBOL, literal = "\"")
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
        val actual = CPSLexer(source).tokenize()
        val expected = listOf(
            CPSNode(type = IElementType.ELEMENT, literal = "*"),
            CPSNode(type = IElementType.WHITESPACE, literal = " "),
            CPSNode(type = IElementType.TEXT, literal = "unordered item"),
            CPSNode(type = IElementType.NEWLINE, literal = "\n"),
            CPSNode(type = IElementType.ELEMENT, literal = "*o"),
            CPSNode(type = IElementType.WHITESPACE, literal = " "),
            CPSNode(type = IElementType.TEXT, literal = "unchecked item"),
            CPSNode(type = IElementType.NEWLINE, literal = "\n"),
            CPSNode(type = IElementType.ELEMENT, literal = "*x"),
            CPSNode(type = IElementType.WHITESPACE, literal = " "),
            CPSNode(type = IElementType.TEXT, literal = "checked item"),
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
        val actual = CPSLexer(source).tokenize()
        val expected = listOf(
            CPSNode(type = IElementType.TABLE_START, literal = "["),
            CPSNode(type = IElementType.NEWLINE, literal = "\n"),
            CPSNode(type = IElementType.TEXT, literal = "a    "),
            CPSNode(type = IElementType.PIPE, literal = "|"),
            CPSNode(type = IElementType.TEXT, literal = "b    "),
            CPSNode(type = IElementType.PIPE, literal = "|"),
            CPSNode(type = IElementType.TEXT, literal = "result"),
            CPSNode(type = IElementType.NEWLINE, literal = "\n"),
            CPSNode(type = IElementType.TEXT, literal = "true "),
            CPSNode(type = IElementType.PIPE, literal = "|"),
            CPSNode(type = IElementType.TEXT, literal = "true "),
            CPSNode(type = IElementType.PIPE, literal = "|"),
            CPSNode(type = IElementType.TEXT, literal = "true"),
            CPSNode(type = IElementType.NEWLINE, literal = "\n"),
            CPSNode(type = IElementType.TABLE_END, literal = "]"),
        )
        assertEquals(expected, actual)
    }
}