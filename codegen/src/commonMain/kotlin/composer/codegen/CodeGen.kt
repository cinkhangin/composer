package composer.codegen

import composer.model.BoxAlignment
import composer.model.ButtonVariant
import composer.model.CornerUnit
import composer.model.DesignTheme
import composer.model.GradientDirection
import composer.model.HAlignment
import composer.model.HArrangement
import composer.model.ModifierSpec
import composer.model.Node
import composer.model.PaddingMode
import composer.model.TextAlignment
import composer.model.TextFontFamily
import composer.model.TextWeight
import composer.model.ThemeColorRef
import composer.model.TopAppBarVariant
import composer.model.effectiveLineHeight
import composer.model.migrateToArtboard
import composer.model.NamedTheme
import composer.model.VAlignment
import composer.model.VArrangement

/**
 * Deterministic Compose Multiplatform code generation from a design tree.
 *
 * Pure function of the [Node] tree (see GOAL.md). Depends only on
 * the model and the Kotlin stdlib, so it runs both on Wasm (for the live code
 * panel) and on the JVM (for golden-file tests in M2).
 */
object CodeGen {

    /**
     * Generate a complete Kotlin source file for [root]: one `@Composable fun` per
     * [Node.Composable] screen under the [Node.Artboard]. Function names come from
     * the artboard's layer names (sanitized to PascalCase identifiers, deduped);
     * an unnamed screen falls back to `Screen<n>`. A customized artboard theme is
     * emitted once as a shared `AppTheme(content)` wrapper — the screen functions
     * are emitted bare so the caller decides where to apply the theme.
     *
     * Old single-Frame roots (and bare component roots in tests) are migrated on
     * the fly, so `generate` accepts any historical tree shape.
     */
    fun generate(root: Node): String {
        val artboard = root.migrateToArtboard()
        // Emit the theme block when there are several themes or the single one is
        // customized — an untouched default stays invisible, as before.
        val themes = artboard.themes
        val themed = themes.size > 1 || themes.any { it.theme.isCustomized() }
        val imports = mutableSetOf("androidx.compose.runtime.Composable")
        // Shared counter so hoisted state vars (state1, state2, …) are file-unique.
        val seq = intArrayOf(0)

        val used = mutableSetOf<String>()
        val schemeVals = if (themed) {
            used += "AppTheme" // reserve — a screen named "App Theme" must not collide
            themes.mapIndexed { i, named ->
                val base = (sanitizeIdentifier(named.name) ?: "Theme${i + 1}") + "Colors"
                var candidate = base
                var n = 2
                while (!used.add(candidate)) {
                    candidate = "$base$n"
                    n++
                }
                candidate
            }
        } else emptyList()

        val screens = artboard.composables.filterIsInstance<Node.Composable>()
        val fns = screens.mapIndexed { i, screen ->
            val body = StringBuilder()
            emit(screen, indent = 1, out = body, imports = imports, seq = seq)
            functionName(artboard.layerNames[screen.id], i, used) to body.toString()
        }
        val themeBlock = if (themed) themeBlock(themes, schemeVals, artboard.activeTheme, imports) else null

        // Some Material3 components (any TopAppBar variant) are experimental — opt in if used.
        val m3Experimental = setOf("TopAppBar", "CenterAlignedTopAppBar", "MediumTopAppBar", "LargeTopAppBar", "ModalBottomSheet")
        val needsM3OptIn = imports.any { it.removePrefix("androidx.compose.material3.") in m3Experimental }
        if (needsM3OptIn) imports += "androidx.compose.material3.ExperimentalMaterial3Api"

        return buildString {
            for (imp in imports.sorted()) appendLine("import $imp")
            appendLine()
            if (themeBlock != null) {
                append(themeBlock)
                if (fns.isNotEmpty()) appendLine()
            }
            fns.forEachIndexed { i, (name, body) ->
                if (needsM3OptIn) appendLine("@OptIn(ExperimentalMaterial3Api::class)")
                appendLine("@Composable")
                appendLine("fun $name() {")
                append(body)
                appendLine("}")
                if (i != fns.lastIndex) appendLine()
            }
        }
    }

    /**
     * A generated function name for one screen: the layer name sanitized to a
     * PascalCase identifier (`"login screen"` → `LoginScreen`; a leading digit is
     * prefixed), `Screen<n>` when unnamed/unsanitizable. Duplicates get a numeric
     * suffix so the file always compiles.
     */
    private fun functionName(layerName: String?, index: Int, used: MutableSet<String>): String {
        val base = layerName?.let { sanitizeIdentifier(it) } ?: "Screen${index + 1}"
        var candidate = base
        var n = 2
        while (!used.add(candidate)) {
            candidate = "$base$n"
            n++
        }
        return candidate
    }

    /** PascalCase identifier from free text, or null if nothing usable remains. */
    private fun sanitizeIdentifier(raw: String): String? {
        val words = raw.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        val joined = words.joinToString("") { w -> w.replaceFirstChar { it.uppercaseChar() } }
        return if (joined.first().isDigit()) "Screen$joined" else joined
    }

    /**
     * The theme block: one top-level `ColorScheme` val per named theme (only the
     * tokens the user changed from the baseline-light default are passed, so a
     * bare dark theme emits `darkColorScheme()` and keeps the builder's dark
     * defaults for the rest), plus a shared `AppTheme(colorScheme, content)`
     * wrapper defaulting to the ACTIVE theme — switch themes by passing another
     * scheme (e.g. `AppTheme(DarkColors) { … }`).
     */
    private fun themeBlock(
        themes: List<NamedTheme>,
        schemeVals: List<String>,
        activeTheme: Int,
        imports: MutableSet<String>,
    ): String {
        imports += "androidx.compose.material3.MaterialTheme"
        imports += "androidx.compose.material3.ColorScheme"
        return buildString {
            themes.forEachIndexed { i, named ->
                val builder = if (named.theme.dark) "darkColorScheme" else "lightColorScheme"
                imports += "androidx.compose.material3.$builder"
                val changed = named.theme.changedTokens()
                if (changed.isEmpty()) {
                    appendLine("val ${schemeVals[i]} = $builder()")
                } else {
                    imports += "androidx.compose.ui.graphics.Color"
                    appendLine("val ${schemeVals[i]} = $builder(")
                    for ((name, value) in changed) {
                        appendLine("    $name = Color(0x${value.toString(16).uppercase().padStart(8, '0')}),")
                    }
                    appendLine(")")
                }
            }
            appendLine()
            val active = schemeVals.getOrNull(activeTheme.coerceIn(0, (schemeVals.size - 1).coerceAtLeast(0))) ?: schemeVals.first()
            appendLine("@Composable")
            appendLine("fun AppTheme(colorScheme: ColorScheme = $active, content: @Composable () -> Unit) {")
            appendLine("    MaterialTheme(colorScheme = colorScheme, content = content)")
            appendLine("}")
        }
    }

    /**
     * @param inWeightScope whether the node's parent is a Row/Column, so a
     * `weight` modifier on this node is valid. Weight is dropped otherwise.
     */
    private fun emit(node: Node, indent: Int, out: StringBuilder, imports: MutableSet<String>, seq: IntArray, inWeightScope: Boolean = false, scopeModifier: String? = null) {
        val pad = "    ".repeat(indent)
        // weight is RowScope/ColumnScope-only, and weight(<=0) throws — strip both cases.
        val mods = node.modifier.filterNot {
            it is ModifierSpec.Weight && (!inWeightScope || it.value <= 0f)
        }
        when (node) {
            is Node.Text -> {
                imports += "androidx.compose.material3.Text"
                val mod = modifierExpr(mods, imports, scopeModifier, indent)
                val args = mutableListOf("\"${esc(node.text)}\"")
                mod?.let { args += "modifier = $it" }
                node.color?.let { args += "color = ${colorExpr(it, imports)}" }
                if (node.fontSize > 0) {
                    imports += "androidx.compose.ui.unit.sp"
                    args += "fontSize = ${node.fontSize}.sp"
                }
                if (node.fontWeight != TextWeight.Normal) {
                    imports += "androidx.compose.ui.text.font.FontWeight"
                    args += "fontWeight = FontWeight.${node.fontWeight.name}"
                }
                if (node.fontFamily != TextFontFamily.Default) {
                    imports += "androidx.compose.ui.text.font.FontFamily"
                    args += "fontFamily = FontFamily.${node.fontFamily.name}"
                }
                val lineHeight = node.effectiveLineHeight()
                if (lineHeight > 0) {
                    imports += "androidx.compose.ui.unit.sp"
                    args += "lineHeight = $lineHeight.sp"
                }
                if (node.textAlign != TextAlignment.Start) {
                    imports += "androidx.compose.ui.text.style.TextAlign"
                    args += "textAlign = TextAlign.${node.textAlign.name}"
                }
                // A device font can't be referenced portably — leave a note to embed it.
                if (node.customFont.isNotEmpty()) {
                    out.appendLine("$pad// Font \"${node.customFont.replace('\n', ' ').replace('\r', ' ')}\" — embed it as a font resource and set fontFamily = FontFamily(Font(...)).")
                }
                appendCall(out, indent, "Text", args)
            }

            is Node.Button -> {
                // A container: children (Text/Icon/anything) emit into the content
                // lambda, exactly like the real M3 Button's RowScope slot.
                val name = buttonComposable(node.variant)
                imports += "androidx.compose.material3.$name"
                val mod = modifierExpr(mods, imports, scopeModifier, indent)
                val args = listOfNotNull("onClick = {}", mod?.let { "modifier = $it" })
                appendCall(out, indent, name, args, open = true)
                for (child in node.children) emit(child, indent + 1, out, imports, seq = seq, inWeightScope = true)
                out.appendLine("$pad}")
            }

            is Node.Spacer -> {
                imports += "androidx.compose.foundation.layout.Spacer"
                val mod = modifierExpr(mods, imports, scopeModifier, indent) ?: run {
                    imports += "androidx.compose.ui.Modifier"
                    "Modifier"
                }
                appendCall(out, indent, "Spacer", listOf("modifier = $mod"))
            }

            is Node.Image -> {
                val mod = modifierExpr(mods, imports, scopeModifier, indent)
                val desc = if (node.contentDescription.isBlank()) "null" else "\"${esc(node.contentDescription)}\""
                val isWebUrl = node.url.startsWith("http://") || node.url.startsWith("https://")
                when {
                    isWebUrl -> {
                        // Coil 3 AsyncImage — the user's project must depend on io.coil-kt.coil3:coil-compose.
                        imports += "coil3.compose.AsyncImage"
                        val args = listOfNotNull(
                            "model = \"${esc(node.url)}\"",
                            "contentDescription = $desc",
                            mod?.let { "modifier = $it" },
                        )
                        appendCall(out, indent, "AsyncImage", args)
                    }
                    else -> {
                        // empty or a picked local (data:) image — emit a placeholder.
                        imports += "androidx.compose.foundation.Image"
                        imports += "androidx.compose.ui.graphics.painter.ColorPainter"
                        imports += "androidx.compose.ui.graphics.Color"
                        if (node.url.isNotEmpty()) out.appendLine("$pad// Local image — set a URL or wire up a real painter/resource here.")
                        val hex = node.placeholderColor.toString(16).uppercase().padStart(8, '0')
                        val args = listOfNotNull(
                            "painter = ColorPainter(Color(0x$hex))",
                            "contentDescription = $desc",
                            mod?.let { "modifier = $it" },
                        )
                        appendCall(out, indent, "Image", args)
                    }
                }
            }

            is Node.Divider -> {
                imports += "androidx.compose.material3.HorizontalDivider"
                val mod = modifierExpr(mods, imports, scopeModifier, indent)
                val args = listOfNotNull(mod?.let { "modifier = $it" })
                appendCall(out, indent, "HorizontalDivider", args)
            }

            is Node.Icon -> {
                imports += "androidx.compose.material3.Icon"
                imports += "androidx.compose.material.icons.Icons"
                imports += "androidx.compose.material.icons.filled.${node.icon.name}"
                val mod = modifierExpr(mods, imports, scopeModifier, indent)
                val desc = if (node.contentDescription.isBlank()) "null" else "\"${esc(node.contentDescription)}\""
                val args = listOfNotNull("Icons.Default.${node.icon.name}", "contentDescription = $desc", mod?.let { "modifier = $it" })
                appendCall(out, indent, "Icon", args)
            }

            is Node.IconButton -> {
                imports += "androidx.compose.material3.IconButton"
                imports += "androidx.compose.material3.Icon"
                imports += "androidx.compose.material.icons.Icons"
                imports += "androidx.compose.material.icons.filled.${node.icon.name}"
                val mod = modifierExpr(mods, imports, scopeModifier, indent)
                val args = listOfNotNull("onClick = {}", mod?.let { "modifier = $it" })
                appendCall(out, indent, "IconButton", args, open = true)
                out.appendLine("$pad    Icon(Icons.Default.${node.icon.name}, contentDescription = null)")
                out.appendLine("$pad}")
            }

            is Node.TextField -> {
                imports += "androidx.compose.material3.OutlinedTextField"
                imports += "androidx.compose.material3.Text"
                stateImports(imports)
                val v = "state${++seq[0]}"
                out.appendLine("${pad}var $v by remember { mutableStateOf(\"${esc(node.value)}\") }")
                val mod = modifierExpr(mods, imports, scopeModifier, indent)
                val args = mutableListOf("value = $v", "onValueChange = { $v = it }")
                mod?.let { args += "modifier = $it" }
                if (node.placeholder.isNotEmpty()) args += "label = { Text(\"${esc(node.placeholder)}\") }"
                appendCall(out, indent, "OutlinedTextField", args)
            }

            is Node.Switch -> {
                imports += "androidx.compose.material3.Switch"
                stateImports(imports)
                val v = "state${++seq[0]}"
                out.appendLine("${pad}var $v by remember { mutableStateOf(${node.checked}) }")
                val mod = modifierExpr(mods, imports, scopeModifier, indent)
                val args = listOfNotNull("checked = $v", "onCheckedChange = { $v = it }", mod?.let { "modifier = $it" })
                appendCall(out, indent, "Switch", args)
            }

            is Node.Checkbox -> {
                imports += "androidx.compose.material3.Checkbox"
                stateImports(imports)
                val v = "state${++seq[0]}"
                out.appendLine("${pad}var $v by remember { mutableStateOf(${node.checked}) }")
                val mod = modifierExpr(mods, imports, scopeModifier, indent)
                val args = listOfNotNull("checked = $v", "onCheckedChange = { $v = it }", mod?.let { "modifier = $it" })
                appendCall(out, indent, "Checkbox", args)
            }

            is Node.RadioButton -> {
                imports += "androidx.compose.material3.RadioButton"
                stateImports(imports)
                val v = "state${++seq[0]}"
                out.appendLine("${pad}var $v by remember { mutableStateOf(${node.selected}) }")
                val mod = modifierExpr(mods, imports, scopeModifier, indent)
                val args = listOfNotNull("selected = $v", "onClick = { $v = !$v }", mod?.let { "modifier = $it" })
                appendCall(out, indent, "RadioButton", args)
            }

            is Node.Slider -> {
                imports += "androidx.compose.material3.Slider"
                stateImports(imports)
                val v = "state${++seq[0]}"
                out.appendLine("${pad}var $v by remember { mutableStateOf(${node.value}f) }")
                val mod = modifierExpr(mods, imports, scopeModifier, indent)
                val args = listOfNotNull("value = $v", "onValueChange = { $v = it }", mod?.let { "modifier = $it" })
                appendCall(out, indent, "Slider", args)
            }

            is Node.CircularProgress -> {
                imports += "androidx.compose.material3.CircularProgressIndicator"
                val mod = modifierExpr(mods, imports, scopeModifier, indent)
                val args = listOfNotNull(mod?.let { "modifier = $it" })
                appendCall(out, indent, "CircularProgressIndicator", args)
            }

            is Node.LinearProgress -> {
                imports += "androidx.compose.material3.LinearProgressIndicator"
                val mod = modifierExpr(mods, imports, scopeModifier, indent)
                val args = listOfNotNull(mod?.let { "modifier = $it" })
                appendCall(out, indent, "LinearProgressIndicator", args)
            }

            is Node.Card -> emitContainer(
                "Card", "androidx.compose.material3.Card",
                mods, node.children, indent, out, imports, seq = seq, childWeightScope = false,
                scopeModifier = scopeModifier,
            )

            is Node.Fab -> {
                imports += "androidx.compose.material3.FloatingActionButton"
                val mod = modifierExpr(mods, imports, scopeModifier, indent)
                val args = listOfNotNull("onClick = {}", mod?.let { "modifier = $it" })
                appendCall(out, indent, "FloatingActionButton", args, open = true)
                for (child in node.children) emit(child, indent + 1, out, imports, seq = seq, inWeightScope = false)
                out.appendLine("$pad}")
            }

            is Node.Dialog -> {
                imports += "androidx.compose.ui.window.Dialog"
                imports += "androidx.compose.material3.Surface"
                imports += "androidx.compose.foundation.shape.RoundedCornerShape"
                imports += "androidx.compose.foundation.layout.Column"
                imports += "androidx.compose.foundation.layout.padding"
                imports += "androidx.compose.ui.Modifier"
                imports += "androidx.compose.ui.unit.dp"
                val mod = modifierExpr(mods, imports, scopeModifier, indent)
                out.appendLine("${pad}Dialog(onDismissRequest = {}) {")
                val surfaceArgs = listOfNotNull(mod?.let { "modifier = $it" }, "shape = RoundedCornerShape(16.dp)")
                appendCall(out, indent + 1, "Surface", surfaceArgs, open = true)
                out.appendLine("$pad        Column(modifier = Modifier.padding(24.dp)) {")
                for (child in node.children) emit(child, indent + 3, out, imports, seq = seq, inWeightScope = true)
                out.appendLine("$pad        }")
                out.appendLine("$pad    }")
                out.appendLine("$pad}")
            }

            is Node.BottomSheet -> {
                imports += "androidx.compose.material3.ModalBottomSheet"
                imports += "androidx.compose.foundation.layout.Column"
                imports += "androidx.compose.foundation.layout.padding"
                imports += "androidx.compose.ui.Modifier"
                imports += "androidx.compose.ui.unit.dp"
                val mod = modifierExpr(mods, imports, scopeModifier, indent)
                val args = listOfNotNull("onDismissRequest = {}", mod?.let { "modifier = $it" })
                appendCall(out, indent, "ModalBottomSheet", args, open = true)
                out.appendLine("$pad    Column(modifier = Modifier.padding(16.dp)) {")
                for (child in node.children) emit(child, indent + 2, out, imports, seq = seq, inWeightScope = true)
                out.appendLine("$pad    }")
                out.appendLine("$pad}")
            }

            is Node.Scaffold -> {
                imports += "androidx.compose.material3.Scaffold"
                val mod = modifierExpr(mods, imports, scopeModifier, indent)
                // Slots are permanent Slot containers; an EMPTY slot omits its
                // argument entirely so the output stays clean.
                fun slotKids(s: Node?): List<Node> = when (s) {
                    is Node.Slot -> s.children
                    null -> emptyList()
                    else -> listOf(s) // legacy direct slot content (pre-Slot saves)
                }
                val topKids = slotKids(node.topBar)
                val bottomKids = slotKids(node.bottomBar)
                val fabKids = slotKids(node.fab)
                val hasArgs = mod != null || topKids.isNotEmpty() || bottomKids.isNotEmpty() || fabKids.isNotEmpty()
                val hasContent = node.children.isNotEmpty()
                // Content applies the Scaffold's innerPadding (so it clears the bars) — no wrapper node.
                val contentScope = if (hasContent) "padding(innerPadding)" else null
                if (hasContent) imports += "androidx.compose.foundation.layout.padding"
                val lambdaOpen = if (hasContent) " { innerPadding ->" else " {"
                if (hasArgs) {
                    out.appendLine("$pad" + "Scaffold(")
                    if (mod != null) out.appendLine("$pad    modifier = $mod,")
                    fun slot(name: String, kids: List<Node>) {
                        if (kids.isEmpty()) return
                        out.appendLine("$pad    $name = {")
                        for (k in kids) emit(k, indent + 2, out, imports, seq = seq, inWeightScope = false)
                        out.appendLine("$pad    },")
                    }
                    slot("topBar", topKids)
                    slot("bottomBar", bottomKids)
                    slot("floatingActionButton", fabKids)
                    out.appendLine("$pad)$lambdaOpen")
                } else {
                    out.appendLine("$pad" + "Scaffold$lambdaOpen")
                }
                for (child in node.children) emit(child, indent + 1, out, imports, seq = seq, inWeightScope = false, scopeModifier = contentScope)
                out.appendLine("$pad}")
            }

            is Node.Column -> {
                val extra = mutableListOf<String>()
                if (node.spacing > 0) {
                    imports += "androidx.compose.foundation.layout.Arrangement"
                    imports += "androidx.compose.ui.unit.dp"
                    extra += "verticalArrangement = Arrangement.spacedBy(${node.spacing}.dp)"
                } else if (node.verticalArrangement != VArrangement.Top) {
                    imports += "androidx.compose.foundation.layout.Arrangement"
                    extra += "verticalArrangement = ${vArrangementCode(node.verticalArrangement)}"
                }
                if (node.horizontalAlignment != HAlignment.Start) {
                    imports += "androidx.compose.ui.Alignment"
                    extra += "horizontalAlignment = ${hAlignmentCode(node.horizontalAlignment)}"
                }
                emitContainer(
                    "Column", "androidx.compose.foundation.layout.Column",
                    mods, node.children, indent, out, imports, seq = seq, childWeightScope = true, extraArgs = extra,
                    scopeModifier = scopeModifier,
                )
            }

            is Node.Row -> {
                val extra = mutableListOf<String>()
                if (node.spacing > 0) {
                    imports += "androidx.compose.foundation.layout.Arrangement"
                    imports += "androidx.compose.ui.unit.dp"
                    extra += "horizontalArrangement = Arrangement.spacedBy(${node.spacing}.dp)"
                } else if (node.horizontalArrangement != HArrangement.Start) {
                    imports += "androidx.compose.foundation.layout.Arrangement"
                    extra += "horizontalArrangement = ${hArrangementCode(node.horizontalArrangement)}"
                }
                if (node.verticalAlignment != VAlignment.Top) {
                    imports += "androidx.compose.ui.Alignment"
                    extra += "verticalAlignment = ${vAlignmentCode(node.verticalAlignment)}"
                }
                emitContainer(
                    "Row", "androidx.compose.foundation.layout.Row",
                    mods, node.children, indent, out, imports, seq = seq, childWeightScope = true, extraArgs = extra,
                    scopeModifier = scopeModifier,
                )
            }

            is Node.Box -> {
                val extra = mutableListOf<String>()
                if (node.contentAlignment != BoxAlignment.TopStart) {
                    imports += "androidx.compose.ui.Alignment"
                    extra += "contentAlignment = Alignment.${node.contentAlignment.name}"
                }
                emitContainer(
                    "Box", "androidx.compose.foundation.layout.Box",
                    mods, node.children, indent, out, imports, seq = seq, childWeightScope = false, extraArgs = extra,
                    scopeModifier = scopeModifier,
                )
            }

            is Node.TopAppBar -> {
                val name = topBarComposable(node.variant)
                imports += "androidx.compose.material3.$name"
                val mod = modifierExpr(mods, imports, scopeModifier, indent)
                out.appendLine("$pad" + "$name(")
                out.appendLine("$pad    title = {")
                node.title?.let { emit(it, indent + 2, out, imports, seq = seq, inWeightScope = false) }
                out.appendLine("$pad    },")
                node.navigationIcon?.let {
                    out.appendLine("$pad    navigationIcon = {")
                    emit(it, indent + 2, out, imports, seq = seq, inWeightScope = false)
                    out.appendLine("$pad    },")
                }
                if (node.actions.isNotEmpty()) {
                    out.appendLine("$pad    actions = {")
                    for (a in node.actions) emit(a, indent + 2, out, imports, seq = seq, inWeightScope = false)
                    out.appendLine("$pad    },")
                }
                mod?.let { out.appendLine("$pad    modifier = $it,") }
                out.appendLine("$pad)")
            }

            // A Composable is the function scope — emit its children directly, no wrapper.
            is Node.Composable -> for (child in node.children) emit(child, indent, out, imports, seq = seq, inWeightScope = false)
            // A Slot is a slot-argument scope — normally emitted by its parent
            // (e.g. the Scaffold branch); defensively emit children directly.
            is Node.Slot -> for (child in node.children) emit(child, indent, out, imports, seq = seq, inWeightScope = false)
            // The artboard is handled by [generate]; defensively emit its screens' bodies.
            is Node.Artboard -> for (screen in node.composables) emit(screen, indent, out, imports, seq = seq, inWeightScope = false)
        }
    }

    private fun emitContainer(
        name: String,
        import: String,
        modifier: List<ModifierSpec>,
        children: List<Node>,
        indent: Int,
        out: StringBuilder,
        imports: MutableSet<String>,
        seq: IntArray,
        childWeightScope: Boolean,
        extraArgs: List<String> = emptyList(),
        scopeModifier: String? = null,
    ) {
        imports += import
        val pad = "    ".repeat(indent)
        val mod = modifierExpr(modifier, imports, scopeModifier, indent)
        val args = listOfNotNull(mod?.let { "modifier = $it" }) + extraArgs
        if (args.isEmpty()) out.appendLine("$pad$name {") else appendCall(out, indent, name, args, open = true)
        for (child in children) emit(child, indent + 1, out, imports, seq = seq, inWeightScope = childWeightScope)
        out.appendLine("$pad}")
    }

    private fun buttonComposable(v: ButtonVariant): String = when (v) {
        ButtonVariant.Filled -> "Button"
        ButtonVariant.Elevated -> "ElevatedButton"
        ButtonVariant.FilledTonal -> "FilledTonalButton"
        ButtonVariant.Outlined -> "OutlinedButton"
        ButtonVariant.Text -> "TextButton"
    }

    private fun topBarComposable(v: TopAppBarVariant): String = when (v) {
        TopAppBarVariant.Small -> "TopAppBar"
        TopAppBarVariant.CenterAligned -> "CenterAlignedTopAppBar"
        TopAppBarVariant.Medium -> "MediumTopAppBar"
        TopAppBarVariant.Large -> "LargeTopAppBar"
    }

    private fun vArrangementCode(v: VArrangement): String = when (v) {
        VArrangement.Top -> "Arrangement.Top"
        VArrangement.Bottom -> "Arrangement.Bottom"
        VArrangement.Center -> "Arrangement.Center"
        VArrangement.SpaceBetween -> "Arrangement.SpaceBetween"
        VArrangement.SpaceAround -> "Arrangement.SpaceAround"
        VArrangement.SpaceEvenly -> "Arrangement.SpaceEvenly"
    }

    private fun hArrangementCode(h: HArrangement): String = when (h) {
        HArrangement.Start -> "Arrangement.Start"
        HArrangement.End -> "Arrangement.End"
        HArrangement.Center -> "Arrangement.Center"
        HArrangement.SpaceBetween -> "Arrangement.SpaceBetween"
        HArrangement.SpaceAround -> "Arrangement.SpaceAround"
        HArrangement.SpaceEvenly -> "Arrangement.SpaceEvenly"
    }

    private fun hAlignmentCode(a: HAlignment): String = when (a) {
        HAlignment.Start -> "Alignment.Start"
        HAlignment.Center -> "Alignment.CenterHorizontally"
        HAlignment.End -> "Alignment.End"
    }

    private fun vAlignmentCode(a: VAlignment): String = when (a) {
        VAlignment.Top -> "Alignment.Top"
        VAlignment.Center -> "Alignment.CenterVertically"
        VAlignment.Bottom -> "Alignment.Bottom"
    }

    /**
     * Build a `Modifier.foo().bar()` expression, or null when the chain is empty.
     * With 2+ calls the chain wraps — each `.call()` on its own line, indented two
     * levels past [indent] (Kotlin-official style) — so multi-modifier calls read cleanly.
     */
    private fun modifierExpr(specs: List<ModifierSpec>, imports: MutableSet<String>, leading: String? = null, indent: Int = 0): String? {
        if (specs.isEmpty() && leading == null) return null
        imports += "androidx.compose.ui.Modifier"
        val specParts = specs.map { spec ->
            when (spec) {
                is ModifierSpec.Padding -> {
                    imports += "androidx.compose.foundation.layout.padding"
                    imports += "androidx.compose.ui.unit.dp"
                    when (spec.mode) {
                        PaddingMode.All -> "padding(${spec.all}.dp)"
                        PaddingMode.Symmetric -> "padding(horizontal = ${spec.horizontal}.dp, vertical = ${spec.vertical}.dp)"
                        PaddingMode.Sides -> "padding(start = ${spec.start}.dp, top = ${spec.top}.dp, end = ${spec.end}.dp, bottom = ${spec.bottom}.dp)"
                    }
                }

                is ModifierSpec.Size -> {
                    imports += "androidx.compose.foundation.layout.size"
                    imports += "androidx.compose.ui.unit.dp"
                    "size(${spec.width}.dp, ${spec.height}.dp)"
                }

                is ModifierSpec.Width -> {
                    imports += "androidx.compose.foundation.layout.width"
                    imports += "androidx.compose.ui.unit.dp"
                    "width(${spec.width}.dp)"
                }

                is ModifierSpec.Height -> {
                    imports += "androidx.compose.foundation.layout.height"
                    imports += "androidx.compose.ui.unit.dp"
                    "height(${spec.height}.dp)"
                }

                is ModifierSpec.Offset -> {
                    imports += "androidx.compose.foundation.layout.offset"
                    imports += "androidx.compose.ui.unit.dp"
                    "offset(${spec.x}.dp, ${spec.y}.dp)"
                }

                is ModifierSpec.Background -> {
                    imports += "androidx.compose.foundation.background"
                    val stops = spec.gradientStops()
                    val fill = if (stops.isNotEmpty()) {
                        imports += "androidx.compose.ui.graphics.Brush"
                        val builder = when (spec.direction) {
                            GradientDirection.Vertical -> "verticalGradient"
                            GradientDirection.Horizontal -> "horizontalGradient"
                            GradientDirection.Diagonal -> "linearGradient"
                            GradientDirection.Radial -> "radialGradient"
                        }
                        "Brush.$builder(listOf(${stops.joinToString(", ") { colorExpr(it, imports) }}))"
                    } else {
                        colorExpr(spec.color, imports)
                    }
                    val shape = cornerShapeExpr(spec.corner, spec.cornerUnit, imports)
                    if (shape != null) "background($fill, $shape)" else "background($fill)"
                }

                // weight() is a Row/Column scope member — no import needed.
                is ModifierSpec.Weight -> "weight(${spec.value}f)"

                is ModifierSpec.AspectRatio -> {
                    imports += "androidx.compose.foundation.layout.aspectRatio"
                    "aspectRatio(${spec.width}f / ${spec.height.coerceAtLeast(1)}f)"
                }

                is ModifierSpec.Clip -> {
                    imports += "androidx.compose.ui.draw.clip"
                    val shape = cornerShapeExpr(spec.corner, spec.cornerUnit, imports)
                        ?: run {
                            // corner 0 still needs a shape argument
                            imports += "androidx.compose.foundation.shape.RoundedCornerShape"
                            imports += "androidx.compose.ui.unit.dp"
                            "RoundedCornerShape(0.dp)"
                        }
                    "clip($shape)"
                }

                is ModifierSpec.Alpha -> {
                    imports += "androidx.compose.ui.draw.alpha"
                    "alpha(${spec.value}f)"
                }

                is ModifierSpec.Border -> {
                    imports += "androidx.compose.foundation.border"
                    imports += "androidx.compose.ui.unit.dp"
                    val colorArg = colorExpr(spec.color, imports)
                    val shape = cornerShapeExpr(spec.corner, spec.cornerUnit, imports)
                    if (shape != null) {
                        "border(${spec.width}.dp, $colorArg, $shape)"
                    } else {
                        "border(${spec.width}.dp, $colorArg)"
                    }
                }

                is ModifierSpec.DropShadow -> {
                    imports += "androidx.compose.ui.draw.dropShadow"
                    shadowExpr("dropShadow", spec.radius, spec.color, spec.offsetX, spec.offsetY, spec.spread, spec.corner, spec.cornerUnit, imports)
                }

                is ModifierSpec.InnerShadow -> {
                    imports += "androidx.compose.ui.draw.innerShadow"
                    shadowExpr("innerShadow", spec.radius, spec.color, spec.offsetX, spec.offsetY, spec.spread, spec.corner, spec.cornerUnit, imports)
                }

                ModifierSpec.FillMaxWidth -> {
                    imports += "androidx.compose.foundation.layout.fillMaxWidth"
                    "fillMaxWidth()"
                }

                ModifierSpec.FillMaxHeight -> {
                    imports += "androidx.compose.foundation.layout.fillMaxHeight"
                    "fillMaxHeight()"
                }

                ModifierSpec.FillMaxSize -> {
                    imports += "androidx.compose.foundation.layout.fillMaxSize"
                    "fillMaxSize()"
                }
            }
        }
        // [leading] is a scope-imposed prefix (e.g. a Scaffold's "padding(innerPadding)").
        val parts = listOfNotNull(leading) + specParts
        return joinChain(parts, indent)
    }

    /**
     * `dropShadow(shape, Shadow(…))` / `innerShadow(shape, Shadow(…))` — the Compose UI 1.9
     * shadow API (`androidx.compose.ui.draw`, `Shadow` from `androidx.compose.ui.graphics.shadow`).
     * Default-valued Shadow args (zero offset/spread) are omitted to keep the call clean.
     */
    private fun shadowExpr(name: String, radius: Int, color: Long, offsetX: Int, offsetY: Int, spread: Int, corner: Int, cornerUnit: CornerUnit, imports: MutableSet<String>): String {
        imports += "androidx.compose.ui.graphics.shadow.Shadow"
        imports += "androidx.compose.ui.unit.dp"
        val shape = cornerShapeExpr(corner, cornerUnit, imports) ?: run {
            imports += "androidx.compose.ui.graphics.RectangleShape"
            "RectangleShape"
        }
        val args = buildList {
            add("radius = $radius.dp")
            add("color = ${colorExpr(color, imports)}")
            if (spread != 0) add("spread = $spread.dp")
            if (offsetX != 0 || offsetY != 0) {
                imports += "androidx.compose.ui.unit.DpOffset"
                add("offset = DpOffset($offsetX.dp, $offsetY.dp)")
            }
        }
        return "$name($shape, Shadow(${args.joinToString(", ")}))"
    }

    /**
     * A color argument: a theme-token reference ([ThemeColorRef]) emits
     * `MaterialTheme.colorScheme.<token>` (follows the runtime theme), a plain
     * value emits a `Color(0x…)` literal. Adds whichever import it needs.
     */
    private fun colorExpr(argb: Long, imports: MutableSet<String>): String {
        val token = ThemeColorRef.tokenName(argb)
        return if (token != null) {
            imports += "androidx.compose.material3.MaterialTheme"
            "MaterialTheme.colorScheme.$token"
        } else {
            imports += "androidx.compose.ui.graphics.Color"
            "Color(0x${argb.toString(16).uppercase().padStart(8, '0')})"
        }
    }

    /**
     * `RoundedCornerShape(N.dp)` or the percent overload `RoundedCornerShape(N)`
     * (N% of the smaller side; 50 = pill/circle); null when [corner] <= 0 so
     * callers can omit the shape argument entirely.
     */
    private fun cornerShapeExpr(corner: Int, unit: CornerUnit, imports: MutableSet<String>): String? {
        if (corner <= 0) return null
        imports += "androidx.compose.foundation.shape.RoundedCornerShape"
        return when (unit) {
            CornerUnit.Dp -> {
                imports += "androidx.compose.ui.unit.dp"
                "RoundedCornerShape($corner.dp)"
            }
            CornerUnit.Percent -> "RoundedCornerShape(${corner.coerceAtMost(50)})"
        }
    }

    private fun joinChain(parts: List<String>, indent: Int): String? {
        if (parts.isEmpty()) return null
        return if (parts.size <= 1) {
            "Modifier." + parts.joinToString(".")
        } else {
            val cont = "    ".repeat(indent + 2)
            "Modifier\n" + parts.joinToString("\n") { "$cont.$it" }
        }
    }

    /**
     * Emit a `Name(args)` call. The call breaks one-arg-per-line (with trailing commas)
     * when any arg is itself multi-line (a wrapped modifier chain) **or** the single-line
     * form would exceed [MAX_LINE] columns. [open] appends ` {` for calls whose
     * trailing-lambda body the caller emits next.
     */
    private fun appendCall(out: StringBuilder, indent: Int, name: String, args: List<String>, open: Boolean = false) {
        val pad = "    ".repeat(indent)
        val tail = if (open) " {" else ""
        val oneLine = "$pad$name(${args.joinToString(", ")})$tail"
        val wrap = args.any { '\n' in it } || (args.size > 1 && oneLine.length > MAX_LINE)
        if (!wrap) {
            out.appendLine(oneLine)
        } else {
            out.appendLine("$pad$name(")
            for (arg in args) out.appendLine("$pad    $arg,")
            out.appendLine("$pad)$tail")
        }
    }

    /** Kotlin official-style max line length; calls wider than this wrap one arg per line. */
    private const val MAX_LINE = 100

    /**
     * Escape a raw user string for safe embedding inside a Kotlin double-quoted
     * literal. Without this, a `"`, `\`, `$` (string template), or control char in
     * user content (a Text, label, URL, contentDescription…) produces source that
     * won't compile — or, worse, silently changes meaning (`"$name"` → a template).
     */
    /** Imports needed for a hoisted `var x by remember { mutableStateOf(...) }` state holder. */
    private fun stateImports(imports: MutableSet<String>) {
        imports += "androidx.compose.runtime.getValue"
        imports += "androidx.compose.runtime.setValue"
        imports += "androidx.compose.runtime.remember"
        imports += "androidx.compose.runtime.mutableStateOf"
    }

    private fun esc(s: String): String = buildString {
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '$' -> append("\\$")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(c)
        }
    }
}
