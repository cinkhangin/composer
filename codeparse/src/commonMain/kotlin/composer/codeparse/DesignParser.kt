package composer.codeparse

import composer.model.Node

/**
 * Kotlin source → Composer design tree: one [Node.Composable] screen per parseable
 * top-level `@Composable` function; everything the model can't represent inside a
 * body becomes a locked [Node.RawCode]. Purely syntactic (no resolve, no PSI —
 * the hand-rolled scanner in Lexer/FileScanner/StatementParser runs on any
 * Kotlin target, including Wasm) — fast, dumb-mode-safe, and honest: the failure
 * mode of the accepted ambiguities is a standard-Compose regeneration of a
 * function the user explicitly edited.
 *
 * A function is a screen iff: top-level, `@Composable`, no receiver, no type
 * parameters, no explicit return type, block body. Anything else in the file
 * (theme vals, `AppTheme`, helpers, other declarations) is untouched file text
 * the write-back never rewrites.
 */
object DesignParser {

    /** Names the parser recognizes → the FQN codegen imports them from. */
    private val EXPECTED_FQNS: Map<String, Set<String>> = buildMap<String, Set<String>> {
        fun put(fqn: String, vararg names: String) {
            for (n in names) this[n] = (this[n] ?: emptySet()) + fqn
        }
        for (n in listOf(
            "Text", "Button", "ElevatedButton", "FilledTonalButton", "OutlinedButton", "TextButton",
            "Icon", "IconButton", "OutlinedTextField", "Switch", "Checkbox", "RadioButton", "Slider",
            "CircularProgressIndicator", "LinearProgressIndicator", "Card", "Scaffold", "Surface",
            "TopAppBar", "CenterAlignedTopAppBar", "MediumTopAppBar", "LargeTopAppBar",
            "ModalBottomSheet", "HorizontalDivider", "Divider", "FloatingActionButton",
            "TabRow", "Tab", "NavigationBar", "NavigationBarItem",
            "AssistChip", "FilterChip", "InputChip", "SuggestionChip", "BadgedBox", "Badge",
        )) put("androidx.compose.material3.$n", n)
        for (n in listOf("Column", "Row", "Box", "Spacer")) put("androidx.compose.foundation.layout.$n", n)
        put("androidx.compose.foundation.Canvas", "Canvas")
        put("androidx.compose.foundation.Image", "Image")
        put("androidx.compose.ui.window.Dialog", "Dialog")
        put("coil3.compose.AsyncImage", "AsyncImage")
    }

    /**
     * Parse [text], or null when it has no screen-shaped `@Composable` function.
     */
    fun parse(text: String): ParsedDesign? {
        val file = scanSource(text)
        val functions = file.declarations
            .filterIsInstance<KFunctionDecl>()
            .filter { isScreenFunction(it) && !isAppThemeWrapper(it) }
        if (functions.isEmpty()) return null

        val ctx = ParseCtx(
            text = text,
            comments = file.comments,
            blockedNames = blockedNames(file),
            screenIdsByName = functions.mapNotNull { it.name }.distinct()
                .withIndex()
                .associate { (i, name) -> name to "s${i + 1}" },
        )

        val screens = mutableListOf<Node.Composable>()
        val parsedFns = mutableListOf<ParsedFunction>()
        val layerNames = mutableMapOf<String, String>()
        functions.forEachIndexed { i, fn ->
            val name = fn.name ?: return@forEachIndexed
            val screenId = ctx.screenIdsByName.getValue(name)
            val body = fn.bodyBlock ?: return@forEachIndexed
            val children = parseBlock(body, ctx)
            val screen = Node.Composable(
                id = screenId,
                children = children,
                x = i * 470,
                y = 0,
            )
            screens += screen
            layerNames[screenId] = name
            ctx.record(screenId, fn.range)
            parsedFns += ParsedFunction(
                screenId = screenId,
                functionName = name,
                fnRange = fn.range,
                treeHash = ParsedFunction.hashOf(screen),
                paramList = fn.paramListText,
            )
        }
        if (screens.isEmpty()) return null

        val artboard = Node.Artboard(
            id = "artboard",
            composables = screens,
            layerNames = layerNames,
            componentIds = ctx.referencedScreenIds.toList(),
        )
        return ParsedDesign(
            artboard = artboard,
            functions = parsedFns,
            existingImports = file.imports.map { it.pathStr },
            importInsertOffset = importInsertOffset(file),
            topLevelFunctionNames = file.declarations.filterIsInstance<KFunctionDecl>().mapNotNull { it.name },
            sourceRanges = ctx.sourceRanges.toMap(),
            hasNonScreenDeclarations = file.declarations.any { isForeignDeclaration(it) },
        )
    }

    /**
     * File-level bookkeeping of [text] with NO screens parsed — the `previous`
     * for a write-back into a file the designer hasn't seen yet.
     */
    fun skeleton(text: String): ParsedDesign {
        val file = scanSource(text)
        return ParsedDesign(
            artboard = Node.Artboard(id = "artboard"),
            functions = emptyList(),
            existingImports = file.imports.map { it.pathStr },
            importInsertOffset = importInsertOffset(file),
            topLevelFunctionNames = file.declarations.filterIsInstance<KFunctionDecl>().mapNotNull { it.name },
            hasNonScreenDeclarations = file.declarations.any { isForeignDeclaration(it) },
        )
    }

    /**
     * Codegen's shared theme wrapper — screen-shaped (top-level `@Composable`
     * with a block body), but it's emitted FROM the design's themes, so parsing
     * it back as a screen would duplicate it on every round trip.
     */
    private fun isAppThemeWrapper(fn: KFunctionDecl): Boolean =
        fn.name == "AppTheme" && "Composable" in fn.annotationNames && "content" in fn.paramListText

    /** Top-level code regeneration would NOT reproduce (helpers, classes, user vals). */
    private fun isForeignDeclaration(d: KDeclaration): Boolean = when (d) {
        is KFunctionDecl -> !isScreenFunction(d) && !isAppThemeWrapper(d)
        is KOtherDecl -> !d.themeArtifact
    }

    // Value parameters ARE allowed (the signature is preserved verbatim on
    // write-back); statements that reference them simply become RawCode.
    private fun isScreenFunction(fn: KFunctionDecl): Boolean =
        fn.name != null &&
            "Composable" in fn.annotationNames &&
            !fn.hasReceiver &&
            !fn.hasTypeParams &&
            !fn.hasReturnType &&
            fn.bodyBlock != null

    /**
     * Simple names disabled file-wide: an explicit import binds the name to a
     * different FQN than codegen's (e.g. `import my.ds.Text`). Star imports and
     * unimported names stay usable (syntactic best-effort).
     */
    private fun blockedNames(file: KSourceFile): Set<String> {
        val blocked = mutableSetOf<String>()
        for (import in file.imports) {
            if (import.isAllUnder) continue
            val bound = import.alias ?: import.fqName.substringAfterLast('.')
            val expected = EXPECTED_FQNS[bound] ?: continue
            if (import.fqName !in expected || import.alias != null) blocked += bound
        }
        return blocked
    }

    private fun importInsertOffset(file: KSourceFile): Int =
        file.imports.lastOrNull()?.endOffset
            ?: file.packageEndOffset
            ?: 0
}
