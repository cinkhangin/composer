package composer.codeparse

import composer.codegen.CodeGen
import composer.model.Node
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Targeted fallback/preservation cases — the "never destroy code" contract. */
class ParserCasesTest {

    private fun parseOne(body: String): Node.Composable {
        // Assembled line by line — trimIndent() over interpolated multi-line
        // bodies mis-indents (template vs body indents get mixed).
        val text = buildString {
            appendLine("import androidx.compose.material3.Text")
            appendLine("import androidx.compose.runtime.Composable")
            appendLine()
            appendLine("@Composable")
            appendLine("fun Screen() {")
            body.trimEnd().lines().forEach { appendLine(if (it.isBlank()) "" else "    $it") }
            appendLine("}")
        }
        val parsed = DesignParser.parse(text) ?: error("no screen parsed")
        return parsed.artboard.composables.single() as Node.Composable
    }

    @Test
    fun unknown_statements_become_rawcode_without_poisoning_siblings() {
        val screen = parseOne(
            """
            Text("hello")
            LazyColumn {
                items(10) { Text("row") }
            }
            Text("world")
            """.trimIndent(),
        )
        assertEquals(3, screen.children.size)
        assertTrue(screen.children[0] is Node.Text)
        val raw = screen.children[1] as Node.RawCode
        assertEquals("LazyColumn {\n    items(10) { Text(\"row\") }\n}", raw.code)
        assertTrue(screen.children[2] is Node.Text)
    }

    @Test
    fun orphaned_state_decl_rematerializes_as_rawcode_in_place() {
        val screen = parseOne(
            """
            var custom by remember { mutableStateOf(true) }
            Text("hello")
            """.trimIndent(),
        )
        assertEquals(2, screen.children.size)
        val raw = screen.children[0] as Node.RawCode
        assertEquals("var custom by remember { mutableStateOf(true) }", raw.code)
        assertTrue(screen.children[1] is Node.Text)
    }

    @Test
    fun adjacent_state_decl_is_swallowed_by_its_consumer() {
        val screen = parseOne(
            """
            var state1 by remember { mutableStateOf(true) }
            Switch(checked = state1, onCheckedChange = { state1 = it })
            """.trimIndent(),
        )
        val switch = screen.children.single() as Node.Switch
        assertTrue(switch.checked)
    }

    @Test
    fun comments_above_a_recognized_call_force_rawcode_to_preserve_them() {
        val screen = parseOne(
            """
            // do not lose me
            Text("hello")
            """.trimIndent(),
        )
        val raw = screen.children.single() as Node.RawCode
        assertEquals("// do not lose me\nText(\"hello\")", raw.code)
    }

    @Test
    fun trailing_same_line_comment_forces_rawcode() {
        val screen = parseOne("Text(\"hello\") // trailing\n")
        val raw = screen.children.single() as Node.RawCode
        assertEquals("Text(\"hello\") // trailing", raw.code)
    }

    @Test
    fun conflicting_import_blocks_the_name_file_wide() {
        val file =
            """
            import my.designsystem.Text
            import androidx.compose.runtime.Composable

            @Composable
            fun Screen() {
                Text("hello")
            }
            """.trimIndent()
        val screen = DesignParser.parse(file)!!.artboard.composables.single() as Node.Composable
        assertTrue(screen.children.single() is Node.RawCode)
    }

    @Test
    fun scaffold_content_rawcode_keeps_inner_padding_reference() {
        val screen = parseOne(
            """
            Scaffold { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    CustomThing()
                }
            }
            """.trimIndent(),
        )
        val scaffold = screen.children.single() as Node.Scaffold
        val box = scaffold.children.single() as Node.Box
        assertTrue(box.modifier.isEmpty(), "scope padding must be stripped, found ${box.modifier}")
        val raw = box.children.single() as Node.RawCode
        assertEquals("CustomThing()", raw.code)
        // Regeneration re-imposes padding(innerPadding) on the direct child.
        val out = CodeGen.generate(Node.Composable("s", children = listOf(scaffold)))
        assertTrue("Scaffold { innerPadding ->" in out, out)
        assertTrue("Box(modifier = Modifier.padding(innerPadding))" in out, out)
    }

    @Test
    fun non_canonical_scaffold_param_name_is_rawcode() {
        val screen = parseOne(
            """
            Scaffold { padding ->
                Text("x", modifier = Modifier.padding(padding))
            }
            """.trimIndent(),
        )
        assertTrue(screen.children.single() is Node.RawCode)
    }

    @Test
    fun functions_with_parameters_parse_with_signature_preserved() {
        val file =
            """
            import androidx.compose.runtime.Composable

            @Composable
            fun Screen() {
                Text("a")
            }

            @Composable
            fun Helper(label: String, count: Int = 0) {
                Text(label)
                Text("static")
            }
            """.trimIndent()
        val parsed = DesignParser.parse(file)!!
        assertEquals(listOf("Screen", "Helper"), parsed.functions.map { it.functionName })
        val helper = parsed.functions.last()
        assertEquals("(label: String, count: Int = 0)", helper.paramList)
        // Statements that use the params degrade to RawCode; static ones parse.
        val screen = parsed.artboard.composables.last() as composer.model.Node.Composable
        assertTrue(screen.children.first() is composer.model.Node.RawCode)
        assertTrue((screen.children.last() as composer.model.Node.Text).text == "static")
    }

    @Test
    fun generated_theme_block_is_not_a_screen_and_not_foreign() {
        val themed = composer.model.Node.Artboard(
            id = "a",
            composables = listOf(Node.Composable("s1", children = listOf(Node.Text("t1", "hi")))),
            themes = listOf(
                composer.model.NamedTheme("Light", composer.model.DesignTheme(primary = 0xFF112233)),
            ),
        )
        val code = CodeGen.generate(themed)
        require("fun AppTheme(" in code && "lightColorScheme(" in code) { code }
        val parsed = DesignParser.parse(code)!!
        // The AppTheme wrapper and scheme vals are codegen's own output — one screen, no foreign code.
        assertEquals(listOf("Composable1"), parsed.artboard.composables.map { parsed.artboard.layerNames[it.id] })
        assertTrue(!parsed.hasNonScreenDeclarations)
    }

    @Test
    fun helper_functions_flag_foreign_declarations() {
        val parsed = DesignParser.parse(
            """
            import androidx.compose.runtime.Composable

            fun helper(): Int = 1

            @Composable
            fun Screen() {
                Text("a")
            }
            """.trimIndent(),
        )!!
        assertTrue(parsed.hasNonScreenDeclarations)
    }

    @Test
    fun file_without_composables_parses_to_null() {
        assertNull(DesignParser.parse("fun main() {}"))
    }

    @Test
    fun preview_composables_are_not_design_screens() {
        val parsed = DesignParser.parse(
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview

            @Preview
            @Composable
            fun HomePreview() {
                Home()
            }

            @Composable
            fun Home() {
                Text("home")
            }
            """.trimIndent(),
        )!!

        assertEquals(listOf("Home"), parsed.artboard.composables.map { parsed.artboard.layerNames[it.id] })
        assertTrue(parsed.hasNonScreenDeclarations)
    }

    @Test
    fun same_file_instance_calls_become_instances() {
        val file =
            """
            import androidx.compose.runtime.Composable

            @Composable
            fun CardWidget() {
                Text("card")
            }

            @Composable
            fun Home() {
                CardWidget()
            }
            """.trimIndent()
        val parsed = DesignParser.parse(file)!!
        val home = parsed.artboard.composables[1] as Node.Composable
        val inst = home.children.single() as Node.Instance
        val cardId = parsed.artboard.composables[0].id
        assertEquals(cardId, inst.refId)
        assertEquals(listOf(cardId), parsed.artboard.componentIds)
    }
}
