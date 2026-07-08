package composer.codegen

import composer.model.ModifierSpec
import composer.model.ModifierSpec.Background
import composer.model.ModifierSpec.FillMaxSize
import composer.model.ModifierSpec.FillMaxWidth
import composer.model.ModifierSpec.Padding
import composer.model.ModifierSpec.Size
import composer.model.Node
import composer.model.TextAlignment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CodeGenTest {

    // ---- Leaf nodes -------------------------------------------------------

    @Test
    fun text_without_modifier_has_no_modifier_arg() {
        val code = CodeGen.generate(Node.Text("t", "Hi"))
        assertTrue("    Text(\"Hi\")\n" in code, code)
        assertTrue("import androidx.compose.material3.Text" in code, code)
        // No modifier used -> no Modifier import.
        assertTrue("androidx.compose.ui.Modifier" !in code, code)
    }

    @Test
    fun instances_call_the_registered_composables_function() {
        val tree = Node.Artboard(
            "art",
            composables = listOf(
                Node.Composable("card", children = listOf(Node.Text("t", "Hello", listOf(Padding(12))))),
                Node.Composable(
                    "home",
                    children = listOf(
                        Node.Instance("i1", "card"),
                        Node.Instance("i2", "card", listOf(Padding(4))),
                    ),
                ),
            ),
            layerNames = mapOf("card" to "info card", "home" to "Home"),
            componentIds = listOf("card"),
        )
        val code = CodeGen.generate(tree)
        // the composable's own function is the component — no separate extraction
        assertTrue("fun InfoCard() {" in code, code)
        // bare instance = bare call
        assertTrue("    InfoCard()\n" in code, code)
        // instance with modifiers wraps the call in an explicit Box (a composable
        // body has no root node to thread a modifier parameter into)
        assertTrue("Box(modifier = Modifier.padding(4.dp)) {\n        InfoCard()\n    }" in code, code)
        // the card's padding lives once, inside InfoCard
        assertTrue(code.indexOf("padding(12.dp)") == code.lastIndexOf("padding(12.dp)"), code)
    }

    @Test
    fun dangling_instance_emits_comment_not_broken_code() {
        val tree = Node.Artboard(
            "art",
            composables = listOf(Node.Composable("s1", children = listOf(Node.Instance("i1", "gone")))),
        )
        val code = CodeGen.generate(tree)
        assertTrue("// Missing component: gone" in code, code)
    }

    @Test
    fun theme_token_colors_emit_colorscheme_references() {
        val primary = composer.model.ThemeColorRef.token("primary")!!
        val onPrimary = composer.model.ThemeColorRef.token("onPrimary")!!
        val code = CodeGen.generate(
            Node.Column(
                "c",
                children = listOf(
                    Node.Text("t", "Hi", color = onPrimary),
                    Node.Box("b", modifier = listOf(Background(primary, corner = 8))),
                ),
            ),
        )
        assertTrue("color = MaterialTheme.colorScheme.onPrimary" in code, code)
        assertTrue("background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))" in code, code)
        assertTrue("import androidx.compose.material3.MaterialTheme" in code, code)
    }

    @Test
    fun gradient_background_emits_brush_with_all_stops() {
        val code = CodeGen.generate(
            Node.Box(
                "b",
                modifier = listOf(
                    Background(
                        0xFF000000, corner = 12,
                        colors = listOf(0xFF2196F3, 0xFF9C27B0, 0xFFFF5722),
                        direction = composer.model.GradientDirection.Horizontal,
                    ),
                ),
            ),
        )
        assertTrue("background(Brush.horizontalGradient(listOf(Color(0xFF2196F3), Color(0xFF9C27B0), Color(0xFFFF5722))), RoundedCornerShape(12.dp))" in code, code)
        assertTrue("import androidx.compose.ui.graphics.Brush" in code, code)
    }

    @Test
    fun single_stop_gradient_falls_back_to_solid() {
        val code = CodeGen.generate(
            Node.Box("b", modifier = listOf(Background(0xFF2196F3, colors = listOf(0xFF9C27B0)))),
        )
        assertTrue("background(Color(0xFF2196F3))" in code, code) // < 2 stops → solid
        assertTrue("Brush" !in code, code)
    }

    @Test
    fun percent_corner_emits_percent_shape_overload() {
        val code = CodeGen.generate(
            Node.Box(
                "b",
                modifier = listOf(
                    Background(0xFF2196F3, corner = 50, cornerUnit = composer.model.CornerUnit.Percent),
                    ModifierSpec.Clip(50, composer.model.CornerUnit.Percent),
                ),
            ),
        )
        assertTrue("background(Color(0xFF2196F3), RoundedCornerShape(50))" in code, code)
        assertTrue("clip(RoundedCornerShape(50))" in code, code)
        assertTrue("RoundedCornerShape(50.dp)" !in code, code) // percent, not dp
    }

    @Test
    fun drop_shadow_emits_compose19_api() {
        val code = CodeGen.generate(
            Node.Box("b", modifier = listOf(ModifierSpec.DropShadow(radius = 10, color = 0x40000000, offsetX = 4, offsetY = 4, spread = 6, corner = 20))),
        )
        assertTrue("dropShadow(RoundedCornerShape(20.dp), Shadow(radius = 10.dp, color = Color(0x40000000), spread = 6.dp, offset = DpOffset(4.dp, 4.dp)))" in code, code)
        assertTrue("import androidx.compose.ui.draw.dropShadow" in code, code)
        assertTrue("import androidx.compose.ui.graphics.shadow.Shadow" in code, code)
        assertTrue("import androidx.compose.ui.unit.DpOffset" in code, code)
    }

    @Test
    fun shadow_chain_order_is_preserved_as_authored() {
        // Order is meaning in Compose (a dropShadow after background paints over
        // the fill) — codegen must emit the chain exactly as the user built it.
        val code = CodeGen.generate(
            Node.Box(
                "b",
                modifier = listOf(
                    Background(0xFFFFFFFF, corner = 12),
                    ModifierSpec.DropShadow(radius = 8, corner = 12),
                ),
            ),
        )
        val shadowAt = code.indexOf(".dropShadow(")
        val backgroundAt = code.indexOf(".background(")
        assertTrue(backgroundAt in 0 until shadowAt, code)
    }

    @Test
    fun inner_shadow_omits_default_args_and_uses_rectangle_shape() {
        val code = CodeGen.generate(
            Node.Box("b", modifier = listOf(ModifierSpec.InnerShadow(radius = 8, color = 0x40000000, offsetX = 0, offsetY = 0, spread = 0, corner = 0))),
        )
        assertTrue("innerShadow(RectangleShape, Shadow(radius = 8.dp, color = Color(0x40000000)))" in code, code)
        assertTrue("import androidx.compose.ui.draw.innerShadow" in code, code)
        assertTrue("import androidx.compose.ui.graphics.RectangleShape" in code, code)
        assertTrue("DpOffset" !in code, code) // zero offset/spread omitted
    }

    @Test
    fun text_style_emits_only_non_default_args() {
        val plain = CodeGen.generate(Node.Text("t", "hi"))
        assertTrue("Text(\"hi\")" in plain, plain) // nothing extra at defaults

        val styled = CodeGen.generate(
            Node.Text(
                "t", "hi",
                fontSize = 20,
                fontWeight = composer.model.TextWeight.Bold,
                fontFamily = composer.model.TextFontFamily.Monospace,
                color = 0xFFFF0000,
            ),
        )
        assertTrue("color = Color(0xFFFF0000)" in styled, styled)
        assertTrue("fontSize = 20.sp" in styled, styled)
        assertTrue("fontWeight = FontWeight.Bold" in styled, styled)
        assertTrue("fontFamily = FontFamily.Monospace" in styled, styled)
    }

    @Test
    fun text_with_padding_emits_modifier_arg_and_imports() {
        val code = CodeGen.generate(Node.Text("t", "Hi", listOf(Padding(8))))
        assertTrue("Text(\"Hi\", modifier = Modifier.padding(8.dp))" in code, code)
        assertTrue("import androidx.compose.foundation.layout.padding" in code, code)
        assertTrue("import androidx.compose.ui.unit.dp" in code, code)
        assertTrue("import androidx.compose.ui.Modifier" in code, code)
    }

    @Test
    fun button_wraps_label_in_text() {
        val code = CodeGen.generate(Node.Button("b", "Go"))
        assertTrue("Button(onClick = {}) {" in code, code)
        assertTrue("        Text(\"Go\")" in code, code)
    }

    @Test
    fun spacer_without_modifier_falls_back_to_bare_Modifier() {
        val code = CodeGen.generate(Node.Spacer("s"))
        assertTrue("Spacer(modifier = Modifier)" in code, code)
        assertTrue("import androidx.compose.ui.Modifier" in code, code)
    }

    // ---- Modifier chain ---------------------------------------------------

    @Test
    fun long_call_wraps_args_one_per_line() {
        // A Text with several style args exceeds 100 cols on one line → should wrap.
        val code = CodeGen.generate(
            Node.Text(
                "t", "A reasonably long label here",
                fontSize = 20,
                fontWeight = composer.model.TextWeight.SemiBold,
                fontFamily = composer.model.TextFontFamily.Monospace,
                color = 0xFF112233,
            ),
        )
        assertTrue("Text(\n" in code, code) // call broke across lines
        assertTrue("    fontSize = 20.sp,\n" in code, code) // one arg per line, trailing comma
    }

    @Test
    fun short_call_stays_on_one_line() {
        // fontSize 12 → auto lineHeight (12 × 1.2 = 14.4 → 14)
        val code = CodeGen.generate(Node.Text("t", "hi", fontSize = 12))
        assertTrue("Text(\"hi\", fontSize = 12.sp, lineHeight = 14.sp)" in code, code)
    }

    @Test
    fun text_line_height_defaults_to_font_size_times_1_2() {
        // auto (lineHeight = 0) derives from fontSize
        assertTrue("lineHeight = 24.sp" in CodeGen.generate(Node.Text("t", "x", fontSize = 20)), "auto")
        // explicit override wins
        assertTrue("lineHeight = 30.sp" in CodeGen.generate(Node.Text("t", "x", fontSize = 20, lineHeight = 30)), "explicit")
        // no fontSize, no explicit → no lineHeight
        assertTrue("lineHeight" !in CodeGen.generate(Node.Text("t", "x")), "none")
    }

    @Test
    fun modifier_order_is_preserved() {
        val a = CodeGen.generate(Node.Box("x", modifier = listOf(FillMaxWidth, Padding(4))))
        val b = CodeGen.generate(Node.Box("x", modifier = listOf(Padding(4), FillMaxWidth)))
        // 2+ modifiers wrap one-per-line; assert relative order is preserved.
        assertTrue(a.indexOf(".fillMaxWidth()") < a.indexOf(".padding(4.dp)"), a)
        assertTrue(b.indexOf(".padding(4.dp)") < b.indexOf(".fillMaxWidth()"), b)
    }

    @Test
    fun padding_modes_emit_the_right_form() {
        val all = CodeGen.generate(Node.Box("x", modifier = listOf(ModifierSpec.Padding(16))))
        assertTrue("padding(16.dp)" in all, all)
        val sym = CodeGen.generate(Node.Box("x", modifier = listOf(ModifierSpec.Padding(horizontal = 12, vertical = 6, mode = composer.model.PaddingMode.Symmetric))))
        assertTrue("padding(horizontal = 12.dp, vertical = 6.dp)" in sym, sym)
        val sides = CodeGen.generate(Node.Box("x", modifier = listOf(ModifierSpec.Padding(start = 1, top = 2, end = 3, bottom = 4, mode = composer.model.PaddingMode.Sides))))
        assertTrue("padding(start = 1.dp, top = 2.dp, end = 3.dp, bottom = 4.dp)" in sides, sides)
    }

    @Test
    fun image_with_url_emits_coil_async_image() {
        val code = CodeGen.generate(Node.Image("i", url = "https://x.com/a.png", contentDescription = "Pic"))
        assertTrue("AsyncImage(model = \"https://x.com/a.png\", contentDescription = \"Pic\")" in code, code)
        assertTrue("import coil3.compose.AsyncImage" in code, code)
        // no url → ColorPainter placeholder
        val placeholder = CodeGen.generate(Node.Image("i"))
        assertTrue("ColorPainter" in placeholder, placeholder)
        assertTrue("AsyncImage" !in placeholder, placeholder)
    }

    @Test
    fun dialog_emits_dialog_with_surface() {
        val code = CodeGen.generate(Node.Dialog("d", children = listOf(Node.Text("t", "Hi"))))
        assertTrue("Dialog(onDismissRequest = {}) {" in code, code)
        assertTrue("Surface(shape = RoundedCornerShape(16.dp)) {" in code, code)
        assertTrue("import androidx.compose.ui.window.Dialog" in code, code)
        assertTrue("Text(\"Hi\")" in code, code)
    }

    @Test
    fun bottom_sheet_emits_modal_with_optin() {
        val code = CodeGen.generate(Node.BottomSheet("s", children = listOf(Node.Text("t", "Hi"))))
        assertTrue("ModalBottomSheet(onDismissRequest = {}) {" in code, code)
        assertTrue("import androidx.compose.material3.ModalBottomSheet" in code, code)
        assertTrue("@OptIn(ExperimentalMaterial3Api::class)" in code, code)
    }

    @Test
    fun clip_modifier_emits_rounded_corner() {
        val code = CodeGen.generate(Node.Box("x", modifier = listOf(ModifierSpec.Clip(12))))
        assertTrue("Modifier.clip(RoundedCornerShape(12.dp))" in code, code)
        assertTrue("import androidx.compose.ui.draw.clip" in code, code)
    }

    @Test
    fun width_and_height_modifiers_emit_per_axis() {
        val w = CodeGen.generate(Node.Box("x", modifier = listOf(ModifierSpec.Width(240))))
        assertTrue("Modifier.width(240.dp)" in w, w)
        assertTrue("import androidx.compose.foundation.layout.width" in w, w)
        val h = CodeGen.generate(Node.Box("x", modifier = listOf(ModifierSpec.Height(48))))
        assertTrue("Modifier.height(48.dp)" in h, h)
        assertTrue("import androidx.compose.foundation.layout.height" in h, h)
    }

    @Test
    fun text_align_emits_only_when_non_default() {
        val start = CodeGen.generate(Node.Text("t", "Hi", textAlign = TextAlignment.Start))
        assertTrue("textAlign" !in start, start)
        val center = CodeGen.generate(Node.Text("t", "Hi", textAlign = TextAlignment.Center))
        assertTrue("textAlign = TextAlign.Center" in center, center)
        assertTrue("import androidx.compose.ui.text.style.TextAlign" in center, center)
    }

    @Test
    fun alpha_modifier_emits() {
        val code = CodeGen.generate(Node.Box("x", modifier = listOf(ModifierSpec.Alpha(0.5f))))
        assertTrue("Modifier.alpha(0.5f)" in code, code)
        assertTrue("import androidx.compose.ui.draw.alpha" in code, code)
    }

    @Test
    fun border_modifier_emits_with_and_without_corner() {
        val sharp = CodeGen.generate(Node.Box("x", modifier = listOf(ModifierSpec.Border(2, 0xFF111827))))
        assertTrue("Modifier.border(2.dp, Color(0xFF111827))" in sharp, sharp)
        assertTrue("import androidx.compose.foundation.border" in sharp, sharp)
        val rounded = CodeGen.generate(Node.Box("x", modifier = listOf(ModifierSpec.Border(1, 0xFF000000, 8))))
        assertTrue("Modifier.border(1.dp, Color(0xFF000000), RoundedCornerShape(8.dp))" in rounded, rounded)
    }

    @Test
    fun rotate_modifier_emits() {
        val code = CodeGen.generate(Node.Box("x", modifier = listOf(ModifierSpec.Rotate(45f))))
        assertTrue("Modifier.rotate(45.0f)" in code, code)
        assertTrue("import androidx.compose.ui.draw.rotate" in code, code)
    }

    @Test
    fun scale_modifier_emits_uniform_and_per_axis() {
        val uniform = CodeGen.generate(Node.Box("x", modifier = listOf(ModifierSpec.Scale(1.5f, 1.5f))))
        assertTrue("Modifier.scale(1.5f)" in uniform, uniform)
        assertTrue("import androidx.compose.ui.draw.scale" in uniform, uniform)
        val perAxis = CodeGen.generate(Node.Box("x", modifier = listOf(ModifierSpec.Scale(1.5f, 2.0f))))
        assertTrue("Modifier.scale(1.5f, 2.0f)" in perAxis, perAxis)
    }

    @Test
    fun duplicate_modifiers_all_emit_in_order() {
        val code = CodeGen.generate(
            Node.Box(
                "x",
                modifier = listOf(
                    ModifierSpec.DropShadow(radius = 4),
                    ModifierSpec.DropShadow(radius = 16, offsetY = 8),
                ),
            ),
        )
        val first = code.indexOf("dropShadow(")
        val second = code.indexOf("dropShadow(", first + 1)
        assertTrue(first >= 0 && second > first, code)
        assertTrue("radius = 4.dp" in code, code)
        assertTrue("radius = 16.dp" in code, code)
    }

    @Test
    fun aspect_ratio_modifier_emits() {
        val code = CodeGen.generate(Node.Box("x", modifier = listOf(ModifierSpec.AspectRatio(16, 9))))
        assertTrue("Modifier.aspectRatio(16f / 9f)" in code, code)
        assertTrue("import androidx.compose.foundation.layout.aspectRatio" in code, code)
    }

    @Test
    fun background_color_is_zero_padded_uppercase_hex() {
        val opaqueGray = CodeGen.generate(Node.Box("x", modifier = listOf(Background(0xFFE0E0E0))))
        assertTrue("background(Color(0xFFE0E0E0))" in opaqueGray, opaqueGray)

        // Leading zeros must be preserved (regression guard for padStart).
        val mostlyTransparent = CodeGen.generate(Node.Box("x", modifier = listOf(Background(0x00FF00FF))))
        assertTrue("background(Color(0x00FF00FF))" in mostlyTransparent, mostlyTransparent)
    }

    @Test
    fun background_corner_emits_rounded_shape() {
        val sharp = CodeGen.generate(Node.Box("x", modifier = listOf(Background(0xFF2196F3, 0))))
        assertTrue("background(Color(0xFF2196F3))" in sharp, sharp)
        assertTrue("RoundedCornerShape" !in sharp, sharp)

        val rounded = CodeGen.generate(Node.Box("x", modifier = listOf(Background(0xFF2196F3, 12))))
        assertTrue("background(Color(0xFF2196F3), RoundedCornerShape(12.dp))" in rounded, rounded)
    }

    // ---- Structure --------------------------------------------------------

    @Test
    fun nested_children_are_indented_by_four_spaces_per_level() {
        val tree = Node.Column(
            "root",
            children = listOf(Node.Row("r", children = listOf(Node.Text("t", "deep")))),
        )
        val code = CodeGen.generate(tree)
        // Column body at 1 level, Row body at 2, Text at 3 (12 spaces).
        assertTrue("            Text(\"deep\")" in code, code)
    }

    @Test
    fun imports_are_sorted_and_deduplicated() {
        val code = CodeGen.generate(
            Node.Column("c", children = listOf(Node.Text("t1", "a"), Node.Text("t2", "b"))),
        )
        val imports = code.lines().filter { it.startsWith("import ") }
        assertEquals(imports.sorted(), imports, "imports must be sorted")
        assertEquals(imports.toSet().size, imports.size, "imports must be deduplicated")
    }

    // ---- New components ---------------------------------------------------

    @Test
    fun image_uses_color_painter_and_null_description_when_blank() {
        val code = CodeGen.generate(Node.Image("img", contentDescription = "", placeholderColor = 0xFFCFD4DC))
        assertTrue("Image(painter = ColorPainter(Color(0xFFCFD4DC)), contentDescription = null)" in code, code)
        assertTrue("import androidx.compose.ui.graphics.painter.ColorPainter" in code, code)
    }

    @Test
    fun image_quotes_non_blank_description() {
        val code = CodeGen.generate(Node.Image("img", contentDescription = "avatar"))
        assertTrue("contentDescription = \"avatar\"" in code, code)
    }

    @Test
    fun divider_emits_horizontal_divider() {
        assertTrue("HorizontalDivider()" in CodeGen.generate(Node.Divider("d")), "bare")
        val withMod = CodeGen.generate(Node.Divider("d", listOf(Padding(4))))
        assertTrue("HorizontalDivider(modifier = Modifier.padding(4.dp))" in withMod, withMod)
    }

    @Test
    fun card_is_a_container() {
        val code = CodeGen.generate(Node.Card("c", children = listOf(Node.Text("t", "hi"))))
        assertTrue("Card {" in code, code)
        assertTrue("        Text(\"hi\")" in code, code)
        assertTrue("import androidx.compose.material3.Card" in code, code)
    }

    @Test
    fun scaffold_applies_inner_padding_to_content_without_wrapper() {
        val code = CodeGen.generate(Node.Scaffold("s", children = listOf(Node.Text("t", "hi"))))
        // content gets the Scaffold's innerPadding directly — no separate wrapper node
        assertTrue("Scaffold { innerPadding ->" in code, code)
        assertTrue("Text(\"hi\", modifier = Modifier.padding(innerPadding))" in code, code)
        assertTrue("Column(modifier = Modifier.padding(innerPadding))" !in code, code)
    }

    @Test
    fun scaffold_inner_padding_prepends_to_content_columns_own_chain() {
        val code = CodeGen.generate(
            Node.Scaffold(
                "s",
                children = listOf(
                    Node.Column("c", children = listOf(Node.Text("t", "hi")), modifier = listOf(ModifierSpec.Padding(8))),
                ),
            ),
        )
        // 2 modifiers wrap; innerPadding is prepended before the Column's own padding.
        assertTrue(".padding(innerPadding)" in code && ".padding(8.dp)" in code, code)
        assertTrue(code.indexOf(".padding(innerPadding)") < code.indexOf(".padding(8.dp)"), code)
    }

    @Test
    fun empty_scaffold_has_no_inner_padding_param() {
        val code = CodeGen.generate(Node.Scaffold("s"))
        assertTrue("Scaffold {" in code, code)
        assertTrue("innerPadding" !in code, code)
    }

    @Test
    fun fab_emits_floating_action_button_with_content() {
        val code = CodeGen.generate(Node.Fab("f", children = listOf(Node.Icon("i", composer.model.IconKind.Add))))
        assertTrue("FloatingActionButton(onClick = {}) {" in code, code)
        assertTrue("Icon(Icons.Default.Add, contentDescription = null)" in code, code)
        assertTrue("import androidx.compose.material3.FloatingActionButton" in code, code)
    }

    @Test
    fun scaffold_emits_slots_as_lambdas() {
        // Legacy shape (pre-Slot saves): direct content in the slot fields.
        val code = CodeGen.generate(
            Node.Scaffold(
                "s",
                children = listOf(Node.Text("c", "content")),
                topBar = Node.Text("tb", "Title"),
                fab = Node.Button("f", "FAB"),
            ),
        )
        assertTrue("topBar = {" in code, code)
        assertTrue("floatingActionButton = {" in code, code)
        assertTrue("bottomBar" !in code, code) // null slot omitted
    }

    @Test
    fun scaffold_slot_nodes_emit_children_and_empty_slots_are_omitted() {
        val code = CodeGen.generate(
            Node.Scaffold(
                "s",
                children = listOf(Node.Text("c", "content")),
                topBar = Node.Slot("s-topBar", "topBar", children = listOf(Node.Text("tb", "Title"))),
                bottomBar = Node.Slot("s-bottomBar", "bottomBar"), // empty → omitted
                fab = Node.Slot("s-fab", "fab", children = listOf(Node.Button("f", "FAB"))),
            ),
        )
        assertTrue("topBar = {" in code, code)
        assertTrue("Text(\"Title\")" in code, code)
        assertTrue("floatingActionButton = {" in code, code)
        assertTrue("bottomBar" !in code, code) // empty Slot omitted, like null
        assertLexicallyValid(code)
    }

    @Test
    fun scaffold_with_only_empty_slots_emits_bare_call() {
        val code = CodeGen.generate(
            Node.Scaffold(
                "s",
                topBar = Node.Slot("s-topBar", "topBar"),
                bottomBar = Node.Slot("s-bottomBar", "bottomBar"),
                fab = Node.Slot("s-fab", "fab"),
            ),
        )
        assertTrue("Scaffold {" in code, code) // no args, no unused innerPadding
        assertLexicallyValid(code)
    }

    // ---- Layout arrangement / alignment -----------------------------------

    @Test
    fun column_emits_arrangement_and_alignment_only_when_non_default() {
        val default = CodeGen.generate(Node.Column("c", children = listOf(Node.Text("t", "x"))))
        assertTrue("Column {" in default, default) // no extra args at defaults

        val custom = CodeGen.generate(
            Node.Column(
                "c",
                children = listOf(Node.Text("t", "x")),
                verticalArrangement = composer.model.VArrangement.SpaceBetween,
                horizontalAlignment = composer.model.HAlignment.Center,
            ),
        )
        // (Long header wraps one arg per line.)
        assertTrue("verticalArrangement = Arrangement.SpaceBetween" in custom, custom)
        assertTrue("horizontalAlignment = Alignment.CenterHorizontally" in custom, custom)
    }

    @Test
    fun spacing_emits_arrangement_spaced_by() {
        val col = CodeGen.generate(Node.Column("c", children = listOf(Node.Text("t", "x")), spacing = 8))
        assertTrue("verticalArrangement = Arrangement.spacedBy(8.dp)" in col, col)
        val row = CodeGen.generate(Node.Row("r", spacing = 12))
        assertTrue("horizontalArrangement = Arrangement.spacedBy(12.dp)" in row, row)
        // spacing overrides the enum arrangement
        val both = CodeGen.generate(Node.Column("c", spacing = 4, verticalArrangement = composer.model.VArrangement.Center))
        assertTrue("Arrangement.spacedBy(4.dp)" in both && "Arrangement.Center" !in both, both)
    }

    @Test
    fun row_and_box_emit_alignment() {
        val row = CodeGen.generate(
            Node.Row("r", horizontalArrangement = composer.model.HArrangement.Center, verticalAlignment = composer.model.VAlignment.Center),
        )
        assertTrue("horizontalArrangement = Arrangement.Center" in row, row)
        assertTrue("verticalAlignment = Alignment.CenterVertically" in row, row)

        val box = CodeGen.generate(Node.Box("b", contentAlignment = composer.model.BoxAlignment.Center))
        assertTrue("Box(contentAlignment = Alignment.Center) {" in box, box)
    }

    // ---- Weight (scope-aware) ---------------------------------------------

    @Test
    fun weight_emits_inside_row_or_column() {
        val tree = Node.Row("r", children = listOf(Node.Text("t", "hi", listOf(ModifierSpec.Weight(1f)))))
        assertTrue("Text(\"hi\", modifier = Modifier.weight(1.0f))" in CodeGen.generate(tree), CodeGen.generate(tree))
    }

    @Test
    fun weight_is_dropped_outside_linear_parents() {
        // A weight on the root (no linear parent) must not be emitted.
        val code = CodeGen.generate(Node.Box("b", modifier = listOf(ModifierSpec.Weight(1f))))
        assertTrue("weight" !in code, code)
        // Inside a Box, weight on a child is also dropped.
        val inBox = CodeGen.generate(Node.Box("b", children = listOf(Node.Text("t", "hi", listOf(ModifierSpec.Weight(1f))))))
        assertTrue("weight" !in inBox, inBox)
    }

    @Test
    fun text_field_emits_outlined_with_hoisted_value_and_label() {
        val code = CodeGen.generate(Node.TextField("t", value = "hi", placeholder = "Name"))
        assertTrue("var state1 by remember { mutableStateOf(\"hi\") }" in code, code)
        assertTrue("OutlinedTextField(value = state1, onValueChange = { state1 = it }, label = { Text(\"Name\") })" in code, code)
        assertTrue("import androidx.compose.material3.OutlinedTextField" in code, code)
    }

    @Test
    fun icon_and_icon_button_emit_with_imports() {
        val icon = CodeGen.generate(Node.Icon("i", composer.model.IconKind.Search, "find"))
        assertTrue("Icon(Icons.Default.Search, contentDescription = \"find\")" in icon, icon)
        assertTrue("import androidx.compose.material.icons.filled.Search" in icon, icon)

        val button = CodeGen.generate(Node.IconButton("b", composer.model.IconKind.Menu))
        assertTrue("IconButton(onClick = {}) {" in button, button)
        assertTrue("Icon(Icons.Default.Menu, contentDescription = null)" in button, button)
    }

    // ---- Material3 stateful components ------------------------------------

    @Test
    fun stateful_components_hoist_remembered_state() {
        // Interactive widgets must hoist real state (not emit no-op callbacks), so the
        // generated code actually responds to input when dropped into a project.
        val sw = CodeGen.generate(Node.Switch("s", true))
        assertTrue("var state1 by remember { mutableStateOf(true) }" in sw, sw)
        assertTrue("Switch(checked = state1, onCheckedChange = { state1 = it })" in sw, sw)
        assertTrue("import androidx.compose.runtime.mutableStateOf" in sw, sw)
        assertTrue("import androidx.compose.runtime.getValue" in sw, sw)

        val cb = CodeGen.generate(Node.Checkbox("c", false))
        assertTrue("var state1 by remember { mutableStateOf(false) }" in cb, cb)
        assertTrue("Checkbox(checked = state1, onCheckedChange = { state1 = it })" in cb, cb)

        val rb = CodeGen.generate(Node.RadioButton("r", true))
        assertTrue("var state1 by remember { mutableStateOf(true) }" in rb, rb)
        assertTrue("RadioButton(selected = state1, onClick = { state1 = !state1 })" in rb, rb)

        val sl = CodeGen.generate(Node.Slider("sl", 0.5f))
        assertTrue("var state1 by remember { mutableStateOf(0.5f) }" in sl, sl)
        assertTrue("Slider(value = state1, onValueChange = { state1 = it })" in sl, sl)
    }

    @Test
    fun hoisted_state_vars_are_uniquely_numbered() {
        // Two switches in one scope must not both declare `state1` (a compile error).
        val code = CodeGen.generate(Node.Column("c", children = listOf(Node.Switch("a", true), Node.Switch("b", false))))
        assertTrue("var state1 by remember" in code, code)
        assertTrue("var state2 by remember" in code, code)
        assertLexicallyValid(code)
    }

    @Test
    fun progress_indicators_emit() {
        assertTrue("CircularProgressIndicator()" in CodeGen.generate(Node.CircularProgress("p")))
        assertTrue("LinearProgressIndicator()" in CodeGen.generate(Node.LinearProgress("p")))
    }

    @Test
    fun top_app_bar_emits_slots_and_optin() {
        val code = CodeGen.generate(
            Node.TopAppBar(
                "tb",
                title = Node.Text("t", "Title"),
                navigationIcon = Node.Button("n", "Menu"),
                actions = listOf(Node.Button("a", "A")),
            ),
        )
        assertTrue("@OptIn(ExperimentalMaterial3Api::class)" in code, code)
        assertTrue("CenterAlignedTopAppBar(" in code, code)
        assertTrue("title = {" in code, code)
        assertTrue("navigationIcon = {" in code, code)
        assertTrue("actions = {" in code, code)
    }

    @Test
    fun button_variants_emit_the_right_composable() {
        fun gen(v: composer.model.ButtonVariant) = CodeGen.generate(Node.Button("b", "Go", variant = v))
        assertTrue("OutlinedButton(onClick = {}) {" in gen(composer.model.ButtonVariant.Outlined), "outlined")
        assertTrue("import androidx.compose.material3.OutlinedButton" in gen(composer.model.ButtonVariant.Outlined), "import")
        assertTrue("TextButton(onClick = {}) {" in gen(composer.model.ButtonVariant.Text), "text")
        assertTrue("ElevatedButton(onClick = {}) {" in gen(composer.model.ButtonVariant.Elevated), "elevated")
        assertTrue("FilledTonalButton(onClick = {}) {" in gen(composer.model.ButtonVariant.FilledTonal), "tonal")
    }

    @Test
    fun top_app_bar_variants_emit_the_right_composable() {
        fun gen(v: composer.model.TopAppBarVariant) =
            CodeGen.generate(Node.TopAppBar("t", title = Node.Text("x", "T"), variant = v))
        assertTrue("MediumTopAppBar(" in gen(composer.model.TopAppBarVariant.Medium), "medium")
        assertTrue("LargeTopAppBar(" in gen(composer.model.TopAppBarVariant.Large), "large")
        assertTrue("TopAppBar(" in gen(composer.model.TopAppBarVariant.Small), "small")
        // still opted-in (experimental)
        assertTrue("@OptIn(ExperimentalMaterial3Api::class)" in gen(composer.model.TopAppBarVariant.Large), "optin")
    }

    // ---- Composable (function scope, no wrapper) ---------------------------

    @Test
    fun composable_emits_children_directly_into_the_function() {
        val code = CodeGen.generate(Node.Composable("root", children = listOf(Node.Text("t", "Hello world"))))
        assertTrue("fun Composable1() {\n    Text(\"Hello world\")\n}" in code, code)
        // No Box/Column wrapper from the screen itself.
        assertTrue("Box" !in code && "Column" !in code, code)
    }

    @Test
    fun composable_emits_multiple_top_level_children() {
        val code = CodeGen.generate(
            Node.Composable("root", children = listOf(Node.Text("t", "A"), Node.Button("b", "B"))),
        )
        assertTrue("    Text(\"A\")" in code, code)
        assertTrue("    Button(onClick = {}) {" in code, code)
    }

    // ---- Artboard: one function per screen, named from layer names ---------

    @Test
    fun artboard_emits_one_function_per_screen_named_from_layer_names() {
        val ab = Node.Artboard(
            id = "ab",
            composables = listOf(
                Node.Composable("s1", listOf(Node.Text("t1", "A"))),
                Node.Composable("s2", listOf(Node.Text("t2", "B"))),
            ),
            layerNames = mapOf("s1" to "login screen", "s2" to "2 factor / auth!"),
        )
        val code = CodeGen.generate(ab)
        assertTrue("fun LoginScreen() {" in code, code)      // PascalCase from free text
        assertTrue("fun Composable2FactorAuth() {" in code, code) // leading digit prefixed, punctuation dropped
        assertLexicallyValid(code)
    }

    @Test
    fun unnamed_screens_fall_back_to_indexed_names_and_duplicates_dedupe() {
        val ab = Node.Artboard(
            id = "ab",
            composables = listOf(
                Node.Composable("s1", listOf(Node.Text("t1", "A"))),
                Node.Composable("s2", listOf(Node.Text("t2", "B"))),
                Node.Composable("s3", listOf(Node.Text("t3", "C"))),
            ),
            layerNames = mapOf("s2" to "Home", "s3" to "Home"),
        )
        val code = CodeGen.generate(ab)
        assertTrue("fun Composable1() {" in code, code)
        assertTrue("fun Home() {" in code, code)
        assertTrue("fun Home2() {" in code, code)
        assertLexicallyValid(code)
    }

    @Test
    fun screen_named_app_theme_does_not_collide_with_the_theme_wrapper() {
        val ab = Node.Artboard(
            id = "ab",
            composables = listOf(Node.Composable("s1", listOf(Node.Text("t", "Hi")))),
            theme = composer.model.DesignTheme(dark = true),
            layerNames = mapOf("s1" to "App Theme"),
        )
        val code = CodeGen.generate(ab)
        assertTrue("fun AppTheme(colorScheme: ColorScheme = DarkColors, content: @Composable () -> Unit) {" in code, code)
        assertTrue("fun AppTheme2() {" in code, code)
        assertLexicallyValid(code)
    }

    // ---- Escaping & lexical validity --------------------------------------

    // A string exercising every character class that must be escaped inside a
    // Kotlin "double-quoted" literal: quote, tab, `$` (template), backslash, newline.
    private val tricky = "He said \"hi\"\tcost \$5 \\ path\nnext"

    @Test
    fun text_escapes_special_characters() {
        val code = CodeGen.generate(Node.Text("t", tricky))
        assertLexicallyValid(code)
        assertTrue("\\\"hi\\\"" in code, code)   // quotes escaped
        assertTrue("\\\$5" in code, code)         // dollar escaped (no template injection)
        assertTrue("path\\nnext" in code, code)   // newline escaped (literal \n)
        assertTrue("\\\\ path" in code, code)     // backslash escaped
        // The raw, unescaped forms must NOT survive.
        assertTrue("\"hi\"" !in code.substringAfter("Text("), code)
    }

    @Test
    fun dollar_sign_does_not_become_a_string_template() {
        // Regression: `Text("Price: $5")` used to emit an unresolved `$5` template.
        val code = CodeGen.generate(Node.Text("t", "Price: \$total"))
        assertLexicallyValid(code)
        assertTrue("Price: \\\$total" in code, code)
    }

    @Test
    fun user_strings_are_escaped_across_all_carriers() {
        // Every node property that carries free user text must be escaped.
        assertLexicallyValid(CodeGen.generate(Node.Button("b", tricky)))
        assertLexicallyValid(CodeGen.generate(Node.TextField("tf", value = tricky, placeholder = tricky)))
        assertLexicallyValid(CodeGen.generate(Node.Image("i", url = "https://x/a.png?q=\"a\"&b=\$c", contentDescription = tricky)))
        assertLexicallyValid(CodeGen.generate(Node.Icon("ic", composer.model.IconKind.Search, contentDescription = tricky)))
    }

    @Test
    fun custom_font_comment_is_single_line() {
        // A newline in the font name must not break the // comment onto a code line.
        val code = CodeGen.generate(Node.Text("t", "hi", customFont = "Weird\nName"))
        assertLexicallyValid(code)
        assertTrue("// Font \"Weird Name\"" in code, code)
    }

    // ---- RawCode (opaque preserved source) ---------------------------------

    @Test
    fun raw_code_emits_verbatim_reindented() {
        val raw = Node.RawCode("r", "if (loading) {\n    CircularProgressIndicator()\n}")
        val code = CodeGen.generate(Node.Composable("root", children = listOf(raw)))
        // Body indent is 1 (4 spaces); the captured text's own nesting is preserved on top.
        assertTrue("    if (loading) {\n        CircularProgressIndicator()\n    }\n" in code, code)
    }

    @Test
    fun raw_code_nested_in_container_indents_to_that_level() {
        val raw = Node.RawCode("r", "Foo()")
        val code = CodeGen.generate(Node.Composable("root", children = listOf(Node.Column("c", children = listOf(raw)))))
        assertTrue("        Foo()\n" in code, code) // indent 2 inside the Column
    }

    @Test
    fun raw_code_is_never_escaped() {
        // Dollar templates and escapes are CODE here, not string content — they
        // must survive byte-for-byte (the one deliberate esc()-exempt emission).
        val raw = Node.RawCode("r", "items.forEach { println(\"item: \$it\") }")
        val code = CodeGen.generate(Node.Composable("root", children = listOf(raw)))
        assertTrue("items.forEach { println(\"item: \$it\") }" in code, code)
        assertTrue("\\\$" !in code, code)
    }

    @Test
    fun raw_code_blank_lines_stay_blank() {
        val raw = Node.RawCode("r", "Foo()\n\nBar()")
        val code = CodeGen.generate(Node.Composable("root", children = listOf(raw)))
        assertTrue("    Foo()\n\n    Bar()\n" in code, code) // no trailing-space padding on the blank line
    }

    @Test
    fun raw_code_with_raw_string_keeps_original_indentation() {
        // Re-indenting lines inside a """ literal would change its content — such
        // text is captured with original indentation and emitted un-padded.
        val body = "    val s = \"\"\"\n  two spaces matter\n    \"\"\""
        val raw = Node.RawCode("r", body)
        val code = CodeGen.generate(Node.Composable("root", children = listOf(raw)))
        assertTrue(body in code, code)
    }

    @Test
    fun every_node_variant_generates_lexically_valid_source() {
        // A tree touching every Node type with adversarial text — the generated
        // source must stay lexically well-formed (balanced (){}[], closed strings).
        assertLexicallyValid(CodeGen.generate(kitchenSink()))
    }

    /**
     * Assert [code] is lexically valid Kotlin: balanced `()`/`{}`/`[]`, every
     * `"…"` literal closed with no raw newline inside, and no accidental string
     * template (`$name`/`${…}`) — this generator always escapes `$`, so any
     * survivor is an escaping bug. Line/block comments are skipped. This is a
     * lexical guard (the class of bug unescaped input causes), not a full type-check.
     */
    private fun assertLexicallyValid(code: String) {
        val opens = setOf('(', '{', '[')
        val close = mapOf(')' to '(', '}' to '{', ']' to '[')
        val stack = ArrayDeque<Char>()
        var i = 0
        while (i < code.length) {
            val c = code[i]
            when {
                c == '/' && i + 1 < code.length && code[i + 1] == '/' ->
                    while (i < code.length && code[i] != '\n') i++
                c == '/' && i + 1 < code.length && code[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < code.length && !(code[i] == '*' && code[i + 1] == '/')) i++
                    i += 2
                }
                c == '"' -> {
                    i++
                    while (i < code.length) {
                        val d = code[i]
                        when {
                            d == '\\' -> { i += 2; continue }
                            d == '\n' -> error("raw newline inside string literal:\n$code")
                            d == '$' && i + 1 < code.length && (code[i + 1] == '{' || code[i + 1] == '_' || code[i + 1].isLetter()) ->
                                error("unescaped string template `$` in literal:\n$code")
                            d == '"' -> break
                        }
                        i++
                    }
                    if (i >= code.length) error("unterminated string literal:\n$code")
                    i++
                }
                c in opens -> { stack.addLast(c); i++ }
                c in close -> {
                    if (stack.isEmpty() || stack.removeLast() != close[c])
                        error("unbalanced '$c':\n$code")
                    i++
                }
                else -> i++
            }
        }
        assertTrue(stack.isEmpty(), "unclosed delimiters $stack in:\n$code")
    }

    /** A tree that instantiates every Node variant, with adversarial user text. */
    private fun kitchenSink(): Node = Node.Composable(
        "root",
        children = listOf(
            Node.Text("t", tricky, fontSize = 16),
            Node.Button("b", tricky),
            Node.Spacer("sp"),
            Node.Image("img", url = "https://x/\"a\".png", contentDescription = tricky),
            Node.Image("img2", url = "data:image/png;base64,ABC=="),
            Node.Divider("dv"),
            Node.Instance("inst", "missing-ref"), // dangling — must emit a safe comment
            Node.Icon("ic", composer.model.IconKind.Add, contentDescription = tricky),
            Node.IconButton("ib", composer.model.IconKind.Menu),
            Node.TextField("tf", value = tricky, placeholder = tricky),
            Node.Switch("sw", true),
            Node.Checkbox("cb", false),
            Node.RadioButton("rb", true),
            Node.Slider("sl", 0.5f),
            Node.CircularProgress("cp"),
            Node.LinearProgress("lp"),
            // Verbatim code — lexically valid Kotlin by contract (it parsed as a
            // statement in the source file it was captured from).
            Node.RawCode("raw", "when (state) {\n    is Loading -> Spinner()\n    else -> Content(items.size)\n}"),
            Node.Card("card", children = listOf(Node.Text("ct", tricky))),
            Node.Fab("fab", children = listOf(Node.Icon("fi", composer.model.IconKind.Add))),
            Node.Dialog("dlg", children = listOf(Node.Text("dt", tricky))),
            Node.BottomSheet("bs", children = listOf(Node.Text("bt", tricky))),
            Node.Column("col", children = listOf(Node.Row("row", children = listOf(Node.Box("box", children = listOf(Node.Text("bx", tricky))))))),
            Node.Scaffold(
                "scaf",
                children = listOf(Node.Text("sc", tricky)),
                topBar = Node.TopAppBar("tb", title = Node.Text("tt", tricky), navigationIcon = Node.IconButton("ni", composer.model.IconKind.Menu), actions = listOf(Node.IconButton("ac", composer.model.IconKind.Search))),
                bottomBar = Node.Box("bb", children = listOf(Node.Text("bbt", "bottom"))),
                fab = Node.Fab("sfab", children = listOf(Node.Icon("sfi", composer.model.IconKind.Add))),
            ),
        ),
    )

    // ---- Theme (shared AppTheme wrapper) -----------------------------------

    @Test
    fun default_theme_emits_no_app_theme_wrapper() {
        val code = CodeGen.generate(Node.Composable("root", children = listOf(Node.Text("t", "Hi"))))
        assertTrue("MaterialTheme" !in code && "AppTheme" !in code, code)
        assertTrue("    Text(\"Hi\")\n" in code, code) // body stays at indent 1 (4 spaces)
    }

    @Test
    fun customized_light_theme_emits_scheme_val_and_app_theme() {
        // Old-style root: migration lifts the screen's legacy theme onto the artboard
        // as a named theme ("Light") in the themes list.
        val theme = composer.model.DesignTheme(primary = 0xFF123456, secondary = 0xFF00FF00)
        val code = CodeGen.generate(Node.Composable("root", theme = theme, children = listOf(Node.Text("t", "Hi"))))
        assertTrue("val LightColors = lightColorScheme(" in code, code)
        assertTrue("    primary = Color(0xFF123456)," in code, code)
        assertTrue("    secondary = Color(0xFF00FF00)," in code, code)
        assertTrue("fun AppTheme(colorScheme: ColorScheme = LightColors, content: @Composable () -> Unit) {" in code, code)
        assertTrue("MaterialTheme(colorScheme = colorScheme, content = content)" in code, code)
        // Screens are emitted bare — the body stays at indent 1 inside its own fun.
        assertTrue("    Text(\"Hi\")" in code, code)
        assertTrue("import androidx.compose.material3.ColorScheme" in code, code)
        assertTrue("import androidx.compose.material3.lightColorScheme" in code, code)
        assertTrue("import androidx.compose.ui.graphics.Color" in code, code)
        // Unchanged tokens are NOT emitted (they keep the builder's defaults).
        assertTrue("surface = " !in code, code)
        assertLexicallyValid(code)
    }

    @Test
    fun dark_theme_without_color_changes_emits_bare_dark_scheme() {
        val code = CodeGen.generate(Node.Composable("root", theme = composer.model.DesignTheme(dark = true), children = listOf(Node.Text("t", "Hi"))))
        assertTrue("val DarkColors = darkColorScheme()" in code, code)
        assertTrue("fun AppTheme(colorScheme: ColorScheme = DarkColors, content: @Composable () -> Unit) {" in code, code)
        assertTrue("import androidx.compose.material3.darkColorScheme" in code, code)
        // No token overrides → no Color import from the scheme.
        assertTrue("lightColorScheme" !in code, code)
        assertLexicallyValid(code)
    }

    @Test
    fun multiple_themes_emit_all_schemes_with_active_as_default() {
        val ab = Node.Artboard(
            id = "ab",
            composables = listOf(Node.Composable("s1", listOf(Node.Text("t", "Hi")))),
            themes = listOf(
                composer.model.NamedTheme("Light"),
                composer.model.NamedTheme("Dark", composer.model.DesignTheme(dark = true)),
                composer.model.NamedTheme("Brand!", composer.model.DesignTheme(primary = 0xFF112233)),
            ),
            activeTheme = 1,
        )
        val code = CodeGen.generate(ab)
        assertTrue("val LightColors = lightColorScheme()" in code, code)
        assertTrue("val DarkColors = darkColorScheme()" in code, code)
        assertTrue("val BrandColors = lightColorScheme(" in code, code)
        assertTrue("    primary = Color(0xFF112233)," in code, code)
        // The ACTIVE theme (Dark) is the AppTheme default.
        assertTrue("fun AppTheme(colorScheme: ColorScheme = DarkColors, content: @Composable () -> Unit) {" in code, code)
        assertLexicallyValid(code)
    }

    // ---- Golden file ------------------------------------------------------

    @Test
    fun full_sample_tree_matches_golden_file() {
        val golden = readResource("/golden/sample_tree.kt")
        assertEquals(golden, CodeGen.generate(sampleTree()))
    }

    private fun readResource(path: String): String =
        CodeGenTest::class.java.getResource(path)?.readText()
            ?: error("golden resource not found: $path")

    /** Mirrors app's SampleTree.kt — kept here so codegen tests are self-contained. */
    private fun sampleTree(): Node = Node.Column(
        id = "root",
        modifier = listOf(FillMaxSize, Padding(16)),
        children = listOf(
            Node.Text("title", "Welcome to Composer", listOf(Padding(8))),
            Node.Row(
                "actions",
                modifier = listOf(Padding(8)),
                children = listOf(
                    Node.Button("primary", "Primary"),
                    Node.Spacer("gap", listOf(Size(12, 0))),
                    Node.Button("secondary", "Secondary"),
                ),
            ),
            Node.Box(
                "panel",
                modifier = listOf(FillMaxWidth, Padding(8), Background(0xFFE0E0E0)),
                children = listOf(Node.Text("panelLabel", "A boxed label", listOf(Padding(16)))),
            ),
        ),
    )
}
