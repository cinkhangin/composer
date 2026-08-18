package composer.codeparse

import composer.model.Node
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NewComposableSourceTest {
    @Test
    fun rejects_a_file_that_already_contains_a_composable() {
        val source = """
            package demo

            import androidx.compose.material3.Text
            import androidx.compose.runtime.Composable

            @Composable
            fun Greeting() {
                Text("Hello")
            }
        """.trimIndent()

        assertFailsWith<IllegalArgumentException> { NewComposableSource.create(source, "ProfileScreen") }
    }

    @Test
    fun creates_a_new_packaged_file_and_rejects_name_collisions() {
        val created = NewComposableSource.create("package demo\n", "Settings")
        assertTrue(created.startsWith("package demo\n"))
        assertTrue("fun Settings()" in created)
        val parsed = assertNotNull(DesignParser.parse(created))
        assertEquals(listOf("Settings"), parsed.artboard.composables.map { parsed.artboard.layerNames[it.id] })
        val added = parsed.artboard.composables.single() as Node.Composable
        assertTrue(added.children.single() is Node.Box)
        assertFailsWith<IllegalArgumentException> { NewComposableSource.create(created, "Profile") }
    }

    @Test
    fun allows_preview_functions_beside_the_single_composable() {
        val previewOnly = """
            package demo

            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview

            @Preview
            @Composable
            fun SettingsPreview() = Unit
        """.trimIndent()

        val created = NewComposableSource.create(previewOnly, "Settings")

        assertTrue("fun SettingsPreview()" in created)
        assertEquals(listOf("Settings"), DesignParser.nonPreviewComposableFunctions(created).map { it.name })
    }
}
