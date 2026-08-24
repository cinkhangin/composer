package composer.codeparse

import composer.codegen.CodeGen
import composer.model.ComposablePreview
import composer.model.Node
import composer.model.childNodes
import composer.model.replaceById
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WriteBackTest {

    private fun twoScreens(): Node.Artboard = Node.Artboard(
        id = "artboard",
        composables = listOf(
            Node.Composable("s1", children = listOf(Node.Text("t1", "One")), preview = ComposablePreview()),
            Node.Composable(
                "s2",
                children = listOf(
                    Node.Text("t2", "Two"),
                    Node.RawCode("r1", "if (loading) {\n    SpinnerWidget()\n}"),
                ),
                preview = ComposablePreview(),
            ),
        ),
        layerNames = mapOf("s1" to "FirstScreen", "s2" to "SecondScreen"),
    )

    private fun parse(text: String): ParsedDesign = DesignParser.parse(text)!!

    @Test
    fun editing_a_parameterized_screen_preserves_its_signature() {
        val text = buildString {
            appendLine("import androidx.compose.material3.Text")
            appendLine("import androidx.compose.runtime.Composable")
            appendLine()
            appendLine("@Composable")
            appendLine("fun Helper(label: String, count: Int = 0) {")
            appendLine("    Text(label)")
            appendLine("    Text(\"static\")")
            appendLine("}")
            appendLine()
            appendLine("@Preview")
            appendLine("@Composable")
            appendLine("fun HelperPreview() { Helper(label = \"Preview\") }")
        }
        val prev = parse(text)
        // Designer edits the static Text; the parameter-backed Text remains source-aware.
        val screen = prev.artboard.composables.single() as Node.Composable
        val staticText = screen.children.last()
        val edited = prev.artboard.replaceById(staticText.id) {
            (it as Node.Text).copy(text = "edited")
        } as Node.Artboard
        val out = WriteBackPlanner.apply(text, WriteBackPlanner.plan(text, prev, edited))
        assertTrue("fun Helper(label: String, count: Int = 0) {" in out, out)
        assertTrue("Text(\"edited\")" in out, out)
        assertTrue("Text(label)" in out, out)
        // Re-parse: signature still recorded, tree matches the edit.
        val reparsed = parse(out)
        assertEquals("(label: String, count: Int = 0)", reparsed.functions.single().paramList)
    }


    @Test
    fun styling_interpolated_text_preserves_binding_and_modifier_parameter() {
        val text = buildString {
            appendLine("import androidx.compose.material3.Text")
            appendLine("import androidx.compose.runtime.Composable")
            appendLine("import androidx.compose.ui.Modifier")
            appendLine()
            appendLine("@Composable")
            appendLine("fun Greeting(name: String, modifier: Modifier = Modifier) {")
            appendLine("    Text(")
            appendLine("        text = \"Hello ${'$'}name!\",")
            appendLine("        modifier = modifier")
            appendLine("    )")
            appendLine("}")
            appendLine()
            appendLine("@Preview")
            appendLine("@Composable")
            appendLine("fun GreetingPreview() { Greeting(name = \"World\") }")
        }
        val prev = parse(text)
        val node = (prev.artboard.composables.single() as Node.Composable).children.single() as Node.Text
        assertEquals("Hello name!", node.text)
        assertEquals("\"Hello ${'$'}name!\"", node.textExpression)
        val edited = prev.artboard.replaceById(node.id) {
            (it as Node.Text).copy(fontSize = 20)
        } as Node.Artboard

        val out = WriteBackPlanner.apply(text, WriteBackPlanner.plan(text, prev, edited))

        assertTrue("fun Greeting(name: String, modifier: Modifier = Modifier)" in out, out)
        assertTrue("\"Hello ${'$'}name!\"" in out, out)
        assertTrue("modifier = modifier" in out, out)
        assertTrue("fontSize = 20.sp" in out, out)
        val reparsed = parse(out)
        val reparsedText = (reparsed.artboard.composables.single() as Node.Composable).children.single() as Node.Text
        assertEquals(node.textExpression, reparsedText.textExpression)
        assertEquals(node.modifier, reparsedText.modifier)
    }

    @Test
    fun unchanged_design_produces_zero_edits() {
        val text = CodeGen.generate(twoScreens())
        val prev = parse(text)
        val plan = WriteBackPlanner.plan(text, prev, prev.artboard)
        assertEquals(emptyList(), plan.edits)
    }

    @Test
    fun moving_a_screen_on_the_artboard_is_not_a_code_edit() {
        val text = CodeGen.generate(twoScreens())
        val prev = parse(text)
        val moved = prev.artboard.copy(
            composables = prev.artboard.composables.map {
                (it as Node.Composable).copy(x = it.x + 120, y = 40)
            },
        )
        assertEquals(emptyList(), WriteBackPlanner.plan(text, prev, moved).edits)
    }

    @Test
    fun editing_one_screen_leaves_the_other_byte_identical() {
        val text = CodeGen.generate(twoScreens())
        val prev = parse(text)
        val s1 = prev.artboard.composables[0] as Node.Composable
        val fn1 = prev.functions[0]
        val fn1Original = text.substring(fn1.fnRange.first, fn1.fnRange.last + 1)
        // Change the OTHER screen's text node.
        val editedS2 = prev.artboard.replaceById(prev.artboard.composables[1].childNodes()[0].id) {
            (it as Node.Text).copy(text = "Two, edited")
        } as Node.Artboard
        val plan = WriteBackPlanner.plan(text, prev, editedS2)
        val result = WriteBackPlanner.apply(text, plan)
        assertTrue(fn1Original in result, "unchanged function must stay byte-identical")
        assertTrue("Two, edited" in result)
        assertTrue("SpinnerWidget()" in result, "RawCode must survive the rewrite verbatim")
        // The result reparses to the edited design.
        assertEquals(canon(editedS2), canon(parse(result).artboard))
        // s1's screen was untouched entirely — only one function edit.
        assertEquals(1, plan.edits.size)
        assertEquals(s1.id, prev.functions[0].screenId)
    }

    @Test
    fun new_screen_appends_at_eof_and_new_imports_merge_append_only() {
        val text = CodeGen.generate(
            Node.Artboard(
                id = "artboard",
                composables = listOf(
                    Node.Composable(
                        "s1",
                        children = listOf(Node.Text("t1", "One")),
                        preview = ComposablePreview(),
                    ),
                ),
                layerNames = mapOf("s1" to "FirstScreen"),
            ),
        )
        val prev = parse(text)
        val added = prev.artboard.copy(
            composables = prev.artboard.composables + Node.Composable(
                "new1",
                children = listOf(Node.Switch("sw1", checked = true)),
                preview = ComposablePreview(),
            ),
            layerNames = prev.artboard.layerNames + ("new1" to "Screen 2"),
        )
        val result = WriteBackPlanner.apply(text, WriteBackPlanner.plan(text, prev, added))
        assertTrue(result.contains("fun Screen2() {"), result)
        assertTrue(result.contains("import androidx.compose.material3.Switch"), result)
        // Existing imports untouched, in place, exactly once.
        assertEquals(1, Regex("import androidx\\.compose\\.material3\\.Text\\b").findAll(result).count(), result)
        assertTrue(result.startsWith("import androidx.compose"), result)
        // Layer names round-trip as their sanitized function names ("Screen 2" → Screen2).
        val expected = added.copy(layerNames = added.layerNames.mapValues { (id, name) -> if (id == "new1") "Screen2" else name })
        assertEquals(canon(expected), canon(parse(result).artboard))
    }

    @Test
    fun deleting_a_screen_removes_its_function() {
        val text = CodeGen.generate(twoScreens())
        val prev = parse(text)
        val without = prev.artboard.copy(composables = prev.artboard.composables.take(1))
        val result = WriteBackPlanner.apply(text, WriteBackPlanner.plan(text, prev, without))
        assertTrue("fun FirstScreen()" in result)
        assertTrue("fun SecondScreen()" !in result)
        assertTrue(!result.contains("\n\n\n"), "no leftover blank runs:\n$result")
    }

    @Test
    fun renaming_a_screen_regenerates_its_instance_callers() {
        val design = Node.Artboard(
            id = "artboard",
            composables = listOf(
                Node.Composable(
                    "s1",
                    children = listOf(Node.Text("t1", "Card")),
                    preview = ComposablePreview(),
                ),
                Node.Composable(
                    "s2",
                    children = listOf(Node.Instance("i1", "s1")),
                    preview = ComposablePreview(),
                ),
            ),
            layerNames = mapOf("s1" to "CardWidget", "s2" to "Home"),
            componentIds = listOf("s1"),
        )
        val text = CodeGen.generate(design)
        val prev = parse(text)
        val renamed = prev.artboard.copy(
            layerNames = prev.artboard.layerNames.mapValues { (id, name) ->
                if (id == prev.artboard.composables[0].id) "FancyCard" else name
            },
        )
        val result = WriteBackPlanner.apply(text, WriteBackPlanner.plan(text, prev, renamed))
        assertTrue("fun FancyCard()" in result, result)
        assertTrue("FancyCard()" in result, "caller must call the new name:\n$result")
        assertTrue("fun CardWidgetPreview()" in result, "the source preview name stays stable:\n$result")
    }

    @Test
    fun untouched_file_regions_survive_write_back() {
        // Hand-written file: helper function + comment between declarations.
        val text = buildString {
            appendLine("package demo")
            appendLine()
            appendLine("import androidx.compose.material3.Text")
            appendLine("import androidx.compose.runtime.Composable")
            appendLine()
            appendLine("// A helper the designer must never touch.")
            appendLine("fun formatPrice(cents: Int): String = \"$\" + cents / 100.0")
            appendLine()
            appendLine("@Composable")
            appendLine("fun Screen() {")
            appendLine("    Text(\"One\")")
            appendLine("}")
            appendLine()
            appendLine("@Preview")
            appendLine("@Composable")
            appendLine("fun ScreenPreview() { Screen() }")
        }
        val prev = parse(text)
        val edited = prev.artboard.replaceById(prev.artboard.composables[0].childNodes()[0].id) {
            (it as Node.Text).copy(text = "One, edited")
        } as Node.Artboard
        val result = WriteBackPlanner.apply(text, WriteBackPlanner.plan(text, prev, edited))
        assertTrue("// A helper the designer must never touch." in result)
        assertTrue("fun formatPrice(cents: Int): String = \"$\" + cents / 100.0" in result)
        assertTrue("package demo" in result)
        assertTrue("One, edited" in result)
    }
}
