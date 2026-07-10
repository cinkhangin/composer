package composer.codeparse

import composer.codegen.AppCodeGen
import composer.model.DesignTheme
import composer.model.NamedTheme
import composer.model.Node

/** One project source file handed to [AppParser]. */
data class SourceFile(val path: String, val text: String)

enum class AppFileRole { ScreenUi, ScreenWiring, ViewModel, MainActivity, Other }

data class ParsedAppFile(
    val path: String,
    val role: AppFileRole,
    /** The screen this file belongs to (the three per-screen roles). */
    val screenId: String? = null,
    /** ScreenUi files: full per-file bookkeeping for declaration-level write-back. */
    val design: ParsedDesign? = null,
    /**
     * Wiring/VM/MainActivity: text == canonical regeneration. ANY user edit
     * (even whitespace) flips this false — the file is then preserved forever
     * and only warnings surface when a design change needed to touch it.
     */
    val canonical: Boolean = false,
)

data class ParsedApp(
    val artboard: Node.Artboard,
    val files: List<ParsedAppFile>,
    val packageName: String,
    val warnings: List<String> = emptyList(),
)

/**
 * Assembles ONE design from an app's file set (the inverse of [AppCodeGen]):
 * MainActivity's route objects define screen ORDER and base names; each
 * `<Base>ScreenUI.kt` contributes that screen's tree (full RawCode contract);
 * the theme block parses back into the artboard's themes. UI files are the
 * source of truth for nav actions — MainActivity's entry wiring is derived.
 */
object AppParser {

    fun parse(files: List<SourceFile>): ParsedApp? {
        val warnings = mutableListOf<String>()
        val scanned = files.map { it to scanSource(it.text) }

        // ---- pass 1: classify + global name maps --------------------------------
        val mainEntry = scanned.firstOrNull { (_, src) ->
            src.declarations.any {
                it is KOtherDecl && it.kind == OtherKind.Class &&
                    it.name == "MainActivity" && "ComponentActivity" in it.superTypes
            }
        }
        val routeBases: List<String> = mainEntry?.second?.declarations
            ?.filterIsInstance<KOtherDecl>()
            ?.filter { it.kind == OtherKind.Object && "NavKey" in it.superTypes && "Serializable" in it.annotationNames }
            ?.mapNotNull { it.name }
            ?: emptyList()

        // UI files by the base of their <Base>ScreenUI screen function.
        val uiFileByBase = mutableMapOf<String, Pair<SourceFile, KSourceFile>>()
        for ((file, src) in scanned) {
            for (d in src.declarations) {
                if (d is KFunctionDecl && d.name?.endsWith("ScreenUI") == true &&
                    "Composable" in d.annotationNames && d.bodyBlock != null
                ) {
                    val base = d.name.removeSuffix("ScreenUI")
                    if (base.isNotEmpty()) {
                        if (base in uiFileByBase) {
                            warnings += "Duplicate ${base}ScreenUI — using the first occurrence."
                        } else {
                            uiFileByBase[base] = file to src
                        }
                    }
                }
            }
        }
        // Screen order: MainActivity's routes; UI files without a route append after
        // (path-sorted, deterministic), flagged.
        val orderedBases = routeBases.filter { it in uiFileByBase } +
            uiFileByBase.keys.filterNot { it in routeBases }.sorted()
        if (orderedBases.isEmpty()) return null
        routeBases.filterNot { it in uiFileByBase }.forEach {
            warnings += "Route $it has no ${it}ScreenUI.kt — skipped."
        }
        uiFileByBase.keys.filterNot { it in routeBases }.forEach {
            warnings += "${it}ScreenUI.kt has no route in MainActivity — appended to the artboard."
        }

        val screenIdByBase = orderedBases.withIndex().associate { (i, b) -> b to "s${i + 1}" }
        val external = DesignParser.ExternalNames(
            screenIdsByName = orderedBases.associate { "${it}ScreenUI" to screenIdByBase.getValue(it) },
            navBaseToScreenId = orderedBases.associate { it to screenIdByBase.getValue(it) },
            fixedScreenIds = emptyMap(), // per-file below
        )

        // ---- pass 2: parse UI files ----------------------------------------------
        val screens = mutableListOf<Node.Composable>()
        val layerNames = mutableMapOf<String, String>()
        val componentIds = linkedSetOf<String>()
        val parsedFiles = mutableListOf<ParsedAppFile>()
        val uiDesignByBase = mutableMapOf<String, ParsedDesign>()
        orderedBases.forEachIndexed { i, base ->
            val (file, _) = uiFileByBase.getValue(base)
            val sid = screenIdByBase.getValue(base)
            val design = DesignParser.parse(
                file.text,
                DesignParser.ExternalNames(
                    external.screenIdsByName,
                    external.navBaseToScreenId,
                    fixedScreenIds = mapOf("${base}ScreenUI" to sid),
                    idPrefix = "$sid-",
                ),
            )
            if (design == null) {
                warnings += "${file.path} no longer parses — screen $base skipped."
                return@forEachIndexed
            }
            val screen = (design.artboard.composables.first() as Node.Composable).copy(x = i * 470, y = 0)
            screens += screen
            layerNames[sid] = base
            componentIds += design.artboard.componentIds
            uiDesignByBase[base] = design
            parsedFiles += ParsedAppFile(file.path, AppFileRole.ScreenUi, screenId = sid, design = design)
        }
        if (screens.isEmpty()) return null

        val packageName = mainEntry?.first?.text?.let(::packageNameOf)
            ?: uiFileByBase.values.firstNotNullOfOrNull { packageNameOf(it.first.text) }
            ?: ""

        // ---- themes: parse the MainActivity theme block back ----------------------
        val themes = mainEntry?.let { parseThemes(it.first.text, it.second) } ?: ThemeParse(emptyList(), 0)

        val artboard = Node.Artboard(
            id = "artboard",
            composables = screens,
            layerNames = layerNames,
            componentIds = componentIds.toList(),
            themes = themes.themes,
            activeTheme = themes.active,
        )

        // ---- pass 3: canonicality of the derived files ----------------------------
        val regenerated = AppCodeGen.generate(artboard, packageName).associateBy { it.path }
        val claimed = parsedFiles.mapTo(mutableSetOf()) { it.path }
        for ((file, _) in scanned) {
            if (file.path in claimed) continue
            val role = when {
                mainEntry?.first === file -> AppFileRole.MainActivity
                file.path.substringAfterLast('/').removeSuffix(".kt").endsWith("Screen") -> AppFileRole.ScreenWiring
                file.path.substringAfterLast('/').removeSuffix(".kt").endsWith("ViewModel") -> AppFileRole.ViewModel
                else -> AppFileRole.Other
            }
            val base = when (role) {
                AppFileRole.ScreenWiring -> file.path.substringAfterLast('/').removeSuffix("Screen.kt")
                AppFileRole.ViewModel -> file.path.substringAfterLast('/').removeSuffix("ViewModel.kt")
                else -> null
            }
            val sid = base?.let { screenIdByBase[it] }
            val canonical = regenerated[file.path.substringAfterLast('/')]?.text == file.text
            parsedFiles += ParsedAppFile(
                file.path,
                if (role != AppFileRole.Other && (role == AppFileRole.MainActivity || sid != null)) role else AppFileRole.Other,
                screenId = sid,
                canonical = canonical,
            )
        }

        return ParsedApp(artboard, parsedFiles, packageName, warnings)
    }

    private val PACKAGE = Regex("""^\s*package\s+([A-Za-z_][\w.]*)""", RegexOption.MULTILINE)

    fun packageNameOf(text: String): String? = PACKAGE.find(text)?.groupValues?.get(1)

    private class ThemeParse(val themes: List<NamedTheme>, val active: Int)

    /**
     * `val XColors = light|darkColorScheme(token = Color(0x…), …)` properties →
     * [NamedTheme]s; the active index comes from AppTheme's default parameter.
     */
    private fun parseThemes(text: String, src: KSourceFile): ThemeParse {
        val vals = src.declarations.filterIsInstance<KOtherDecl>().filter { it.themeArtifact && it.name != null }
        if (vals.isEmpty()) return ThemeParse(emptyList(), 0)
        val themes = vals.mapNotNull { decl ->
            val declText = text.substring(decl.range.first, decl.range.last + 1)
            val lexed = lex(declText)
            val block = parseBlockSpan(ParseInput(declText, lexed.tokens, lexed.comments), 0, lexed.tokens.size, declText.indices)
            val prop = block.statements.singleOrNull() as? KPropertyStatement ?: return@mapNotNull null
            val call = prop.initializer?.unparen() as? KCall ?: return@mapNotNull null
            val builder = callName(call) ?: return@mapNotNull null
            var theme = DesignTheme(dark = builder == "darkColorScheme")
            for (arg in call.args) {
                val token = arg.name ?: return@mapNotNull null
                val value = colorValue(arg.expr) ?: return@mapNotNull null
                theme = theme.set(token, value)
            }
            NamedTheme(decl.name!!.removeSuffix("Colors"), theme)
        }
        val appTheme = src.declarations.filterIsInstance<KFunctionDecl>().firstOrNull { it.name == "AppTheme" }
        val activeVal = appTheme?.paramListText?.let { Regex("""=\s*([A-Za-z_]\w*)""").find(it)?.groupValues?.get(1) }
        val active = vals.indexOfFirst { it.name == activeVal }.coerceAtLeast(0)
        return ThemeParse(themes, active)
    }
}
