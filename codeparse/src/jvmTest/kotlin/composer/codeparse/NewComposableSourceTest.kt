package composer.codeparse

import composer.model.Node
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NewComposableSourceTest {
    @Test
    fun appends_to_current_file_without_rewriting_existing_source() {
        val source = """
            package demo

            import androidx.compose.material3.Text
            import androidx.compose.runtime.Composable

            @Composable
            fun Greeting() {
                Text("Hello")
            }
        """.trimIndent()

        val updated = NewComposableSource.append(source, "ProfileScreen")
        assertTrue(
            """
            @Composable
            fun Greeting() {
                Text("Hello")
            }
            """.trimIndent() in updated,
        )
        assertTrue("import androidx.compose.foundation.layout.Box" in updated)
        assertTrue("fun ProfileScreen()" in updated)
        val parsed = assertNotNull(DesignParser.parse(updated))
        assertEquals(listOf("Greeting", "ProfileScreen"), parsed.artboard.composables.map {
            parsed.artboard.layerNames[it.id]
        })
        val added = parsed.artboard.composables.last() as Node.Composable
        assertTrue(added.children.single() is Node.Box)
    }

    @Test
    fun creates_a_new_packaged_file_and_rejects_name_collisions() {
        val created = NewComposableSource.append("package demo\n", "Settings")
        assertTrue(created.startsWith("package demo\n"))
        assertTrue("fun Settings()" in created)
        assertFailsWith<IllegalArgumentException> { NewComposableSource.append(created, "Settings") }
    }
}
