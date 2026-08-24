package composer

import composer.model.ComposablePreview
import composer.model.Node
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
