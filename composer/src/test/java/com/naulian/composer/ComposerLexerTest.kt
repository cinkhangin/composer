package com.naulian.composer

import org.junit.Assert.assertEquals
import org.junit.Test

class ComposerLexerTest {

    @Test
    fun plainTextTest() {
        val source = """
            Lorem ipsum dolor sit amet, consectetur adipiscing elit.
    
            Lorem ipsum dolor sit amet, consectetur adipiscing elit.
        """.trimIndent()
        val actual = ComposerLexer(source).tokenize()
        val expected = listOf(
            ComposerNode(
                type = IElementType.TEXT,
                literal = "Lorem ipsum dolor sit amet, consectetur adipiscing elit."
            ),
            ComposerNode(type = IElementType.NEWLINE, literal = "\n\n"),
            ComposerNode(
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
        val actual = ComposerLexer(source).tokenize()
        val expected = listOf(
            ComposerNode(type = IElementType.TEXT, literal = "this is "),
            ComposerNode(type = IElementType.BLOCK_SYMBOL, literal = "&"),
            ComposerNode(type = IElementType.TEXT, literal = "bold"),
            ComposerNode(type = IElementType.BLOCK_SYMBOL, literal = "&"),
            ComposerNode(type = IElementType.WHITESPACE, literal = " "),
            ComposerNode(type = IElementType.TEXT, literal = "text"),
            ComposerNode(type = IElementType.NEWLINE, literal = "\n"),

            ComposerNode(type = IElementType.TEXT, literal = "this is "),
            ComposerNode(type = IElementType.BLOCK_SYMBOL, literal = "/"),
            ComposerNode(type = IElementType.TEXT, literal = "italic"),
            ComposerNode(type = IElementType.BLOCK_SYMBOL, literal = "/"),
            ComposerNode(type = IElementType.WHITESPACE, literal = " "),
            ComposerNode(type = IElementType.TEXT, literal = "text"),
            ComposerNode(type = IElementType.NEWLINE, literal = "\n"),

            ComposerNode(type = IElementType.TEXT, literal = "this is "),
            ComposerNode(type = IElementType.BLOCK_SYMBOL, literal = "_"),
            ComposerNode(type = IElementType.TEXT, literal = "underline"),
            ComposerNode(type = IElementType.BLOCK_SYMBOL, literal = "_"),
            ComposerNode(type = IElementType.WHITESPACE, literal = " "),
            ComposerNode(type = IElementType.TEXT, literal = "text"),
            ComposerNode(type = IElementType.NEWLINE, literal = "\n"),

            ComposerNode(type = IElementType.TEXT, literal = "this is "),
            ComposerNode(type = IElementType.BLOCK_SYMBOL, literal = "~"),
            ComposerNode(type = IElementType.TEXT, literal = "strikethrough"),
            ComposerNode(type = IElementType.BLOCK_SYMBOL, literal = "~"),
            ComposerNode(type = IElementType.WHITESPACE, literal = " "),
            ComposerNode(type = IElementType.TEXT, literal = "text")
        )
        assertEquals(expected, actual)
    }

    @Test
    fun dividerTest() {
        val source = """
            =line=
        """.trimIndent()
        val actual = ComposerLexer(source).tokenize()
        val expected = listOf(
            ComposerNode(type = IElementType.DIVIDER, literal = "line")
        )
        assertEquals(expected, actual)
    }

    @Test
    fun ignoreTest() {
        val source = """
            `ignore ~syntax~ here`
        """.trimIndent()
        val actual = ComposerLexer(source).tokenize()
        val expected = listOf(
            ComposerNode(type = IElementType.IGNORE, literal = "ignore ~syntax~ here")
        )
        assertEquals(expected, actual)
    }

    @Test
    fun colorTest() {
        val source = """
            <color this text#FF0000>
        """.trimIndent()
        val actual = ComposerLexer(source).tokenize()
        val expected = listOf(
            ComposerNode(type = IElementType.COLOR_START, literal = "<"),
            ComposerNode(type = IElementType.TEXT, literal = "color this text"),
            ComposerNode(type = IElementType.COLOR_HEX, literal = "#FF0000"),
            ComposerNode(type = IElementType.COLOR_END, literal = ">")
        )
        assertEquals(expected, actual)
    }

    @Test
    fun linkTest() {
        val source = """
            (https://www.google.com)
            Search (here@http://www.google.com)
        """.trimIndent()
        val actual = ComposerLexer(source).tokenize()
        val expected = listOf(
            ComposerNode(type = IElementType.LINK, literal = "https://www.google.com"),
            ComposerNode(type = IElementType.NEWLINE, literal = "\n"),
            ComposerNode(type = IElementType.TEXT, literal = "Search "),
            ComposerNode(type = IElementType.HYPER_LINK, literal = "here@http://www.google.com")
        )
        assertEquals(expected, actual)
    }

    @Test
    fun imageAndYoutubeTest() {
        val source = """
            (img@https://picsum.photos/id/67/300/200)
            (ytb@https://www.youtube.com/watch?v=dQw4w9WgXcQ)
        """.trimIndent()
        val actual = ComposerLexer(source).tokenize()
        val expected = listOf(
            ComposerNode(type = IElementType.IMAGE, literal = "https://picsum.photos/id/67/300/200"),
            ComposerNode(type = IElementType.NEWLINE, literal = "\n"),
            ComposerNode(
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
        val actual = ComposerLexer(source).tokenize()
        val expected = listOf(
            ComposerNode(type = IElementType.ESCAPE, literal = "\""),
            ComposerNode(type = IElementType.TEXT, literal = "this should not show as quote"),
            ComposerNode(type = IElementType.ESCAPE, literal = "\"")
        )
        assertEquals(expected, actual)
    }

    @Test
    fun headingTest() {
        val source = """
            #1 heading 1
            #2 heading 2
        """.trimIndent()
        val actual = ComposerLexer(source).tokenize()
        val expected = listOf(
            ComposerNode(type = IElementType.HEADER, literal = "#1"),
            ComposerNode(type = IElementType.WHITESPACE, literal = " "),
            ComposerNode(type = IElementType.TEXT, literal = "heading 1"),
            ComposerNode(type = IElementType.NEWLINE, literal = "\n"),
            ComposerNode(type = IElementType.HEADER, literal = "#2"),
            ComposerNode(type = IElementType.WHITESPACE, literal = " "),
            ComposerNode(type = IElementType.TEXT, literal = "heading 2")
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
        val actual = ComposerLexer(source).tokenize()
        val expected = listOf(
            ComposerNode(
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
        val actual = ComposerLexer(source).tokenize()
        val expected = listOf(
            ComposerNode(type = IElementType.BLOCK_SYMBOL, literal = "\""),
            ComposerNode(
                type = IElementType.TEXT,
                literal = "this is quote text -author".trimIndent()
            ),
            ComposerNode(type = IElementType.BLOCK_SYMBOL, literal = "\"")
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
        val actual = ComposerLexer(source).tokenize()
        val expected = listOf(
            ComposerNode(type = IElementType.ELEMENT, literal = "*"),
            ComposerNode(type = IElementType.WHITESPACE, literal = " "),
            ComposerNode(type = IElementType.TEXT, literal = "unordered item"),
            ComposerNode(type = IElementType.NEWLINE, literal = "\n"),
            ComposerNode(type = IElementType.ELEMENT, literal = "*o"),
            ComposerNode(type = IElementType.WHITESPACE, literal = " "),
            ComposerNode(type = IElementType.TEXT, literal = "unchecked item"),
            ComposerNode(type = IElementType.NEWLINE, literal = "\n"),
            ComposerNode(type = IElementType.ELEMENT, literal = "*x"),
            ComposerNode(type = IElementType.WHITESPACE, literal = " "),
            ComposerNode(type = IElementType.TEXT, literal = "checked item"),
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
        val actual = ComposerLexer(source).tokenize()
        val expected = listOf(
            ComposerNode(type = IElementType.TABLE_START, literal = "["),
            ComposerNode(type = IElementType.NEWLINE, literal = "\n"),
            ComposerNode(type = IElementType.TEXT, literal = "a    "),
            ComposerNode(type = IElementType.PIPE, literal = "|"),
            ComposerNode(type = IElementType.TEXT, literal = "b    "),
            ComposerNode(type = IElementType.PIPE, literal = "|"),
            ComposerNode(type = IElementType.TEXT, literal = "result"),
            ComposerNode(type = IElementType.NEWLINE, literal = "\n"),
            ComposerNode(type = IElementType.TEXT, literal = "true "),
            ComposerNode(type = IElementType.PIPE, literal = "|"),
            ComposerNode(type = IElementType.TEXT, literal = "true "),
            ComposerNode(type = IElementType.PIPE, literal = "|"),
            ComposerNode(type = IElementType.TEXT, literal = "true"),
            ComposerNode(type = IElementType.NEWLINE, literal = "\n"),
            ComposerNode(type = IElementType.TABLE_END, literal = "]"),
        )
        assertEquals(expected, actual)
    }
}