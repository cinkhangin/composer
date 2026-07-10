package composer.codegen

import composer.model.Node
import composer.model.hasBackAction
import composer.model.migrateToArtboard
import composer.model.navTargets
import composer.model.validComponentIds

/** One generated source file; [path] is relative to the source-root package dir. */
data class GeneratedFile(val path: String, val text: String)

/**
 * The app-wide naming plan, all names drawn from ONE dedupe set (reserving
 * MainActivity/App/AppTheme and the theme scheme vals) so nothing in the
 * generated file set can collide. [bases] is parallel to the artboard's
 * screens; every derived name comes from the base: route object `Login`,
 * wiring fn/file `LoginScreen`, UI fn/file `LoginScreenUI`, `LoginViewModel`,
 * `LoginUiState`.
 */
data class AppNamePlan(
    val bases: List<String>,
    val themed: Boolean,
    val schemeVals: List<String>,
)

/**
 * Whole-app generation (IntelliJ plugin track): per screen THREE files —
 * `<Base>Screen.kt` (wiring: ViewModel state → UI call), `<Base>ScreenUI.kt`
 * (the designed stateless UI — the file the designer regenerates on edit),
 * `<Base>ViewModel.kt` (ViewModel + UiState skeleton) — plus `MainActivity.kt`
 * with Navigation 3 (`rememberNavBackStack` + `NavDisplay` + `entryProvider`,
 * `@Serializable data object <Base> : NavKey` routes) and the theme block.
 *
 * All files live in ONE package: same-package top-level declarations resolve
 * without imports, so cross-file instance calls and the wiring→UI→VM
 * references need zero import logic. Templates are canonical and comment-free
 * — AppParser byte-compares wiring/VM/MainActivity against regeneration to
 * decide whether a file is still designer-owned.
 */
object AppCodeGen {

    fun generate(root: Node, packageName: String): List<GeneratedFile> {
        val artboard = root.migrateToArtboard()
        val plan = appNamePlan(artboard)
        val screens = artboard.composables.filterIsInstance<Node.Composable>()
        val baseById = screens.indices.associate { screens[it].id to plan.bases[it] }

        // Instances call the stateless UI composable of their target screen.
        val compIds = artboard.validComponentIds().toSet()
        val componentFns = screens.indices
            .filter { screens[it].id in compIds }
            .associate { screens[it].id to "${plan.bases[it]}ScreenUI" }

        val files = mutableListOf<GeneratedFile>()
        screens.forEachIndexed { i, screen ->
            val base = plan.bases[i]
            files += GeneratedFile("${base}ScreenUI.kt", screenUiFile(screen, base, packageName, componentFns, baseById))
            files += GeneratedFile("${base}Screen.kt", wiringFile(screen, base, packageName, artboard, baseById))
            files += GeneratedFile("${base}ViewModel.kt", viewModelFile(base, packageName))
        }
        files += GeneratedFile("MainActivity.kt", mainActivityFile(artboard, plan, packageName, baseById))
        return files
    }

    fun appNamePlan(root: Node): AppNamePlan {
        val artboard = root.migrateToArtboard()
        val themes = artboard.themes
        val themed = themes.size > 1 || themes.any { it.theme.isCustomized() }
        val used = mutableSetOf("MainActivity", "App", "AppTheme")
        val schemeVals = if (themed) {
            themes.mapIndexed { i, named ->
                dedupe((CodeGen.sanitizeName(named.name) ?: "Theme${i + 1}") + "Colors", used)
            }
        } else emptyList()
        val screens = artboard.composables.filterIsInstance<Node.Composable>()
        val bases = screens.mapIndexed { i, screen ->
            var base = CodeGen.sanitizeName(artboard.layerNames[screen.id] ?: "") ?: "Screen${i + 1}"
            // "LoginScreen" → "Login" so the derived names don't stutter (LoginScreenScreen).
            if (base.length > "Screen".length && base.endsWith("Screen")) base = base.removeSuffix("Screen")
            dedupe(base, used)
        }
        return AppNamePlan(bases, themed, schemeVals)
    }

    private fun dedupe(base: String, used: MutableSet<String>): String {
        var candidate = base
        var n = 2
        while (!used.add(candidate)) {
            candidate = "$base$n"
            n++
        }
        return candidate
    }

    /** Nav callback names for [screen]: target base names in preorder + optional onBack. */
    private fun navCallbacks(screen: Node.Composable, artboard: Node.Artboard, baseById: Map<String, String>): Pair<List<String>, Boolean> {
        val targets = screen.navTargets(artboard).mapNotNull { baseById[it] }
        return targets to screen.hasBackAction()
    }

    private fun screenUiFile(
        screen: Node.Composable,
        base: String,
        packageName: String,
        componentFns: Map<String, String>,
        baseById: Map<String, String>,
    ): String {
        // navFns = target BASE names, so params read onNavigateToHome (not …ScreenUI).
        val code = CodeGen.screenFunction(screen, "${base}ScreenUI", componentFns, params = null, navFns = baseById)
        return buildString {
            appendLine("package $packageName")
            appendLine()
            for (imp in code.imports.sorted()) appendLine("import $imp")
            appendLine()
            appendLine(code.text)
        }
    }

    private fun wiringFile(
        screen: Node.Composable,
        base: String,
        packageName: String,
        artboard: Node.Artboard,
        baseById: Map<String, String>,
    ): String {
        val (targets, back) = navCallbacks(screen, artboard, baseById)
        val callbacks = targets.map { "onNavigateTo$it" } + (if (back) listOf("onBack") else emptyList())
        return buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine("import androidx.compose.runtime.Composable")
            appendLine("import androidx.compose.runtime.collectAsState")
            appendLine("import androidx.compose.runtime.getValue")
            appendLine("import androidx.lifecycle.viewmodel.compose.viewModel")
            appendLine()
            appendLine("@Composable")
            if (callbacks.isEmpty()) {
                appendLine("fun ${base}Screen(viewModel: ${base}ViewModel = viewModel()) {")
            } else {
                appendLine("fun ${base}Screen(")
                appendLine("    viewModel: ${base}ViewModel = viewModel(),")
                for (cb in callbacks) appendLine("    $cb: () -> Unit = {},")
                appendLine(") {")
            }
            appendLine("    val uiState by viewModel.uiState.collectAsState()")
            appendLine()
            if (callbacks.isEmpty()) {
                appendLine("    ${base}ScreenUI()")
            } else {
                appendLine("    ${base}ScreenUI(")
                for (cb in callbacks) appendLine("        $cb = $cb,")
                appendLine("    )")
            }
            appendLine("}")
        }
    }

    private fun viewModelFile(base: String, packageName: String): String = buildString {
        appendLine("package $packageName")
        appendLine()
        appendLine("import androidx.lifecycle.ViewModel")
        appendLine("import kotlinx.coroutines.flow.MutableStateFlow")
        appendLine("import kotlinx.coroutines.flow.StateFlow")
        appendLine("import kotlinx.coroutines.flow.asStateFlow")
        appendLine()
        appendLine("data class ${base}UiState(")
        appendLine("    val isLoading: Boolean = false,")
        appendLine(")")
        appendLine()
        appendLine("class ${base}ViewModel : ViewModel() {")
        appendLine("    private val _uiState = MutableStateFlow(${base}UiState())")
        appendLine("    val uiState: StateFlow<${base}UiState> = _uiState.asStateFlow()")
        appendLine("}")
    }

    private fun mainActivityFile(
        artboard: Node.Artboard,
        plan: AppNamePlan,
        packageName: String,
        baseById: Map<String, String>,
    ): String {
        val screens = artboard.composables.filterIsInstance<Node.Composable>()
        val imports = sortedImports(plan)
        val theme = if (plan.themed) CodeGen.themeBlock(artboard.themes, plan.schemeVals, artboard.activeTheme, imports) else null
        return buildString {
            appendLine("package $packageName")
            appendLine()
            for (imp in imports.sorted()) appendLine("import $imp")
            appendLine()
            plan.bases.forEach { base ->
                appendLine("@Serializable")
                appendLine("data object $base : NavKey")
                appendLine()
            }
            if (theme != null) {
                append(theme)
                appendLine()
            }
            appendLine("class MainActivity : ComponentActivity() {")
            appendLine("    override fun onCreate(savedInstanceState: Bundle?) {")
            appendLine("        super.onCreate(savedInstanceState)")
            appendLine("        setContent {")
            if (theme != null) {
                appendLine("            AppTheme {")
                appendLine("                App()")
                appendLine("            }")
            } else {
                appendLine("            App()")
            }
            appendLine("        }")
            appendLine("    }")
            appendLine("}")
            appendLine()
            appendLine("@Composable")
            appendLine("fun App() {")
            appendLine("    val backStack = rememberNavBackStack(${plan.bases.firstOrNull() ?: "TODO"})")
            appendLine("    NavDisplay(")
            appendLine("        backStack = backStack,")
            appendLine("        onBack = { backStack.removeLastOrNull() },")
            appendLine("        entryProvider = entryProvider {")
            screens.forEachIndexed { i, screen ->
                val base = plan.bases[i]
                val (targets, back) = navCallbacks(screen, artboard, baseById)
                if (targets.isEmpty() && !back) {
                    appendLine("            entry<$base> {")
                    appendLine("                ${base}Screen()")
                    appendLine("            }")
                } else {
                    appendLine("            entry<$base> {")
                    appendLine("                ${base}Screen(")
                    for (t in targets) appendLine("                    onNavigateTo$t = { backStack.add($t) },")
                    if (back) appendLine("                    onBack = { backStack.removeLastOrNull() },")
                    appendLine("                )")
                    appendLine("            }")
                }
            }
            appendLine("        },")
            appendLine("    )")
            appendLine("}")
        }
    }

    private fun sortedImports(plan: AppNamePlan): MutableSet<String> = mutableSetOf(
        "android.os.Bundle",
        "androidx.activity.ComponentActivity",
        "androidx.activity.compose.setContent",
        "androidx.compose.runtime.Composable",
        "androidx.navigation3.runtime.NavKey",
        "androidx.navigation3.runtime.entry",
        "androidx.navigation3.runtime.entryProvider",
        "androidx.navigation3.runtime.rememberNavBackStack",
        "androidx.navigation3.ui.NavDisplay",
        "kotlinx.serialization.Serializable",
    )
}
