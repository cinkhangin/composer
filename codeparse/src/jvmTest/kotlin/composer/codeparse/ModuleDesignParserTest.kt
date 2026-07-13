package composer.codeparse

import composer.model.Node
import composer.model.replaceById
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModuleDesignParserTest {
    private val first = SourceFile(
        "/module/src/main/kotlin/demo/Home.kt",
        """
        package demo
        import androidx.compose.material3.Text
        import androidx.compose.runtime.Composable

        @Composable
        fun Home() {
            Text("Home")
            SharedCard()
        }

        class Holder {
            @Composable fun MemberOnly() { Text("member") }
        }
        """.trimIndent(),
    )
    private val second = SourceFile(
        "/module/src/main/kotlin/demo/Shared.kt",
        """
        package demo
        import androidx.compose.material3.Text
        import androidx.compose.runtime.Composable

        @Composable
        fun SharedCard() { Text("Card") }

        @Composable
        fun WithParameter(label: String) { Text(label) }
        """.trimIndent(),
    )

    @Test
    fun renders_every_parseable_top_level_composable_across_files() {
        val parsed = assertNotNull(ModuleDesignParser.parse(listOf(second, first)))
        assertEquals(listOf("Home", "SharedCard", "WithParameter"), parsed.artboard.composables.map {
            parsed.artboard.layerNames[it.id]
        })
        assertTrue(parsed.artboard.layerNames.values.none { it == "MemberOnly" })
        val home = parsed.artboard.composables.first() as Node.Composable
        assertTrue(home.children.any { it is Node.Instance })
        assertEquals(listOf(0, 470, 940), parsed.artboard.composables.map { (it as Node.Composable).x })
    }

    @Test
    fun edits_only_the_owning_file_and_preserves_parameterized_raw_code() {
        val parsed = assertNotNull(ModuleDesignParser.parse(listOf(first, second)))
        val card = parsed.artboard.composables.first { parsed.artboard.layerNames[it.id] == "SharedCard" } as Node.Composable
        val text = card.children.single() as Node.Text
        val edited = parsed.artboard.replaceById(text.id) { (it as Node.Text).copy(text = "Changed") } as Node.Artboard
        val files = listOf(first, second).associate { it.path to it.text }
        val plan = ModuleWriteBackPlanner.plan(parsed, files, edited)
        assertEquals(listOf(second.path), plan.files.map { it.path })
        val result = WriteBackPlanner.apply(second.text, WriteBackPlan(plan.files.single().edits))
        assertTrue("Text(\"Changed\")" in result)
        assertTrue("fun WithParameter(label: String) { Text(label) }" in result)
    }

    @Test
    fun structural_changes_are_blocked_until_annotations_exist() {
        val parsed = assertNotNull(ModuleDesignParser.parse(listOf(first, second)))
        val removed = parsed.artboard.copy(composables = parsed.artboard.composables.dropLast(1))
        val plan = ModuleWriteBackPlanner.plan(parsed, mapOf(first.path to first.text, second.path to second.text), removed)
        assertNotNull(plan.blockedReason)
        assertTrue(plan.files.isEmpty())
    }

    @Test
    fun overloads_render_with_distinct_ids() {
        val file = SourceFile(
            "/module/Overloads.kt",
            """
            @Composable fun Chip() { Text("one") }
            @Composable fun Chip(label: String) { Text(label) }
            """.trimIndent(),
        )
        val parsed = assertNotNull(ModuleDesignParser.parse(listOf(file)))
        assertEquals(2, parsed.artboard.composables.size)
        assertEquals(2, parsed.artboard.composables.map { it.id }.toSet().size)
        assertTrue(parsed.warnings.single().contains("Chip"))
    }
}
