package composer.codeparse

import composer.codegen.CodeGen
import composer.model.ModifierSpec
import composer.model.Node
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
    fun unsupported_wrappers_expose_renderable_children_without_poisoning_siblings() {
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
        val lazy = screen.children[1] as Node.SourceContainer
        assertEquals("LazyColumn", lazy.name)
        val items = lazy.children.single() as Node.SourceContainer
        assertTrue(items.children.single() is Node.Text)
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
    fun rawcode_only_composable_is_not_a_design_screen() {
        val file =
            """
            import androidx.compose.runtime.Composable

            @Composable
            fun Screen() {
                CustomThing()
            }
            """.trimIndent()
        assertNull(DesignParser.parse(file))
    }

    @Test
    fun ordinary_attached_comments_are_preserved_without_hiding_the_component() {
        val screen = parseOne(
            """
            // do not lose me
            Text("still visible")
            Text("visible")
            """.trimIndent(),
        )
        val raw = screen.children.first() as Node.RawCode
        assertEquals("// do not lose me", raw.code)
        assertEquals("still visible", (screen.children[1] as Node.Text).text)
        assertTrue(screen.children.last() is Node.Text)
    }

    @Test
    fun runtime_modifier_chain_is_kept_as_opaque_source_while_children_render() {
        val screen = parseOne(
            """
            Box(modifier = Modifier.offset(y = runtimeOffset).drawBehind { customDraw() }) {
                Text("Visible child")
            }
            """.trimIndent(),
        )
        val box = screen.children.single() as Node.Box
        val source = box.modifier.single() as ModifierSpec.External
        assertEquals("Modifier.offset(y = runtimeOffset).drawBehind { customDraw() }", source.expression)
        assertTrue(source.opaque)
        assertEquals("Visible child", (box.children.single() as Node.Text).text)
    }

    @Test
    fun compose_color_constants_and_alpha_copy_render_as_static_colors() {
        val screen = parseOne(
            """
            Text("White", color = Color.White)
            Text("Faded", color = Color.Black.copy(alpha = 0.5f))
            """.trimIndent(),
        )
        assertEquals(0xFFFFFFFF, (screen.children[0] as Node.Text).color)
        assertEquals(0x80000000, (screen.children[1] as Node.Text).color)
    }

    @Test
    fun dynamic_text_color_and_letter_spacing_keep_source_without_hiding_text() {
        val screen = parseOne(
            """
            Text(
                text = title,
                color = selectedColor,
                letterSpacing = 2.sp,
            )
            """.trimIndent(),
        )
        val text = screen.children.single() as Node.Text
        assertEquals("title", text.textExpression)
        assertEquals("selectedColor", text.colorExpression)
        assertEquals(2, text.letterSpacing)
    }

    @Test
    fun typography_and_source_icons_remain_renderable_and_round_trip() {
        val screen = parseOne(
            """
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
            )
            IconButton(onClick = { goBack() }) {
                Icon(Icons.Default.Grid3x3, contentDescription = "Grid")
            }
            """.trimIndent(),
        )
        val text = screen.children[0] as Node.Text
        assertTrue(text.styleExpression.startsWith("MaterialTheme.typography"))
        val icon = screen.children[1] as Node.Icon
        assertEquals("imageVector", icon.sourceArgumentName)
        assertEquals("Icons.AutoMirrored.Filled.ArrowBack", icon.sourceImageExpression)
        assertEquals("Color.White", icon.tintExpression)
        val button = screen.children[2] as Node.IconButton
        assertEquals("{ goBack() }", button.onClickExpression)
        assertTrue("Grid3x3" in button.contentExpression)

        val out = CodeGen.generate(Node.Composable("s", children = screen.children))
        assertTrue("style = MaterialTheme.typography.headlineSmall.copy" in out, out)
        assertTrue("imageVector = Icons.AutoMirrored.Filled.ArrowBack" in out, out)
        assertTrue("tint = Color.White" in out, out)
        assertTrue("IconButton(onClick = { goBack() })" in out, out)
        assertTrue("Icon(Icons.Default.Grid3x3, contentDescription = \"Grid\")" in out, out)
    }

    @Test
    fun resource_image_renders_as_placeholder_and_preserves_painter_expressions() {
        val screen = parseOne(
            """
            Image(
                painter = painterResource(R.drawable.hero),
                contentDescription = "Hero",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Crop,
            )
            """.trimIndent(),
        )
        val image = screen.children.single() as Node.Image
        assertEquals("painterResource(R.drawable.hero)", image.painterExpression)
        assertEquals("ContentScale.Crop", image.contentScaleExpression)
        assertEquals("Hero", image.contentDescription)
    }

    @Test
    fun top_app_bar_colors_expression_does_not_hide_its_title() {
        val screen = parseOne(
            """
            TopAppBar(
                title = { Text("Express") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
            """.trimIndent(),
        )
        val bar = screen.children.single() as Node.TopAppBar
        assertEquals("Express", (bar.title as Node.Text).text)
        assertTrue("TopAppBarDefaults.topAppBarColors" in bar.colorsExpression)
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
        assertNull(DesignParser.parse(file))
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
        assertTrue(box.modifier.isEmpty(), "canonical scope padding must stay implicit, found ${box.modifier}")
        val raw = box.children.single() as Node.RawCode
        assertEquals("CustomThing()", raw.code)
        // Regeneration re-imposes canonical padding(innerPadding) on the direct child.
        val out = CodeGen.generate(Node.Composable("s", children = listOf(scaffold)))
        assertTrue("Scaffold { innerPadding ->" in out, out)
        assertTrue("Box(modifier = Modifier.padding(innerPadding))" in out, out)
    }

    @Test
    fun scaffold_padding_preserves_its_modifier_order_and_fill_preview() {
        val screen = parseOne(
            """
            Scaffold(
                topBar = { Text("Wallet") },
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Wallet content")
                }
            }
            """.trimIndent(),
        )
        val scaffold = screen.children.single() as Node.Scaffold
        val box = scaffold.children.single() as Node.Box
        assertEquals(composer.model.BoxAlignment.Center, box.contentAlignment)
        assertEquals(
            listOf(ModifierSpec.FillMaxSize(), ModifierSpec.ScaffoldPadding("padding")),
            box.modifier,
        )

        val out = CodeGen.generate(Node.Composable("s", children = listOf(scaffold)))
        val fillAt = out.indexOf(".fillMaxSize()")
        val paddingAt = out.indexOf(".padding(padding)")
        assertTrue(fillAt >= 0 && paddingAt > fillAt, out)
        assertEquals(1, Regex("\\.padding\\(padding\\)").findAll(out).count(), out)
    }

    @Test
    fun scaffold_preserves_authored_param_and_source_arguments() {
        val file =
            """
            import androidx.compose.runtime.Composable

            @Composable
            fun Screen() {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                ) { padding ->
                    Text("x", modifier = Modifier.padding(padding))
                }
            }
            """.trimIndent()
        val parsed = assertNotNull(DesignParser.parse(file))
        val scaffold = (parsed.artboard.composables.single() as Node.Composable).children.single() as Node.Scaffold
        assertEquals("padding", scaffold.contentParameter)
        assertEquals("MaterialTheme.colorScheme.background", scaffold.sourceArguments["containerColor"])
        val out = CodeGen.generate(Node.Composable("s", children = listOf(scaffold)))
        assertTrue("containerColor = MaterialTheme.colorScheme.background" in out, out)
        assertTrue(") { padding ->" in out, out)
    }

    @Test
    fun unsupported_lambda_wrappers_expose_children_and_round_trip_source() {
        val screen = parseOne(
            """
            LazyColumn(state = listState) {
                items(cards) { card ->
                    AnimatedVisibility(visible = card.visible) {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        ) {
                            Text(card.title)
                        }
                    }
                }
            }
            """.trimIndent(),
        )
        val lazy = screen.children.single() as Node.SourceContainer
        assertEquals("LazyColumn", lazy.name)
        val items = lazy.children.single() as Node.SourceContainer
        val visibility = items.children.single() as Node.SourceContainer
        val card = visibility.children.single() as Node.SourceContainer
        assertTrue(card.children.single() is Node.Text)

        val out = CodeGen.generate(Node.Composable("s", children = listOf(lazy)))
        assertTrue("LazyColumn(state = listState)" in out, out)
        assertTrue("items(cards) { card ->" in out, out)
        assertTrue("AnimatedVisibility(visible = card.visible)" in out, out)
        assertTrue("shape = RoundedCornerShape(24.dp)" in out, out)
        assertTrue("Text(card.title)" in out, out)
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
        // Parameter-backed Text is renderable and retains its exact expression.
        val screen = parsed.artboard.composables.last() as composer.model.Node.Composable
        val dynamic = screen.children.first() as composer.model.Node.Text
        assertEquals("label", dynamic.text)
        assertEquals("label", dynamic.textExpression)
        assertTrue((screen.children.last() as composer.model.Node.Text).text == "static")
    }

    @Test
    fun named_interpolated_text_and_parameter_modifier_are_renderable() {
        val screen = parseOne(
            """
            Text(
                text = "Hello ${'$'}name!",
                modifier = modifier
            )
            """.trimIndent(),
        )
        val text = screen.children.single() as Node.Text
        assertEquals("Hello name!", text.text)
        assertEquals("\"Hello ${'$'}name!\"", text.textExpression)
        assertEquals(listOf(composer.model.ModifierSpec.External("modifier")), text.modifier)
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
