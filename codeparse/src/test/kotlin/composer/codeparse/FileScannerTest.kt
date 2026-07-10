package composer.codeparse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileScannerTest {

    private fun funs(src: String): List<KFunctionDecl> =
        scanSource(src).declarations.filterIsInstance<KFunctionDecl>()

    @Test
    fun package_and_imports() {
        val src = """
            package com.example.app

            import androidx.compose.material3.Text
            import androidx.compose.foundation.layout.*
            import my.lib.Thing as Other

            fun x() { }
        """.trimIndent()
        val f = scanSource(src)
        assertEquals(src.indexOf("com.example.app") + "com.example.app".length, f.packageEndOffset)
        assertEquals(
            listOf("androidx.compose.material3.Text", "androidx.compose.foundation.layout.*", "my.lib.Thing"),
            f.imports.map { it.pathStr },
        )
        assertEquals("Other", f.imports[2].alias)
        assertTrue(f.imports[1].isAllUnder)
        // Insert offset = end of the last import directive (the alias).
        assertEquals(src.indexOf("as Other") + "as Other".length, f.imports.last().endOffset)
    }

    @Test
    fun screen_shaped_function() {
        val src = """
            @Composable
            fun Home() {
                Text("hi")
            }
        """.trimIndent()
        val fn = funs(src).single()
        assertEquals("Home", fn.name)
        assertEquals(listOf("Composable"), fn.annotationNames)
        assertFalse(fn.hasReceiver)
        assertFalse(fn.hasTypeParams)
        assertFalse(fn.hasReturnType)
        assertEquals("()", fn.paramListText)
        assertNotNull(fn.bodyBlock)
        assertEquals(src.indices.first until src.length, fn.range.first until fn.range.last + 1)
        assertEquals(1, fn.bodyBlock!!.statements.size)
    }

    @Test
    fun optin_annotation_args_are_part_of_the_declaration() {
        val src = """
            @OptIn(ExperimentalMaterial3Api::class)
            @Composable
            fun Screen() {
            }
        """.trimIndent()
        val fn = funs(src).single()
        assertEquals(listOf("OptIn", "Composable"), fn.annotationNames)
        assertEquals(0, fn.range.first)
        assertEquals(src.length - 1, fn.range.last)
    }

    @Test
    fun member_composables_are_not_top_level() {
        val src = """
            class Holder(val x: Int) : Base() {
                @Composable
                fun Inner() { Text("no") }
            }

            object Single {
                @Composable fun Obj() { }
            }

            @Composable
            fun Real() { }
        """.trimIndent()
        assertEquals(listOf("Real"), funs(src).map { it.name })
    }

    @Test
    fun expression_body_and_bodyless_functions() {
        val src = """
            fun price(): String = "$" + amount / 100.0
            fun generic(): Map<String, () -> Unit> = mapOf()
            @Composable
            fun Screen() { }
        """.trimIndent()
        val fs = funs(src)
        assertEquals(listOf("price", "generic", "Screen"), fs.map { it.name })
        assertNull(fs[0].bodyBlock)
        assertTrue(fs[0].hasReturnType)
        assertNull(fs[1].bodyBlock)
        assertNotNull(fs[2].bodyBlock)
        // The expression body extends to the end of its line.
        assertEquals(src.indexOf("100.0") + "100.0".length - 1, fs[0].range.last)
    }

    @Test
    fun receivers_and_type_params_flagged() {
        val src = """
            fun String.ext() { }
            fun <T> generic(t: T) { }
            fun List<Int>.sum2(): Int = 0
            fun plain() { }
        """.trimIndent()
        val fs = funs(src)
        assertTrue(fs[0].hasReceiver)
        assertTrue(fs[1].hasTypeParams)
        assertTrue(fs[2].hasReceiver)
        assertFalse(fs[3].hasReceiver)
        assertFalse(fs[3].hasTypeParams)
    }

    @Test
    fun param_list_text_is_verbatim() {
        val src = """
            @Composable
            fun Screen(modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
            }
        """.trimIndent()
        val fn = funs(src).single()
        assertEquals("(modifier: Modifier = Modifier, onClick: () -> Unit = {})", fn.paramListText)
    }

    @Test
    fun kdoc_is_part_of_the_declaration_range() {
        val src = """
            /** The home screen. */
            @Composable
            fun Home() { }
        """.trimIndent()
        val fn = funs(src).single()
        assertEquals(0, fn.range.first)
    }

    @Test
    fun plain_comment_above_is_not_part_of_the_declaration() {
        val src = """
            // just a note
            @Composable
            fun Home() { }
        """.trimIndent()
        val fn = funs(src).single()
        assertEquals(src.indexOf("@Composable"), fn.range.first)
    }

    @Test
    fun file_annotations_and_properties_are_skipped() {
        val src = """
            @file:Suppress("unused")
            package x

            val LightColors = lightColorScheme(
                primary = Color(0xFF6650A4),
            )

            private const val MAX = 10

            var counter = 0
                private set

            typealias Handler = () -> Unit

            @Composable
            fun Screen() { }
        """.trimIndent()
        val f = scanSource(src)
        assertEquals(listOf("Screen"), funs(src).map { it.name })
        assertTrue(f.declarations.filterIsInstance<KOtherDecl>().size >= 4)
    }

    @Test
    fun top_level_function_names_in_order() {
        val src = """
            fun helper() = 1
            @Composable
            fun A() { }
            class C { fun member() {} }
            @Composable
            fun B() { }
        """.trimIndent()
        val names = funs(src).map { it.name }
        assertEquals(listOf("helper", "A", "B"), names)
    }

    @Test
    fun enum_and_nested_classes_are_skipped_wholesale() {
        val src = """
            enum class Kind { A, B, C }
            sealed interface Node {
                data class Leaf(val x: Int) : Node
                companion object { fun of() = Leaf(1) }
            }
            @Composable
            fun S() { }
        """.trimIndent()
        assertEquals(listOf("S"), funs(src).map { it.name })
    }

    @Test
    fun malformed_input_never_throws() {
        for (src in listOf("", "}", "fun", "fun (", "class", "@", "fun f() {", "val x = ", "import")) {
            scanSource(src) // must not throw
        }
    }
}
