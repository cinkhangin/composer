package composer.codeparse

import composer.model.NavAction
import composer.model.Node

/**
 * Kotlin source → Composer design tree: one [Node.Composable] screen per parseable
 * top-level `@Composable` function; everything the model can't represent inside a
 * body becomes a locked [Node.RawCode]. Purely syntactic (no resolve, no PSI —
 * the hand-rolled scanner in Lexer/FileScanner/StatementParser is plain
 * Kotlin/JVM) — fast, dumb-mode-safe, and honest: the failure
 * mode of the accepted ambiguities is a standard-Compose regeneration of a
 * function the user explicitly edited.
 *
 * A function is a screen iff: top-level, `@Composable`, no receiver, no type
 * parameters, no explicit return type, block body. Anything else in the file
 * (theme vals, `AppTheme`, helpers, other declarations) is untouched file text
 * the write-back never rewrites.
 */
object DesignParser {

    /** A parseable top-level `@Composable` declaration discovered in source order. */
    data class ComposableFunctionRef(val name: String, val startOffset: Int)

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
     * Cross-file name environment for app-mode parsing: instance calls and
     * `onNavigateTo<X>` params resolve through the app's GLOBAL maps instead of
     * this file's functions, and screen ids are assigned by the app plan.
     */
    class ExternalNames(
        /** Callable composable name → screen id (instances; e.g. `HomeScreenUI` → s2). */
        val screenIdsByName: Map<String, String>,
        /** Nav base name → screen id (`onNavigateToHome` → s2). */
        val navBaseToScreenId: Map<String, String>,
        /** THIS file's function name → its fixed screen id; other fns aren't screens. */
        val fixedScreenIds: Map<String, String> = emptyMap(),
        /**
         * Declaration start offset → fixed id. Module discovery uses offsets so
         * overloads with the same name can still be rendered independently.
         */
        val fixedScreenIdsByOffset: Map<Int, String> = emptyMap(),
        /** Node-id prefix keeping ids unique across the app's separately parsed files. */
        val idPrefix: String = "",
    )

    /** Discover every function shape [parse] can render, excluding AppTheme. */
    fun composableFunctions(text: String): List<ComposableFunctionRef> =
        scanSource(text).declarations
            .filterIsInstance<KFunctionDecl>()
            .filter { isScreenFunction(it) && !isAppThemeWrapper(it) }
            .mapNotNull { fn -> fn.name?.let { ComposableFunctionRef(it, fn.range.first) } }

    /**
     * Parse [text], or null when it has no screen-shaped `@Composable` function.
     */
    fun parse(text: String, external: ExternalNames? = null): ParsedDesign? {
        val file = scanSource(text)
        val functions = file.declarations
            .filterIsInstance<KFunctionDecl>()
            .filter { isScreenFunction(it) && !isAppThemeWrapper(it) }
            .filter {
                external == null ||
                    it.range.first in external.fixedScreenIdsByOffset ||
                    it.name in external.fixedScreenIds
            }
        if (functions.isEmpty()) return null

        val ctx = ParseCtx(
            text = text,
            comments = file.comments,
            blockedNames = blockedNames(file),
            screenIdsByName = external?.screenIdsByName
                ?: functions.mapNotNull { it.name }.distinct()
                    .withIndex()
                    .associate { (i, name) -> name to "s${i + 1}" },
            idPrefix = external?.idPrefix ?: "",
        )
        val fixedIds = external?.fixedScreenIds
        val navNames = external?.navBaseToScreenId ?: ctx.screenIdsByName

        val screens = mutableListOf<Node.Composable>()
        val parsedFns = mutableListOf<ParsedFunction>()
        val layerNames = mutableMapOf<String, String>()
        functions.forEachIndexed { i, fn ->
            val name = fn.name ?: return@forEachIndexed
            val screenId = external?.fixedScreenIdsByOffset?.get(fn.range.first)
                ?: fixedIds?.getValue(name)
                ?: ctx.screenIdsByName.getValue(name)
            val body = fn.bodyBlock ?: return@forEachIndexed
            val navParams = parseNavParamList(fn.paramListText, navNames)
            ctx.navParams = navParams ?: emptyMap()
            val children = parseBlock(body, ctx)
            ctx.navParams = emptyMap()
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
                paramsCanonical = navParams != null,
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
            hasNonScreenDeclarations = file.declarations.any {
                isForeignDeclaration(it) ||
                    (external != null && it is KFunctionDecl && isScreenFunction(it) &&
                        !isAppThemeWrapper(it) &&
                        it.range.first !in external.fixedScreenIdsByOffset &&
                        it.name !in external.fixedScreenIds)
            },
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

    private val NAV_PARAM = Regex("""^([A-Za-z_][A-Za-z0-9_]*)\s*:\s*\(\)\s*->\s*Unit\s*=\s*\{\}$""")

    /**
     * The exact inverse of codegen's synthesized nav-callback signature:
     * `()` or `(name: () -> Unit = {}, …)` where every name is `onBack` or
     * `onNavigateTo<Fn>` resolving to a screen in this file. Returns the
     * name → action map, or null for any other (user-authored) signature —
     * which is then preserved verbatim, exactly as before.
     */
    private fun parseNavParamList(paramsText: String, screenIdsByName: Map<String, String>): Map<String, NavAction>? {
        val trimmed = paramsText.trim()
        if (!trimmed.startsWith("(") || !trimmed.endsWith(")")) return null
        val inner = trimmed.substring(1, trimmed.length - 1).trim()
        if (inner.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, NavAction>()
        for (part in inner.split(',')) {
            val m = NAV_PARAM.matchEntire(part.trim()) ?: return null
            val name = m.groupValues[1]
            val action = when {
                name == "onBack" -> NavAction.Back
                name.startsWith("onNavigateTo") -> {
                    val target = name.removePrefix("onNavigateTo")
                    NavAction.Navigate(screenIdsByName[target] ?: return null)
                }
                else -> return null
            }
            if (out.put(name, action) != null) return null
        }
        return out
    }

    private fun importInsertOffset(file: KSourceFile): Int =
        file.imports.lastOrNull()?.endOffset
            ?: file.packageEndOffset
            ?: 0
}
