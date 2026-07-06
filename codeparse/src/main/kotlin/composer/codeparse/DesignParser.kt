package composer.codeparse

import composer.model.Node
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Kotlin file → Composer design tree: one [Node.Composable] screen per parseable
 * top-level `@Composable` function; everything the model can't represent inside a
 * body becomes a locked [Node.RawCode]. Purely syntactic (no resolve) — fast,
 * dumb-mode-safe, and honest: the failure mode of the accepted ambiguities is a
 * standard-Compose regeneration of a function the user explicitly edited.
 *
 * A function is a screen iff: top-level, `@Composable`, zero value parameters,
 * no receiver, no type parameters, no explicit return type, block body. Anything
 * else in the file (theme vals, `AppTheme`, helpers, other declarations) is
 * untouched file text the write-back never rewrites.
 */
object DesignParser {

    /** Names the parser recognizes → the FQN codegen imports them from. */
    private val EXPECTED_FQNS: Map<String, Set<String>> = buildMap<String, Set<String>> {
        fun put(fqn: String, vararg names: String) {
            for (n in names) merge(n, setOf(fqn)) { a, b -> a + b }
        }
        for (n in listOf(
            "Text", "Button", "ElevatedButton", "FilledTonalButton", "OutlinedButton", "TextButton",
            "Icon", "IconButton", "OutlinedTextField", "Switch", "Checkbox", "RadioButton", "Slider",
            "CircularProgressIndicator", "LinearProgressIndicator", "Card", "Scaffold", "Surface",
            "TopAppBar", "CenterAlignedTopAppBar", "MediumTopAppBar", "LargeTopAppBar",
            "ModalBottomSheet", "HorizontalDivider", "Divider", "FloatingActionButton",
        )) put("androidx.compose.material3.$n", n)
        for (n in listOf("Column", "Row", "Box", "Spacer")) put("androidx.compose.foundation.layout.$n", n)
        put("androidx.compose.foundation.Image", "Image")
        put("androidx.compose.ui.window.Dialog", "Dialog")
        put("coil3.compose.AsyncImage", "AsyncImage")
    }

    /**
     * Parse [file], or null when it has no screen-shaped `@Composable` function.
     */
    fun parse(file: KtFile): ParsedDesign? {
        val functions = file.declarations
            .filterIsInstance<KtNamedFunction>()
            .filter { isScreenFunction(it) }
        if (functions.isEmpty()) return null

        val imports = file.importDirectives.mapNotNull { it.importPath?.pathStr }
        val ctx = ParseCtx(
            blockedNames = blockedNames(file),
            screenIdsByName = functions.associate { (it.name ?: "") to "" }.keys
                .filter { it.isNotEmpty() }
                .withIndex()
                .associate { (i, name) -> name to "s${i + 1}" },
        )

        val screens = mutableListOf<Node.Composable>()
        val parsedFns = mutableListOf<ParsedFunction>()
        val layerNames = mutableMapOf<String, String>()
        functions.forEachIndexed { i, fn ->
            val name = fn.name ?: return@forEachIndexed
            val screenId = ctx.screenIdsByName.getValue(name)
            val body = fn.bodyBlockExpression ?: return@forEachIndexed
            val children = parseBlock(body, ctx)
            val screen = Node.Composable(
                id = screenId,
                children = children,
                x = i * 470,
                y = 0,
            )
            screens += screen
            layerNames[screenId] = name
            parsedFns += ParsedFunction(
                screenId = screenId,
                functionName = name,
                fnRange = fn.textRange.startOffset until fn.textRange.endOffset,
                treeHash = ParsedFunction.hashOf(screen),
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
            existingImports = imports,
            importInsertOffset = importInsertOffset(file),
            topLevelFunctionNames = file.declarations.filterIsInstance<KtNamedFunction>().mapNotNull { it.name },
        )
    }

    private fun isScreenFunction(fn: KtNamedFunction): Boolean =
        fn.name != null &&
            fn.annotationEntries.any { it.shortName?.asString() == "Composable" } &&
            fn.valueParameters.isEmpty() &&
            fn.receiverTypeReference == null &&
            fn.typeParameters.isEmpty() &&
            fn.typeReference == null &&
            fn.bodyBlockExpression != null

    /**
     * Simple names disabled file-wide: an explicit import binds the name to a
     * different FQN than codegen's (e.g. `import my.ds.Text`). Star imports and
     * unimported names stay usable (syntactic best-effort).
     */
    private fun blockedNames(file: KtFile): Set<String> {
        val blocked = mutableSetOf<String>()
        for (directive in file.importDirectives) {
            val path = directive.importPath ?: continue
            if (path.isAllUnder) continue
            val bound = path.alias?.identifier ?: path.fqName?.shortName()?.asString() ?: continue
            val expected = EXPECTED_FQNS[bound] ?: continue
            val fqn = path.fqName?.asString() ?: continue
            if (fqn !in expected || path.alias != null) blocked += bound
        }
        return blocked
    }

    private fun importInsertOffset(file: KtFile): Int =
        file.importDirectives.lastOrNull()?.textRange?.endOffset
            ?: file.packageDirective?.textRange?.endOffset
            ?: 0
}
