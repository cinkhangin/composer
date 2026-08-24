package composer.codeparse

import composer.codegen.CodeGen
import composer.model.Node
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreviewContractTest {
    @Test
    fun composable_without_preview_is_not_rendered() {
        val source = """
            @Composable
            fun Hidden() {
                Text("not on canvas")
            }
        """.trimIndent()

        assertNull(DesignParser.parse(source))
        assertTrue(DesignParser.composableFunctions(source).isEmpty())
    }

    @Test
    fun preview_inside_theme_wrapper_supplies_inspector_parameters() {
        val source = """
            @Composable
            fun Greeting(name: String, count: Int = 2) {
                Text("Hello ${'$'}name: ${'$'}count")
            }

            @Preview(showBackground = true)
            @Composable
            fun GreetingPreview() {
                AppTheme {
                    Greeting(name = "Android", count = 4)
                }
            }
        """.trimIndent()

        val parsed = assertNotNull(DesignParser.parse(source))
        val screen = parsed.artboard.composables.single() as Node.Composable
        assertEquals("GreetingPreview", screen.preview?.functionName)
        assertEquals(
            listOf("name" to "\"Android\"", "count" to "4"),
            screen.preview?.parameters?.map { it.name to it.expression },
        )
    }

    @Test
    fun editing_preview_value_rewrites_only_the_invocation() {
        val source = """
            @Composable
            fun Greeting(name: String) {
                Text("Hello ${'$'}name")
            }

            @Preview(showBackground = true)
            @Composable
            fun GreetingPreview() {
                Greeting(name = "Android")
            }
        """.trimIndent()
        val parsed = assertNotNull(DesignParser.parse(source))
        val screen = parsed.artboard.composables.single() as Node.Composable
        val preview = assertNotNull(screen.preview)
        val editedScreen = screen.copy(
            preview = preview.copy(
                parameters = preview.parameters.map { it.copy(expression = "\"Desktop\"") },
            ),
        )
        val edited = parsed.artboard.copy(composables = listOf(editedScreen))

        val result = WriteBackPlanner.apply(source, WriteBackPlanner.plan(source, parsed, edited))

        assertTrue("fun Greeting(name: String) {\n    Text(\"Hello ${'$'}name\")\n}" in result)
        assertTrue("@Preview(showBackground = true)" in result)
        assertTrue("Greeting(name = \"Desktop\")" in result)

        val regenerated = CodeGen.generate(edited)
        assertTrue("fun Greeting(name: String)" in regenerated, regenerated)
        assertTrue("Greeting(name = \"Desktop\")" in regenerated, regenerated)
    }
}
