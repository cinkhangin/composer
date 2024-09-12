package com.naulian.composer

import org.junit.Assert.assertEquals
import org.junit.Test

class ComposerParserTest {

    @Test
    fun plainTextTest() {
        val source = "plain text"
        val actual = ComposerParser(source).parse().children
        val expected = listOf(
            ComposerNode(
                type = IElementType.PARAGRAPH,
                children = listOf(
                    ComposerNode(type = IElementType.TEXT, literal = "plain text")
                )
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun headerTest() {
        val source = "#1 header"
        val actual = ComposerParser(source).parse().children
        val expected = listOf(
            ComposerNode(
                type = IElementType.H1,
                literal = "",
                children = listOf(
                    ComposerNode(
                        type = IElementType.PARAGRAPH,
                        children = listOf(
                            ComposerNode(type = IElementType.TEXT, literal = "header")
                        )
                    )
                )
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun header2Test() {
        val source = """
            #1 header 1
            #2 header 2
        """.trimIndent()
        val actual = ComposerParser(source).parse().children
        val expected = listOf(
            ComposerNode(
                type = IElementType.H1,
                literal = "",
                children = listOf(
                    ComposerNode(
                        type = IElementType.PARAGRAPH,
                        children = listOf(
                            ComposerNode(type = IElementType.TEXT, literal = "header 1")
                        )
                    )
                )
            ),
            ComposerNode(
                type = IElementType.H2,
                literal = "",
                children = listOf(
                    ComposerNode(
                        type = IElementType.PARAGRAPH,
                        children = listOf(
                            ComposerNode(type = IElementType.TEXT, literal = "header 2")
                        )
                    )
                )
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun headerWithStyleTest() {
        val source = "#1 ~header underline~"
        val actual = ComposerParser(source).parse().children
        val expected = listOf(
            ComposerNode(
                type = IElementType.H1,
                literal = "",
                children = listOf(
                    ComposerNode(
                        type = IElementType.PARAGRAPH, children = listOf(
                            ComposerNode(
                                type = IElementType.STRIKE,
                                children = listOf(
                                    ComposerNode(
                                        type = IElementType.TEXT,
                                        literal = "header underline"
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun quotationTest() {
        val source = """
            "this is a quote text &-author&"
        """.trimIndent()
        val actual = ComposerParser(source).parse().children
        val expected = listOf(
            ComposerNode(
                type = IElementType.QUOTATION,
                literal = "",
                children = listOf(
                    ComposerNode(
                        type = IElementType.PARAGRAPH, children = listOf(
                            ComposerNode(type = IElementType.TEXT, literal = "this is a quote text "),
                            ComposerNode(
                                type = IElementType.BOLD,
                                children = listOf(
                                    ComposerNode(type = IElementType.TEXT, literal = "-author")
                                )
                            )
                        )
                    )
                )
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun codeTest() {
        val source = """
            {
            .py
            def main():
                print("Hello World!")
                
            if __name__ == '__main__':
                main()
            }
        """.trimIndent()
        val actual = ComposerParser(source).parse().children
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
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun dividerTest() {
        val source = """
            =line=
        """.trimIndent()
        val actual = ComposerParser(source).parse().children
        val expected = listOf(
            ComposerNode(
                type = IElementType.DIVIDER,
                literal = "line"
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun tableTest() {
        val source = """
            [
            a    |b    |c
            true |false|true
            ]
        """.trimIndent()
        val actual = ComposerParser(source).parse().children
        val expected = listOf(
            ComposerNode(
                type = IElementType.TABLE,
                children = listOf(
                    ComposerNode(
                        type = IElementType.TABLE_COLOMN,
                        children = listOf(
                            ComposerNode(
                                type = IElementType.PARAGRAPH,
                                children = listOf(
                                    ComposerNode(type = IElementType.TEXT, literal = "a    ")
                                )
                            ),
                            ComposerNode(type = IElementType.PIPE, literal = "|"),
                            ComposerNode(
                                type = IElementType.PARAGRAPH,
                                children = listOf(
                                    ComposerNode(type = IElementType.TEXT, literal = "b    ")
                                )
                            ),
                            ComposerNode(type = IElementType.PIPE, literal = "|"),
                            ComposerNode(
                                type = IElementType.PARAGRAPH,
                                children = listOf(
                                    ComposerNode(type = IElementType.TEXT, literal = "c"),
                                )
                            )
                        )
                    ),
                    ComposerNode(
                        type = IElementType.TABLE_COLOMN,
                        children = listOf(
                            ComposerNode(
                                type = IElementType.PARAGRAPH,
                                children = listOf(
                                    ComposerNode(type = IElementType.TEXT, literal = "true "),
                                )
                            ),
                            ComposerNode(type = IElementType.PIPE, literal = "|"),
                            ComposerNode(
                                type = IElementType.PARAGRAPH,
                                children = listOf(
                                    ComposerNode(type = IElementType.TEXT, literal = "false"),
                                )
                            ),
                            ComposerNode(type = IElementType.PIPE, literal = "|"),
                            ComposerNode(
                                type = IElementType.PARAGRAPH,
                                children = listOf(
                                    ComposerNode(type = IElementType.TEXT, literal = "true"),
                                )
                            )
                        )
                    )
                )
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun coloredTest() {
        val source = """
            <color this text#FF0000>
        """.trimIndent()
        val actual = ComposerParser(source).parse().children
        val expected = listOf(
            ComposerNode(
                type = IElementType.PARAGRAPH,
                children = listOf(
                    ComposerNode(
                        type = IElementType.COLORED,
                        literal = "#FF0000",
                        children = listOf(
                            ComposerNode(type = IElementType.TEXT, literal = "color this text"),
                        )
                    )
                )
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun ignoreTest() {
        val source = """
            `the syntax ~should~ be ignore here`
        """.trimIndent()
        val actual = ComposerParser(source).parse().children
        val expected = listOf(
            ComposerNode(
                type = IElementType.PARAGRAPH,
                children = listOf(
                    ComposerNode(
                        type = IElementType.IGNORE,
                        literal = "the syntax ~should~ be ignore here",
                    )
                )
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun elementTest() {
        val source = """
            * unordered item
            
            *o unchecked item
            *x checked item
        """.trimIndent()
        val actual = ComposerParser(source).parse().children
        val expected = listOf(
            ComposerNode(
                type = IElementType.ELEMENT,
                children = listOf(
                    ComposerNode(
                        type = IElementType.ELEMENT_BULLET,
                        children = listOf(
                            ComposerNode(
                                type = IElementType.PARAGRAPH,
                                children = listOf(
                                    ComposerNode(type = IElementType.TEXT, literal = "unordered item"),
                                )
                            )
                        )
                    ),
                    ComposerNode(
                        type = IElementType.ELEMENT_UNCHECKED,
                        children = listOf(
                            ComposerNode(
                                type = IElementType.PARAGRAPH,
                                children = listOf(
                                    ComposerNode(type = IElementType.TEXT, literal = "unchecked item"),
                                )
                            )
                        )
                    ),
                    ComposerNode(
                        type = IElementType.ELEMENT_CHECKED,
                        children = listOf(
                            ComposerNode(
                                type = IElementType.PARAGRAPH,
                                children = listOf(
                                    ComposerNode(type = IElementType.TEXT, literal = "checked item"),
                                )
                            )
                        )
                    )
                )
            )
        )
        assertEquals(expected, actual)
    }
}