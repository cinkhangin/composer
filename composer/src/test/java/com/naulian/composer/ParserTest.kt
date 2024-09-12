package com.naulian.composer

import org.junit.Assert.assertEquals
import org.junit.Test

class ParserTest {

    @Test
    fun plainTextTest() {
        val source = "plain text"
        val actual = Parser(source).parse().children
        val expected = listOf(
            CPSNode(
                type = IElementType.PARAGRAPH,
                children = listOf(
                    CPSNode(type = IElementType.TEXT, literal = "plain text")
                )
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun headerTest() {
        val source = "#1 header"
        val actual = Parser(source).parse().children
        val expected = listOf(
            CPSNode(
                type = IElementType.H1,
                literal = "",
                children = listOf(
                    CPSNode(
                        type = IElementType.PARAGRAPH,
                        children = listOf(
                            CPSNode(type = IElementType.TEXT, literal = "header")
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
        val actual = Parser(source).parse().children
        val expected = listOf(
            CPSNode(
                type = IElementType.H1,
                literal = "",
                children = listOf(
                    CPSNode(
                        type = IElementType.PARAGRAPH,
                        children = listOf(
                            CPSNode(type = IElementType.TEXT, literal = "header 1")
                        )
                    )
                )
            ),
            CPSNode(
                type = IElementType.H2,
                literal = "",
                children = listOf(
                    CPSNode(
                        type = IElementType.PARAGRAPH,
                        children = listOf(
                            CPSNode(type = IElementType.TEXT, literal = "header 2")
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
        val actual = Parser(source).parse().children
        val expected = listOf(
            CPSNode(
                type = IElementType.H1,
                literal = "",
                children = listOf(
                    CPSNode(
                        type = IElementType.PARAGRAPH, children = listOf(
                            CPSNode(
                                type = IElementType.STRIKE,
                                children = listOf(
                                    CPSNode(
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
        val actual = Parser(source).parse().children
        val expected = listOf(
            CPSNode(
                type = IElementType.QUOTATION,
                literal = "",
                children = listOf(
                    CPSNode(
                        type = IElementType.PARAGRAPH, children = listOf(
                            CPSNode(type = IElementType.TEXT, literal = "this is a quote text "),
                            CPSNode(
                                type = IElementType.BOLD,
                                children = listOf(
                                    CPSNode(type = IElementType.TEXT, literal = "-author")
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
        val actual = Parser(source).parse().children
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
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun dividerTest() {
        val source = """
            =line=
        """.trimIndent()
        val actual = Parser(source).parse().children
        val expected = listOf(
            CPSNode(
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
        val actual = Parser(source).parse().children
        val expected = listOf(
            CPSNode(
                type = IElementType.TABLE,
                children = listOf(
                    CPSNode(
                        type = IElementType.TABLE_COLOMN,
                        children = listOf(
                            CPSNode(
                                type = IElementType.PARAGRAPH,
                                children = listOf(
                                    CPSNode(type = IElementType.TEXT, literal = "a    ")
                                )
                            ),
                            CPSNode(type = IElementType.PIPE, literal = "|"),
                            CPSNode(
                                type = IElementType.PARAGRAPH,
                                children = listOf(
                                    CPSNode(type = IElementType.TEXT, literal = "b    ")
                                )
                            ),
                            CPSNode(type = IElementType.PIPE, literal = "|"),
                            CPSNode(
                                type = IElementType.PARAGRAPH,
                                children = listOf(
                                    CPSNode(type = IElementType.TEXT, literal = "c"),
                                )
                            )
                        )
                    ),
                    CPSNode(
                        type = IElementType.TABLE_COLOMN,
                        children = listOf(
                            CPSNode(
                                type = IElementType.PARAGRAPH,
                                children = listOf(
                                    CPSNode(type = IElementType.TEXT, literal = "true "),
                                )
                            ),
                            CPSNode(type = IElementType.PIPE, literal = "|"),
                            CPSNode(
                                type = IElementType.PARAGRAPH,
                                children = listOf(
                                    CPSNode(type = IElementType.TEXT, literal = "false"),
                                )
                            ),
                            CPSNode(type = IElementType.PIPE, literal = "|"),
                            CPSNode(
                                type = IElementType.PARAGRAPH,
                                children = listOf(
                                    CPSNode(type = IElementType.TEXT, literal = "true"),
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
        val actual = Parser(source).parse().children
        val expected = listOf(
            CPSNode(
                type = IElementType.PARAGRAPH,
                children = listOf(
                    CPSNode(
                        type = IElementType.COLORED,
                        literal = "#FF0000",
                        children = listOf(
                            CPSNode(type = IElementType.TEXT, literal = "color this text"),
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
        val actual = Parser(source).parse().children
        val expected = listOf(
            CPSNode(
                type = IElementType.PARAGRAPH,
                children = listOf(
                    CPSNode(
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
        val actual = Parser(source).parse().children
        val expected = listOf(
            CPSNode(
                type = IElementType.ELEMENT,
                children = listOf(
                    CPSNode(
                        type = IElementType.ELEMENT_BULLET,
                        children = listOf(
                            CPSNode(
                                type = IElementType.PARAGRAPH,
                                children = listOf(
                                    CPSNode(type = IElementType.TEXT, literal = "unordered item"),
                                )
                            )
                        )
                    ),
                    CPSNode(
                        type = IElementType.ELEMENT_UNCHECKED,
                        children = listOf(
                            CPSNode(
                                type = IElementType.PARAGRAPH,
                                children = listOf(
                                    CPSNode(type = IElementType.TEXT, literal = "unchecked item"),
                                )
                            )
                        )
                    ),
                    CPSNode(
                        type = IElementType.ELEMENT_CHECKED,
                        children = listOf(
                            CPSNode(
                                type = IElementType.PARAGRAPH,
                                children = listOf(
                                    CPSNode(type = IElementType.TEXT, literal = "checked item"),
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