package composer

import composer.codegen.CodeGen
import composer.model.ComposablePreview
import composer.model.NavAction
import composer.model.Node
import composer.model.PreviewParameter
import composer.render.previewText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PreviewRenderingTest {
    @Test
    fun editor_hides_functions_without_a_preview() {
        val state = EditorState(
            Node.Artboard(
                id = "artboard",
                composables = listOf(
                    Node.Composable("hidden"),
                    Node.Composable("visible", preview = ComposablePreview()),
                ),
            ),
        )

        assertEquals(listOf("visible"), state.composables.map { it.id })
    }

    @Test
    fun newly_created_composable_has_a_preview_and_visible_empty_box() {
        val state = EditorState(Node.Artboard("artboard"))

        state.addComposable()

        val screen = state.composables.single()
        assertNotNull(screen.preview)
        assertTrue(screen.children.single() is Node.Box)
    }

    @Test
    fun preview_parameters_resolve_direct_and_interpolated_text() {
        val values = mapOf("name" to "\"Desktop\"", "count" to "4")

        assertEquals("Desktop", previewText("name", "name", values))
        assertEquals(
            "Hello Desktop: 4",
            previewText("fallback", "\"Hello ${'$'}name: ${'$'}count\"", values),
        )
    }

    @Test
    fun string_parameter_values_are_quoted_and_escaped_automatically() {
        assertEquals("\"Desktop\"", parameterValueForSource("String", "Desktop"))
        assertEquals("Desktop", parameterValueForDisplay("String", "\"Desktop\""))
        assertEquals(
            "\"A \\\"quoted\\\" \\\\ value\\n\"",
            parameterValueForSource("kotlin.String", "A \"quoted\" \\ value\n"),
        )
        assertEquals("null", parameterValueForSource("String?", "null"))
        assertEquals("42", parameterValueForSource("Int", " 42 "))
    }

    @Test
    fun dollar_prefixed_text_binds_to_a_string_parameter() {
        val parameters = listOf(
            PreviewParameter("email", "String", "\"hello@example.com\""),
            PreviewParameter("count", "Int", "3"),
        )
        val bound = Node.Text("text", "${'$'}email", textExpression = "email")

        assertEquals("email", textParameterReference("${'$'}email", parameters))
        assertEquals(null, textParameterReference("${'$'}count", parameters))
        assertEquals(null, textParameterReference("${'$'}missing", parameters))
        assertEquals("${'$'}email", textValueForDisplay(bound, parameters))
        assertEquals(
            "hello@example.com",
            previewText(bound.text, bound.textExpression, parameters.associate { it.name to it.expression }),
        )
    }

    @Test
    fun text_parameter_bindings_follow_rename_and_detach_on_removal() {
        val state = EditorState(
            Node.Artboard(
                id = "artboard",
                composables = listOf(
                    Node.Composable(
                        id = "screen",
                        children = listOf(Node.Text("text", "${'$'}email", textExpression = "email")),
                        preview = ComposablePreview(
                            parameters = listOf(
                                PreviewParameter("email", "String", "\"hello@example.com\""),
                            ),
                        ),
                        sourceParameterList = "(email: String)",
                        parametersManagedByEditor = true,
                    ),
                ),
                layerNames = mapOf("screen" to "Profile"),
            ),
        )
        val parameter = state.composables.single().preview!!.parameters.single()

        state.setComposableParameter("screen", 0, parameter.copy(name = "contactEmail"))

        val renamed = state.composables.single().children.single() as Node.Text
        assertEquals("contactEmail", renamed.textExpression)
        assertEquals("${'$'}contactEmail", renamed.text)
        assertTrue("Text(contactEmail)" in CodeGen.generate(state.root))

        state.removeComposableParameter("screen", 0)

        val detached = state.composables.single().children.single() as Node.Text
        assertEquals("", detached.textExpression)
        assertEquals("${'$'}contactEmail", detached.text)
        assertTrue("Text(\"\\${'$'}contactEmail\")" in CodeGen.generate(state.root))
    }

    @Test
    fun inspector_parameters_drive_signature_preview_and_text_expression() {
        val state = EditorState(
            Node.Artboard(
                id = "artboard",
                composables = listOf(
                    Node.Composable(
                        id = "screen",
                        children = listOf(Node.Text("text", "Fallback")),
                        preview = ComposablePreview(),
                    ),
                ),
                layerNames = mapOf("screen" to "Greeting"),
            ),
        )

        state.addComposableParameter("screen")
        val added = state.composables.single().preview!!.parameters.single()
        state.setComposableParameter(
            "screen",
            0,
            added.copy(name = "title", type = "String", expression = "\"Desktop\"", hasDefault = true),
        )
        state.update("text") { (it as Node.Text).copy(textExpression = "title") }

        val screen = state.composables.single()
        assertEquals("(title: String = \"Desktop\")", screen.sourceParameterList)
        val generated = CodeGen.generate(state.root)
        assertTrue("fun Greeting(title: String = \"Desktop\")" in generated, generated)
        assertTrue("Text(title)" in generated, generated)
        assertTrue("Greeting(title = \"Desktop\")" in generated, generated)

        state.setComposableParameter("screen", 0, screen.preview!!.parameters.single().copy(hasDefault = false))
        assertEquals("(title: String)", state.composables.single().sourceParameterList)

        state.removeComposableParameter("screen", 0)
        assertEquals("()", state.composables.single().sourceParameterList)
        assertTrue(state.composables.single().preview!!.parameters.isEmpty())
    }

    @Test
    fun inspector_parameters_keep_generated_navigation_callbacks() {
        val state = EditorState(
            Node.Artboard(
                id = "artboard",
                composables = listOf(
                    Node.Composable(
                        id = "source",
                        children = listOf(Node.Button("button", navAction = NavAction.Navigate("target"))),
                        preview = ComposablePreview(),
                    ),
                    Node.Composable(id = "target", preview = ComposablePreview()),
                ),
                layerNames = mapOf("source" to "Source", "target" to "Target"),
            ),
        )

        state.addComposableParameter("source")

        val generated = CodeGen.generate(state.root)
        assertTrue(
            "fun Source(parameter: String = \"Value\", onNavigateToTarget: () -> Unit = {})" in generated,
            generated,
        )
        assertTrue("Button(onClick = onNavigateToTarget)" in generated, generated)
    }

    @Test
    fun removing_the_last_preview_clears_the_web_canvas() {
        val state = EditorState(
            Node.Artboard(
                id = "artboard",
                composables = listOf(Node.Composable("visible", preview = ComposablePreview())),
            ),
        )
        val sync = CodeSyncState()

        parseAndApply(
            state,
            sync,
            """
                @Composable
                fun Hidden() {
                    Text("source only")
                }
            """.trimIndent(),
        )

        assertTrue(state.composables.isEmpty())
        assertEquals(null, sync.parseError)
    }
}
