package composer.codegen

import composer.model.BoxAlignment
import composer.model.ButtonVariant
import composer.model.ChipVariant
import composer.model.CornerUnit
import composer.model.DesignTheme
import composer.model.GradientDirection
import composer.model.HAlignment
import composer.model.HArrangement
import composer.model.ModifierSpec
import composer.model.NavAction
import composer.model.childNodes
import composer.model.Node
import composer.model.navAction
import composer.model.PaddingMode
import composer.model.TextAlignment
import composer.model.TextFontFamily
import composer.model.TextWeight
import composer.model.ThemeColorRef
import composer.model.TopAppBarVariant
import composer.model.effectiveLineHeight
import composer.model.typeName
import composer.model.migrateToArtboard
import composer.model.validComponentIds
import composer.model.NamedTheme
import composer.model.VAlignment
import composer.model.VArrangement

/**
 * Deterministic Compose Multiplatform code generation from a design tree.
 *
 * Pure function of the [Node] tree (see GOAL.md). Depends only on
 * the model and the Kotlin stdlib, so it stays deterministic and JVM-testable.
 */
object CodeGen {

    /**
     * Component-function registry for the CURRENT [generate] run: main-node id →
     * generated function name. A field rather than a parameter purely to avoid
     * threading context through ~40 recursive emit call sites — [generate] sets
     * it up-front and the object is used sequentially by tests and IDE writes,
     * so generate remains a pure function of its input.
     */
    private var componentFns: Map<String, String> = emptyMap()

    /**
     * Nav environment of the screen being emitted (same run-scoped pattern as
     * [componentFns]): target screen id → the synthesized callback param name,
     * plus the `onBack` param when the screen has a Back action. Click sites
     * consult it; anything unresolved degrades to the empty lambda.
     */
    private var navEnv: Map<String, String> = emptyMap()
    private var backParam: String? = null

    /** A screen's synthesized nav callbacks: signature text + emission environment. */
    private class NavParams(val paramsText: String, val byTarget: Map<String, String>, val back: String?)

    /**
     * Collect [screen]'s nav callbacks in preorder first-use order (Back last).
     * [fnNames] doubles as the liveness set: a Navigate target without a
     * function name (deleted screen) contributes nothing and emits as None.
     */
    private fun navParams(screen: Node.Composable, fnNames: Map<String, String>): NavParams {
        val byTarget = LinkedHashMap<String, String>()
        var back = false
        fun walk(n: Node) {
            when (val a = n.navAction()) {
                is NavAction.Navigate -> fnNames[a.screenId]?.let { byTarget.getOrPut(a.screenId) { "onNavigateTo$it" } }
                NavAction.Back -> back = true
                else -> {}
            }
            n.childNodes().forEach(::walk)
        }
        walk(screen)
        val names = byTarget.values + listOfNotNull(if (back) "onBack" else null)
        val text = if (names.isEmpty()) "()" else names.joinToString(", ", "(", ")") { "$it: () -> Unit = {}" }
        return NavParams(text, byTarget, if (back) "onBack" else null)
    }

    /**
     * The onClick argument when the node's action resolves to a live callback
     * param, else null — a dangling Navigate target emits exactly like None,
     * so canonical forms (plain Card, stateful Chip) stay canonical.
     */
    private fun resolvedNavArg(node: Node): String? = when (val a = node.navAction()) {
        is NavAction.Navigate -> navEnv[a.screenId]?.let { "onClick = $it" }
        NavAction.Back -> backParam?.let { "onClick = $it" }
        else -> null
    }

    /** The canonical onClick argument: a resolved nav callback, else the empty lambda. */
    private fun onClickArg(node: Node): String = resolvedNavArg(node) ?: "onClick = {}"

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
        val (themed, schemeVals, screenNames) = namePlan(artboard)
        val themes = artboard.themes
        val imports = mutableSetOf("androidx.compose.runtime.Composable")

        // Reusable components ARE composables: every Node.Composable generates a
        // function, and a registered one can be instantiated from other composables
        // (instances emit calls to its function). Names must exist BEFORE bodies —
        // an instance in composable A may call composable B.
        val screens = artboard.composables.filterIsInstance<Node.Composable>()
        val compIds = artboard.validComponentIds().toSet()
        componentFns = screens.indices
            .filter { screens[it].id in compIds }
            .associate { screens[it].id to screenNames[it] }
        val fnNameById = screens.indices.associate { screens[it].id to screenNames[it] }
        val fns = screens.mapIndexed { i, screen ->
            val nav = navParams(screen, fnNameById)
            navEnv = nav.byTarget
            backParam = nav.back
            val body = StringBuilder()
            // Per-FUNCTION state counter (state1 restarts in each fun): stateN vars
            // are function-local, and the IDE plugin's write-back regenerates
            // functions in isolation — their text must match full-file output.
            emit(screen, indent = 1, out = body, imports = imports, seq = intArrayOf(0))
            Triple(screenNames[i], nav.paramsText, body.toString())
        }
        componentFns = emptyMap()
        navEnv = emptyMap()
        backParam = null
        val themeBlock = if (themed) themeBlock(themes, schemeVals, artboard.activeTheme, imports) else null

        // Some Material3 components (any TopAppBar variant) are experimental — opt in if used.
        val needsM3OptIn = imports.any { it.removePrefix("androidx.compose.material3.") in M3_EXPERIMENTAL }
        if (needsM3OptIn) imports += "androidx.compose.material3.ExperimentalMaterial3Api"

        return buildString {
            for (imp in imports.sorted()) appendLine("import $imp")
            appendLine()
            if (themeBlock != null) {
                append(themeBlock)
                if (fns.isNotEmpty()) appendLine()
            }
            fns.forEachIndexed { i, (name, params, body) ->
                if (needsM3OptIn) appendLine("@OptIn(ExperimentalMaterial3Api::class)")
                appendLine("@Composable")
                appendLine("fun $name$params {")
                append(body)
                appendLine("}")
                if (i != fns.lastIndex) appendLine()
            }
        }
    }

    /** Material3 composables that need `@OptIn(ExperimentalMaterial3Api::class)`. */
    private val M3_EXPERIMENTAL = setOf("TopAppBar", "CenterAlignedTopAppBar", "MediumTopAppBar", "LargeTopAppBar", "ModalBottomSheet")

    /** One complete generated function declaration + the imports its body needs. */
    data class ScreenCode(val text: String, val imports: Set<String>)

    /**
     * Partial generation for the IDE plugin's write-back: ONE complete
     * `@Composable fun` declaration (no trailing newline) for [screen], named
     * [name], with instance calls resolved through [componentFns] (registered
     * screen id → function name). The text matches what [generate] would emit
     * for this screen inside a full file, including a per-function
     * `@OptIn(ExperimentalMaterial3Api::class)` when its own body needs it.
     */
    fun screenFunction(
        screen: Node.Composable,
        name: String,
        componentFns: Map<String, String> = emptyMap(),
        /** null = synthesize the canonical nav-callback signature; non-null = splice VERBATIM (preserved user signature). */
        params: String? = null,
        /** Nav target screen id → its function name (liveness set for Navigate actions). */
        navFns: Map<String, String> = emptyMap(),
    ): ScreenCode {
        val imports = mutableSetOf("androidx.compose.runtime.Composable")
        this.componentFns = componentFns
        val nav = navParams(screen, navFns)
        val paramsText: String
        if (params == null) {
            paramsText = nav.paramsText
            navEnv = nav.byTarget
            backParam = nav.back
        } else {
            // A preserved user signature can't receive new callbacks: wire only
            // the nav params whose names already appear in it; the rest emit {}.
            paramsText = params
            val declared = identifiersIn(params)
            navEnv = nav.byTarget.filterValues { it in declared }
            backParam = nav.back?.takeIf { it in declared }
        }
        val body = StringBuilder()
        emit(screen, indent = 1, out = body, imports = imports, seq = intArrayOf(0))
        this.componentFns = emptyMap()
        navEnv = emptyMap()
        backParam = null
        val needsOptIn = imports.any { it.removePrefix("androidx.compose.material3.") in M3_EXPERIMENTAL }
        if (needsOptIn) imports += "androidx.compose.material3.ExperimentalMaterial3Api"
        val text = buildString {
            if (needsOptIn) appendLine("@OptIn(ExperimentalMaterial3Api::class)")
            appendLine("@Composable")
            appendLine("fun $name$paramsText {")
            append(body)
            append("}")
        }
        return ScreenCode(text, imports)
    }

    /** All identifier-shaped tokens in a signature text (cheap lexical scan). */
    private fun identifiersIn(text: String): Set<String> {
        val out = mutableSetOf<String>()
        val sb = StringBuilder()
        for (c in text + " ") {
            if (c.isLetterOrDigit() || c == '_') sb.append(c) else {
                if (sb.isNotEmpty() && !sb[0].isDigit()) out += sb.toString()
                sb.clear()
            }
        }
        return out
    }

    /** [sanitizeIdentifier] for callers outside codegen (write-back naming). */
    fun sanitizeName(raw: String): String? = sanitizeIdentifier(raw)

    /**
     * The function names [generate] would emit, parallel to the artboard's
     * screens — including the dedupe suffixes and the `AppTheme`/scheme-val
     * reservations of a themed design. Lets callers (the web code editor's
     * merge step) match screens in emitted code back to model screens exactly.
     */
    fun screenFunctionNames(root: Node): List<String> = namePlan(root.migrateToArtboard()).third

    /**
     * Names shared by one generated file: whether a theme block is emitted, the
     * scheme val names, and the per-screen function names — all drawn from one
     * dedupe set so nothing in the file can collide.
     */
    private fun namePlan(artboard: Node.Artboard): Triple<Boolean, List<String>, List<String>> {
        // Emit the theme block when there are several themes or the single one is
        // customized — an untouched default stays invisible, as before.
        val themes = artboard.themes
        val themed = themes.size > 1 || themes.any { it.theme.isCustomized() }
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
        val screenNames = screens.mapIndexed { i, screen -> functionName(artboard.layerNames[screen.id], i, used) }
        return Triple(themed, schemeVals, screenNames)
    }

    /**
     * A generated function name for one screen: the layer name sanitized to a
     * PascalCase identifier (`"login screen"` → `LoginScreen`; a leading digit is
     * prefixed), `Screen<n>` when unnamed/unsanitizable. Duplicates get a numeric
     * suffix so the file always compiles.
     */
    private fun functionName(layerName: String?, index: Int, used: MutableSet<String>): String {
        val base = layerName?.let { sanitizeIdentifier(it) } ?: "Composable${index + 1}"
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
        return if (joined.first().isDigit()) "Composable$joined" else joined
    }

    /**
     * The theme block: one top-level `ColorScheme` val per named theme (only the
     * tokens the user changed from the baseline-light default are passed, so a
     * bare dark theme emits `darkColorScheme()` and keeps the builder's dark
     * defaults for the rest), plus a shared `AppTheme(colorScheme, content)`
     * wrapper defaulting to the ACTIVE theme — switch themes by passing another
     * scheme (e.g. `AppTheme(DarkColors) { … }`).
     */
    internal fun themeBlock(
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
     * The Compose scope the node's PARENT puts its children in — gates the
     * scope-member modifiers: `weight` needs a Row/Column scope, `align` needs
     * the matching scope kind (Box = 2D, Row = vertical, Column = horizontal).
     */
    private enum class ChildScope { NONE, BOX, ROW, COLUMN }

    private val ChildScope.isLinear get() = this == ChildScope.ROW || this == ChildScope.COLUMN

    /** Project an Align onto [scope]: keep only the matching field, or drop it. */
    private fun projectAlign(spec: ModifierSpec.Align, scope: ChildScope): ModifierSpec.Align? = when (scope) {
        ChildScope.BOX -> spec.box?.let { ModifierSpec.Align(box = it) }
        ChildScope.ROW -> spec.vertical?.let { ModifierSpec.Align(vertical = it) }
        ChildScope.COLUMN -> spec.horizontal?.let { ModifierSpec.Align(horizontal = it) }
        ChildScope.NONE -> null
    }

    /** Emit sibling components with ONE blank line between them (readability). */
    private fun emitSiblings(children: List<Node>, indent: Int, out: StringBuilder, imports: MutableSet<String>, seq: IntArray, scope: ChildScope = ChildScope.NONE, scopeModifier: String? = null) {
        children.forEachIndexed { i, child ->
            if (i > 0) out.appendLine()
            emit(child, indent, out, imports, seq, scope, scopeModifier)
        }
    }

    private fun emit(node: Node, indent: Int, out: StringBuilder, imports: MutableSet<String>, seq: IntArray, scope: ChildScope = ChildScope.NONE, scopeModifier: String? = null) {
        val pad = "    ".repeat(indent)
        // Scope members are stripped/projected per the parent scope: weight only in
        // Row/Column (and weight(<=0) throws), align only where its kind matches.
        val mods = node.modifier.mapNotNull { spec ->
            when {
                spec is ModifierSpec.Weight && (!scope.isLinear || spec.value <= 0f) -> null
                spec is ModifierSpec.Align -> projectAlign(spec, scope)
                else -> spec
            }
        }
        when (node) {
            is Node.RawCode -> {
                // Opaque preserved source (IDE plugin code-import): re-emit VERBATIM,
                // never through esc() — this is code, not a string literal. Lines are
                // re-indented to the current level; text containing a raw string ("""
                // anywhere) keeps its captured indentation untouched, since padding
                // its lines would change the literal's content.
                val lines = node.code.lines()
                val hasRawString = node.code.contains("\"\"\"")
                for (line in lines) {
                    when {
                        line.isBlank() -> out.appendLine()
                        hasRawString -> out.appendLine(line)
                        else -> out.appendLine("$pad$line")
                    }
                }
            }
            is Node.Instance -> {
                val fnName = componentFns[node.refId]
                val mod = modifierExpr(mods, imports, scopeModifier, indent)
                if (fnName == null) {
                    // Dangling reference (the composable was deleted) — keep output compiling.
                    out.appendLine("$pad// Missing component: ${node.refId.replace(Regex("[\\r\\n]"), " ")}")
                } else if (mod == null) {
                    out.appendLine("$pad$fnName()")
                } else {
                    // A composable's body has no root to receive a modifier param, so the
                    // instance's chain wraps the call — exactly what the canvas renders.
                    imports += "androidx.compose.foundation.layout.Box"
                    appendCall(out, indent, "Box", listOf("modifier = $mod"), open = true)
                    out.appendLine("$pad    $fnName()")
                    out.appendLine("$pad}")
                }
            }
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
                val args = listOfNotNull(onClickArg(node), mod?.let { "modifier = $it" })
                appendCall(out, indent, name, args, open = true)
                emitSiblings(node.children, indent + 1, out, imports, seq, ChildScope.ROW)
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
                val mod = modifierExpr(mods, imports, scopeModifier, indent)
                val desc = if (node.contentDescription.isBlank()) "null" else "\"${esc(node.contentDescription)}\""
                val symbol = safeSymbol(node.symbol)
                if (symbol != null) {
                    imports += "org.jetbrains.compose.resources.painterResource"
                    out.appendLine("$pad${symbolComment(symbol)}")
                    appendCall(out, indent, "Icon", listOfNotNull("painterResource(Res.drawable.ic_$symbol)", "contentDescription = $desc", mod?.let { "modifier = $it" }))
                } else {
                    imports += "androidx.compose.material.icons.Icons"
                    imports += "androidx.compose.material.icons.filled.${node.icon.name}"
                    appendCall(out, indent, "Icon", listOfNotNull("Icons.Default.${node.icon.name}", "contentDescription = $desc", mod?.let { "modifier = $it" }))
                }
            }

            is Node.IconButton -> {
                imports += "androidx.compose.material3.IconButton"
                imports += "androidx.compose.material3.Icon"
                val symbol = safeSymbol(node.symbol)
                if (symbol != null) {
                    imports += "org.jetbrains.compose.resources.painterResource"
                } else {
                    imports += "androidx.compose.material.icons.Icons"
                    imports += "androidx.compose.material.icons.filled.${node.icon.name}"
                }
                val mod = modifierExpr(mods, imports, scopeModifier, indent)
                val args = listOfNotNull(onClickArg(node), mod?.let { "modifier = $it" })
                appendCall(out, indent, "IconButton", args, open = true)
                if (symbol != null) {
                    out.appendLine("$pad    ${symbolComment(symbol)}")
                    out.appendLine("$pad    Icon(painterResource(Res.drawable.ic_$symbol), contentDescription = null)")
                } else {
                    out.appendLine("$pad    Icon(Icons.Default.${node.icon.name}, contentDescription = null)")
                }
                out.appendLine("$pad}")
            }

            is Node.TabRow -> {
                imports += "androidx.compose.material3.TabRow"
                imports += "androidx.compose.material3.Tab"
                imports += "androidx.compose.material3.Text"
                stateImports(imports)
                val state = "state${++seq[0]}"
                val mod = modifierExpr(mods, imports, scopeModifier, indent)
                out.appendLine("${pad}var $state by remember { mutableStateOf(${node.selectedIndex}) }")
                appendCall(out, indent, "TabRow", listOfNotNull("selectedTabIndex = $state", mod?.let { "modifier = $it" }), open = true)
                node.children.forEachIndexed { i, child ->
                    if (child is Node.Tab) {
                        // Tab chains never carry scope members (weight/align) — no scope here.
                        val tmod = modifierExpr(child.modifier.filterNot { it is ModifierSpec.Weight || it is ModifierSpec.Align }, imports, null, indent + 1)
                        appendCall(
                            out, indent + 1, "Tab",
                            listOfNotNull(
                                "selected = $state == $i",
                                "onClick = { $state = $i }",
                                "text = { Text(\"${esc(child.label)}\") }",
                                tmod?.let { "modifier = $it" },
                            ),
                        )
                    } else {
                        emit(child, indent + 1, out, imports, seq)
                    }
                }
                out.appendLine("$pad}")
            }

            // Standalone Tab (outside a TabRow — tolerated defensively): a static tab.
            is Node.Tab -> {
                imports += "androidx.compose.material3.Tab"
                imports += "androidx.compose.material3.Text"
                val mod = modifierExpr(mods, imports, scopeModifier, indent)
                appendCall(out, indent, "Tab", listOfNotNull("selected = false", "onClick = {}", "text = { Text(\"${esc(node.label)}\") }", mod?.let { "modifier = $it" }))
            }

            is Node.NavigationBar -> {
                imports += "androidx.compose.material3.NavigationBar"
                imports += "androidx.compose.material3.NavigationBarItem"
                imports += "androidx.compose.material3.Icon"
                imports += "androidx.compose.material3.Text"
                stateImports(imports)
                val state = "state${++seq[0]}"
                val mod = modifierExpr(mods, imports, scopeModifier, indent)
                out.appendLine("${pad}var $state by remember { mutableStateOf(${node.selectedIndex}) }")
                if (mod != null) {
                    appendCall(out, indent, "NavigationBar", listOf("modifier = $mod"), open = true)
                } else {
                    out.appendLine("$pad" + "NavigationBar {")
                }
                node.children.forEachIndexed { i, child ->
                    if (child is Node.NavItem) {
                        val symbol = safeSymbol(child.symbol)
                        if (symbol != null) imports += "org.jetbrains.compose.resources.painterResource"
                        val imod = modifierExpr(child.modifier.filterNot { it is ModifierSpec.Weight || it is ModifierSpec.Align }, imports, null, indent + 1)
                        out.appendLine("$pad    NavigationBarItem(")
                        out.appendLine("$pad        selected = $state == $i,")
                        out.appendLine("$pad        onClick = { $state = $i },")
                        if (symbol != null) {
                            out.appendLine("$pad        icon = {")
                            out.appendLine("$pad            ${symbolComment(symbol)}")
                            out.appendLine("$pad            Icon(painterResource(Res.drawable.ic_$symbol), contentDescription = null)")
                            out.appendLine("$pad        },")
                        } else {
                            out.appendLine("$pad        icon = {},")
                        }
                        out.appendLine("$pad        label = { Text(\"${esc(child.label)}\") },")
                        imod?.let { out.appendLine("$pad        modifier = $it,") }
                        out.appendLine("$pad    )")
                    } else {
                        emit(child, indent + 1, out, imports, seq)
                    }
                }
                out.appendLine("$pad}")
            }

            // A NavigationBarItem is a RowScope member of NavigationBar — it cannot
            // compile anywhere else, so a stray one degrades to a comment.
            is Node.NavItem -> {
                out.appendLine("$pad// NavItem \"${node.label.replace(Regex("[\\r\\n]"), " ")}\" must live inside a NavigationBar")
            }

            is Node.Chip -> {
                val name = chipComposable(node.variant)
                imports += "androidx.compose.material3.$name"
                imports += "androidx.compose.material3.Text"
                // A chip that navigates doesn't toggle: state hoisting is suppressed
                // and `selected` emits as the literal (the standalone-Tab precedent).
                val selectable = node.variant == ChipVariant.Filter || node.variant == ChipVariant.Input
                val stateful = selectable && resolvedNavArg(node) == null
                val state = if (stateful) "state${++seq[0]}" else null
                if (state != null) {
                    stateImports(imports)
                    out.appendLine("${pad}var $state by remember { mutableStateOf(${node.selected}) }")
                }
                val mod = modifierExpr(mods, imports, scopeModifier, indent)
                val symbol = safeSymbol(node.symbol)
                val iconParam = if (node.variant == ChipVariant.Suggestion) "icon" else "leadingIcon"
                val head = listOfNotNull(
                    state?.let { "selected = $it" } ?: if (selectable) "selected = ${node.selected}" else null,
                    if (state != null) "onClick = { $state = !$state }" else onClickArg(node),
                    "label = { Text(\"${esc(node.label)}\") }",
                )
                if (symbol == null) {
                    appendCall(out, indent, name, head + listOfNotNull(mod?.let { "modifier = $it" }))
                } else {
                    imports += "androidx.compose.material3.Icon"
                    imports += "org.jetbrains.compose.resources.painterResource"
                    out.appendLine("$pad$name(")
                    head.forEach { out.appendLine("$pad    $it,") }
                    out.appendLine("$pad    $iconParam = {")
                    out.appendLine("$pad        ${symbolComment(symbol)}")
                    out.appendLine("$pad        Icon(painterResource(Res.drawable.ic_$symbol), contentDescription = null)")
                    out.appendLine("$pad    },")
                    mod?.let { out.appendLine("$pad    modifier = $it,") }
                    out.appendLine("$pad)")
                }
            }

            is Node.BadgedBox -> {
                imports += "androidx.compose.material3.BadgedBox"
                imports += "androidx.compose.material3.Badge"
                val mod = modifierExpr(mods, imports, scopeModifier, indent)
                val badge = if (node.badge.isEmpty()) {
                    "badge = { Badge() }"
                } else {
                    imports += "androidx.compose.material3.Text"
                    "badge = { Badge { Text(\"${esc(node.badge)}\") } }"
                }
                appendCall(out, indent, "BadgedBox", listOfNotNull(badge, mod?.let { "modifier = $it" }), open = true)
                emitSiblings(node.children, indent + 1, out, imports, seq, ChildScope.BOX)
                out.appendLine("$pad}")
            }

            is Node.Canvas -> {
                imports += "androidx.compose.foundation.Canvas"
                imports += "androidx.compose.ui.Modifier"
                imports += "androidx.compose.ui.unit.dp"
                val mod = modifierExpr(mods, imports, scopeModifier, indent) ?: "Modifier" // Canvas has no default modifier param
                out.appendLine("$pad" + "Canvas(modifier = $mod) {")
                for (shape in node.children) {
                    val call = shapeCall(shape, imports)
                    if (call != null) {
                        out.appendLine("$pad    $call")
                    } else {
                        out.appendLine("$pad    // Unsupported canvas child: ${shape.typeName()}")
                    }
                }
                out.appendLine("$pad}")
            }

            // Shapes are draw calls, not composables — a stray one outside a Canvas
            // degrades to a comment (canParent prevents this in the editor).
            is Node.Line, is Node.RectShape, is Node.CircleShape, is Node.EllipseShape, is Node.ArcShape ->
                out.appendLine("$pad// ${node.typeName()} shapes must live inside a Canvas")

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

            is Node.Card -> if (resolvedNavArg(node) == null) {
                emitContainer(
                    "Card", "androidx.compose.material3.Card",
                    mods, node.children, indent, out, imports, seq = seq, childScope = ChildScope.NONE,
                    scopeModifier = scopeModifier,
                )
            } else {
                // The M3 clickable overload; onClick leads like Button/Fab.
                imports += "androidx.compose.material3.Card"
                val mod = modifierExpr(mods, imports, scopeModifier, indent)
                val args = listOfNotNull(onClickArg(node), mod?.let { "modifier = $it" })
                appendCall(out, indent, "Card", args, open = true)
                emitSiblings(node.children, indent + 1, out, imports, seq)
                out.appendLine("$pad}")
            }

            is Node.Fab -> {
                imports += "androidx.compose.material3.FloatingActionButton"
                val mod = modifierExpr(mods, imports, scopeModifier, indent)
                val args = listOfNotNull(onClickArg(node), mod?.let { "modifier = $it" })
                appendCall(out, indent, "FloatingActionButton", args, open = true)
                emitSiblings(node.children, indent + 1, out, imports, seq)
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
                emitSiblings(node.children, indent + 3, out, imports, seq, ChildScope.COLUMN)
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
                emitSiblings(node.children, indent + 2, out, imports, seq, ChildScope.COLUMN)
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
                        emitSiblings(kids, indent + 2, out, imports, seq)
                        out.appendLine("$pad    },")
                    }
                    slot("topBar", topKids)
                    slot("bottomBar", bottomKids)
                    slot("floatingActionButton", fabKids)
                    out.appendLine("$pad)$lambdaOpen")
                } else {
                    out.appendLine("$pad" + "Scaffold$lambdaOpen")
                }
                emitSiblings(node.children, indent + 1, out, imports, seq, scopeModifier = contentScope)
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
                    mods, node.children, indent, out, imports, seq = seq, childScope = ChildScope.COLUMN, extraArgs = extra,
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
                    mods, node.children, indent, out, imports, seq = seq, childScope = ChildScope.ROW, extraArgs = extra,
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
                    mods, node.children, indent, out, imports, seq = seq, childScope = ChildScope.BOX, extraArgs = extra,
                    scopeModifier = scopeModifier,
                )
            }

            is Node.TopAppBar -> {
                val name = topBarComposable(node.variant)
                imports += "androidx.compose.material3.$name"
                val mod = modifierExpr(mods, imports, scopeModifier, indent)
                out.appendLine("$pad" + "$name(")
                out.appendLine("$pad    title = {")
                node.title?.let { emit(it, indent + 2, out, imports, seq = seq) }
                out.appendLine("$pad    },")
                node.navigationIcon?.let {
                    out.appendLine("$pad    navigationIcon = {")
                    emit(it, indent + 2, out, imports, seq = seq)
                    out.appendLine("$pad    },")
                }
                if (node.actions.isNotEmpty()) {
                    out.appendLine("$pad    actions = {")
                    emitSiblings(node.actions, indent + 2, out, imports, seq)
                    out.appendLine("$pad    },")
                }
                mod?.let { out.appendLine("$pad    modifier = $it,") }
                out.appendLine("$pad)")
            }

            // A Composable is the function scope — emit its children directly, no wrapper.
            is Node.Composable -> emitSiblings(node.children, indent, out, imports, seq)
            // A Slot is a slot-argument scope — normally emitted by its parent
            // (e.g. the Scaffold branch); defensively emit children directly.
            is Node.Slot -> emitSiblings(node.children, indent, out, imports, seq)
            // The artboard is handled by [generate]; defensively emit its screens' bodies.
            is Node.Artboard -> for (screen in node.composables) emit(screen, indent, out, imports, seq = seq)
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
        childScope: ChildScope,
        extraArgs: List<String> = emptyList(),
        scopeModifier: String? = null,
    ) {
        imports += import
        val pad = "    ".repeat(indent)
        val mod = modifierExpr(modifier, imports, scopeModifier, indent)
        val args = listOfNotNull(mod?.let { "modifier = $it" }) + extraArgs
        if (args.isEmpty()) out.appendLine("$pad$name {") else appendCall(out, indent, name, args, open = true)
        emitSiblings(children, indent + 1, out, imports, seq, childScope)
        out.appendLine("$pad}")
    }

    /**
     * A [Node.Icon.symbol] sanitized to a resource-safe form (`[a-z0-9_]`), or null
     * when empty. Both the emitted `Res.drawable.ic_<x>` and the sourcing comment
     * use the SAME sanitized name, so the parser round-trips byte-identically.
     */
    fun safeSymbol(symbol: String): String? = symbol
        .lowercase()
        .replace(Regex("[^a-z0-9_]"), "_")
        .takeIf { it.isNotBlank() && it.any { c -> c != '_' } }

    /** The sourcing note emitted above a symbol icon — a fixed, parseable format. */
    fun symbolComment(symbol: String): String =
        "// Icon \"$symbol\" — Material Symbols: download ic_$symbol.xml from https://fonts.google.com/icons into your resources."

    /** `N.dp.toPx()` (negatives parenthesized so the parser sees one receiver). */
    private fun dpPx(n: Int): String = if (n < 0) "($n).dp.toPx()" else "$n.dp.toPx()"

    /** Shape colors are draw-time — theme tokens can't be referenced there; fall back to black. */
    private fun shapeColorExpr(value: Long, imports: MutableSet<String>): String =
        colorExpr(if (ThemeColorRef.tokenName(value) != null) 0xFF000000 else value, imports)

    /** One DrawScope call for a shape leaf inside a [Node.Canvas], or null for non-shapes. */
    private fun shapeCall(shape: Node, imports: MutableSet<String>): String? {
        fun stroke(width: Int): String {
            imports += "androidx.compose.ui.graphics.drawscope.Stroke"
            return "style = Stroke(${dpPx(width)})"
        }
        fun offset(x: Int, y: Int): String {
            imports += "androidx.compose.ui.geometry.Offset"
            return "Offset(${dpPx(x)}, ${dpPx(y)})"
        }
        fun size(w: Int, h: Int): String {
            imports += "androidx.compose.ui.geometry.Size"
            return "Size(${dpPx(w)}, ${dpPx(h)})"
        }
        return when (shape) {
            is Node.Line -> "drawLine(${shapeColorExpr(shape.color, imports)}, start = ${offset(shape.x1, shape.y1)}, end = ${offset(shape.x2, shape.y2)}, strokeWidth = ${dpPx(shape.strokeWidth)})"
            is Node.RectShape -> {
                val style = if (shape.filled) null else stroke(shape.strokeWidth)
                if (shape.corner > 0) {
                    imports += "androidx.compose.ui.geometry.CornerRadius"
                    listOfNotNull(
                        shapeColorExpr(shape.color, imports),
                        "topLeft = ${offset(shape.x, shape.y)}",
                        "size = ${size(shape.width, shape.height)}",
                        "cornerRadius = CornerRadius(${dpPx(shape.corner)})",
                        style,
                    ).joinToString(", ", prefix = "drawRoundRect(", postfix = ")")
                } else {
                    listOfNotNull(
                        shapeColorExpr(shape.color, imports),
                        "topLeft = ${offset(shape.x, shape.y)}",
                        "size = ${size(shape.width, shape.height)}",
                        style,
                    ).joinToString(", ", prefix = "drawRect(", postfix = ")")
                }
            }
            is Node.CircleShape -> listOfNotNull(
                shapeColorExpr(shape.color, imports),
                "radius = ${dpPx(shape.radius)}",
                "center = ${offset(shape.cx, shape.cy)}",
                if (shape.filled) null else stroke(shape.strokeWidth),
            ).joinToString(", ", prefix = "drawCircle(", postfix = ")")
            is Node.EllipseShape -> listOfNotNull(
                shapeColorExpr(shape.color, imports),
                "topLeft = ${offset(shape.x, shape.y)}",
                "size = ${size(shape.width, shape.height)}",
                if (shape.filled) null else stroke(shape.strokeWidth),
            ).joinToString(", ", prefix = "drawOval(", postfix = ")")
            is Node.ArcShape -> listOfNotNull(
                shapeColorExpr(shape.color, imports),
                "startAngle = ${shape.startAngle}f",
                "sweepAngle = ${shape.sweepAngle}f",
                "useCenter = ${shape.filled}",
                "topLeft = ${offset(shape.x, shape.y)}",
                "size = ${size(shape.width, shape.height)}",
                if (shape.filled) null else stroke(shape.strokeWidth),
            ).joinToString(", ", prefix = "drawArc(", postfix = ")")
            else -> null
        }
    }

    private fun chipComposable(v: ChipVariant): String = when (v) {
        ChipVariant.Assist -> "AssistChip"
        ChipVariant.Filter -> "FilterChip"
        ChipVariant.Input -> "InputChip"
        ChipVariant.Suggestion -> "SuggestionChip"
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

                is ModifierSpec.Rotate -> {
                    imports += "androidx.compose.ui.draw.rotate"
                    "rotate(${spec.degrees}f)"
                }

                is ModifierSpec.Scale -> {
                    imports += "androidx.compose.ui.draw.scale"
                    if (spec.x == spec.y) "scale(${spec.x}f)" else "scale(${spec.x}f, ${spec.y}f)"
                }

                // align() is a Box/Row/Column scope member (no import, like weight);
                // emit() has already projected the spec onto the parent scope.
                is ModifierSpec.Align -> {
                    imports += "androidx.compose.ui.Alignment"
                    val alignment = spec.box?.let { "Alignment.${it.name}" }
                        ?: spec.vertical?.let { vAlignmentCode(it) }
                        ?: spec.horizontal?.let { hAlignmentCode(it) }
                        ?: "Alignment.Center" // unreachable post-projection
                    "align($alignment)"
                }

                is ModifierSpec.ZIndex -> {
                    imports += "androidx.compose.ui.zIndex"
                    "zIndex(${spec.value}f)"
                }

                is ModifierSpec.Blur -> {
                    imports += "androidx.compose.ui.draw.blur"
                    imports += "androidx.compose.ui.unit.dp"
                    "blur(${spec.radius}.dp)"
                }

                is ModifierSpec.FillMaxWidth -> {
                    imports += "androidx.compose.foundation.layout.fillMaxWidth"
                    if (spec.fraction == 1f) "fillMaxWidth()" else "fillMaxWidth(${spec.fraction}f)"
                }

                is ModifierSpec.FillMaxHeight -> {
                    imports += "androidx.compose.foundation.layout.fillMaxHeight"
                    if (spec.fraction == 1f) "fillMaxHeight()" else "fillMaxHeight(${spec.fraction}f)"
                }

                is ModifierSpec.FillMaxSize -> {
                    imports += "androidx.compose.foundation.layout.fillMaxSize"
                    if (spec.fraction == 1f) "fillMaxSize()" else "fillMaxSize(${spec.fraction}f)"
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
