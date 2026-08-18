package composer.codeparse

import composer.codegen.AppCodeGen
import composer.codegen.CodeGen
import composer.model.NavAction
import composer.model.Node
import composer.model.childNodes
import composer.model.navAction
import composer.model.validComponentIds

/** The plan for one file: minimal edits, a whole new file, or a deletion. */
data class AppFilePlan(
    /** Target path for edits, creation, or a move. */
    val path: String,
    val edits: List<TextEdit> = emptyList(),
    /** Non-null = create this file with this content (path may not exist yet). */
    val createText: String? = null,
    /** Non-null = move this existing file to [path] before writing [createText]. */
    val moveFrom: String? = null,
    /** The screen owning this file was deleted — the caller decides how to present it. */
    val delete: Boolean = false,
)

data class AppWriteBackPlan(
    val files: List<AppFilePlan>,
    val renames: List<ScreenRename> = emptyList(),
    val warnings: List<String> = emptyList(),
    /** Non-null means applying only the safe subset would break cross-file symbols. */
    val blockedReason: String? = null,
)

/**
 * Designer edits → per-file plans for the app file set. UI files get the same
 * declaration-level minimal-edit treatment as the single-file planner (treeHash
 * skip, verbatim signatures, append-only imports); wiring/VM/MainActivity are
 * whole-file replaced while CANONICAL and preserved forever once user-edited
 * (a warning surfaces when a design change needed to touch a preserved file).
 */
object AppWriteBackPlanner {

    fun plan(previous: ParsedApp, filesNow: Map<String, String>, edited: Node.Artboard): AppWriteBackPlan {
        val warnings = mutableListOf<String>()
        val editedScreens = edited.composables.filterIsInstance<Node.Composable>()
        val editedPlan = AppCodeGen.appNamePlan(edited)
        val baseById = editedScreens.indices.associate { editedScreens[it].id to editedPlan.bases[it] }

        val prevUiByScreen = previous.files.filter { it.role == AppFileRole.ScreenUi }.associateBy { it.screenId }
        val prevWiringByScreen = previous.files.filter { it.role == AppFileRole.ScreenWiring }.associateBy { it.screenId }
        val prevVmByScreen = previous.files.filter { it.role == AppFileRole.ViewModel }.associateBy { it.screenId }
        val prevTheme = previous.files.firstOrNull { it.role == AppFileRole.Theme }
        val prevMain = previous.files.firstOrNull { it.role == AppFileRole.MainActivity }
        val prevBaseById = previous.artboard.composables.filterIsInstance<Node.Composable>().let { prevScreens ->
            val prevPlan = AppCodeGen.appNamePlan(previous.artboard)
            prevScreens.indices.associate { prevScreens[it].id to prevPlan.bases[it] }
        }
        val dirPrefix = (prevMain?.path ?: prevTheme?.path ?: prevUiByScreen.values.firstOrNull()?.path ?: "MainActivity.kt")
            .substringBeforeLast('/', "")
            .let { if (it.isEmpty()) "" else "$it/" }

        // Renamed/deleted screens change their referrers' emission (instance calls,
        // nav callback names) — exactly the single-file rule, at app scope.
        val editedIds = editedScreens.mapTo(mutableSetOf()) { it.id }
        val renamedIds = editedScreens.mapNotNull { s ->
            s.id.takeIf { prevBaseById[it] != null && prevBaseById[it] != baseById[it] }
        }.toSet()
        val deletedIds = prevBaseById.keys.filterNot { it in editedIds }.toSet()
        val changedTargets = renamedIds + deletedIds

        // A rename changes symbols in all three screen files and MainActivity.
        // If any derived file has left canonical ownership, applying only the
        // remaining moves would leave references to declarations that no longer
        // exist. Reject the whole designer edit instead of producing broken code.
        if (renamedIds.isNotEmpty()) {
            val blockers = buildList {
                if (prevMain?.canonical == false) add(prevMain.path)
                for (id in renamedIds) {
                    prevWiringByScreen[id]?.takeUnless { it.canonical }?.let { add(it.path) }
                    prevVmByScreen[id]?.takeUnless { it.canonical }?.let { add(it.path) }
                }
            }.distinct()
            if (blockers.isNotEmpty()) {
                val reason = "Composer couldn't rename the screen because these files are hand-edited: " +
                    blockers.joinToString(", ") { it.substringAfterLast('/') }
                return AppWriteBackPlan(emptyList(), warnings = listOf(reason), blockedReason = reason)
            }
        }

        fun referencesChanged(screen: Node.Composable): Boolean {
            fun walk(n: Node): Boolean =
                (n is Node.Instance && n.refId in changedTargets) ||
                    (n.navAction() as? NavAction.Navigate)?.screenId in changedTargets ||
                    n.childNodes().any(::walk)
            return walk(screen)
        }

        fun hasNavActions(screen: Node.Composable): Boolean {
            fun walk(n: Node): Boolean =
                (n.navAction() ?: NavAction.None) != NavAction.None || n.childNodes().any(::walk)
            return walk(screen)
        }

        // Canonical file sets for the edited and previous designs.
        val newFiles = AppCodeGen.generate(edited, previous.packageName).associateBy { it.path }
        val prevCanonicalFiles = AppCodeGen.generate(previous.artboard, previous.packageName).associateBy { it.path }

        val compIds = edited.validComponentIds().toSet()
        val uiFns = editedScreens.indices
            .filter { editedScreens[it].id in compIds }
            .associate { editedScreens[it].id to "${editedPlan.bases[it]}ScreenUI" }

        val plans = mutableListOf<AppFilePlan>()

        // ---- per-screen files -------------------------------------------------
        editedScreens.forEachIndexed { i, screen ->
            val base = editedPlan.bases[i]
            val prevUi = prevUiByScreen[screen.id]
            val renamed = screen.id in renamedIds

            if (prevUi?.design == null) {
                // New screen: create the whole trio.
                plans += AppFilePlan("$dirPrefix${base}ScreenUI.kt", createText = newFiles.getValue("${base}ScreenUI.kt").text)
                plans += AppFilePlan("$dirPrefix${base}Screen.kt", createText = newFiles.getValue("${base}Screen.kt").text)
                plans += AppFilePlan("$dirPrefix${base}ViewModel.kt", createText = newFiles.getValue("${base}ViewModel.kt").text)
                return@forEachIndexed
            }

            // UI file: declaration-level minimal edit (single-file mechanics).
            val design = prevUi.design
            val prevFn = design.functions.firstOrNull { it.screenId == screen.id }
            val text = filesNow[prevUi.path]
            if (prevFn != null && text != null) {
                val changed = prevFn.treeHash != ParsedFunction.hashOf(screen) ||
                    prevFn.functionName != "${base}ScreenUI" ||
                    referencesChanged(screen)
                if (changed) {
                    val verbatimParams = prevFn.takeIf { !it.paramsCanonical }?.paramList
                    if (verbatimParams != null && hasNavActions(screen)) {
                        warnings += "\"${base}ScreenUI\" has navigation actions but a custom signature — undeclared callbacks were not wired."
                    }
                    val code = CodeGen.screenFunction(screen, "${base}ScreenUI", uiFns, params = verbatimParams, navFns = baseById)
                    val edits = mutableListOf<TextEdit>()
                    val original = text.substring(prevFn.fnRange.first, prevFn.fnRange.last + 1)
                    if (original != code.text) edits += TextEdit(prevFn.fnRange.first, prevFn.fnRange.last + 1, code.text)
                    if (edits.isNotEmpty()) {
                        WriteBackPlanner.planImportMerge(design, code.imports)?.let { edits += it }
                        // A renamed screen moves its file alongside the fn. Keeping
                        // the move explicit prevents the host from leaving the old UI
                        // file behind, where the next app parse would resurrect it as
                        // an un-routed screen.
                        if (renamed) {
                            plans += AppFilePlan(
                                "$dirPrefix${base}ScreenUI.kt",
                                createText = WriteBackPlanner.apply(text, WriteBackPlan(edits)),
                                moveFrom = prevUi.path,
                            )
                        } else {
                            plans += AppFilePlan(prevUi.path, edits = edits)
                        }
                    }
                }
            }

            // Wiring + ViewModel: whole-file replace while canonical.
            for ((role, prevFile, fileName) in listOf(
                Triple(AppFileRole.ScreenWiring, prevWiringByScreen[screen.id], "${base}Screen.kt"),
                Triple(AppFileRole.ViewModel, prevVmByScreen[screen.id], "${base}ViewModel.kt"),
            )) {
                val newText = newFiles.getValue(fileName).text
                if (prevFile == null) {
                    plans += AppFilePlan("$dirPrefix$fileName", createText = newText)
                    continue
                }
                val nowText = filesNow[prevFile.path] ?: continue
                if (prevFile.canonical) {
                    if (renamed) {
                        plans += AppFilePlan(
                            "$dirPrefix$fileName",
                            createText = newText,
                            moveFrom = prevFile.path,
                        )
                    } else if (newText != nowText) {
                        plans += AppFilePlan(prevFile.path, edits = listOf(TextEdit(0, nowText.length, newText)))
                    }
                } else {
                    val prevName = prevFile.path.substringAfterLast('/')
                    val needed = prevCanonicalFiles[prevName]?.text != newText
                    if (needed) {
                        warnings += "${prevFile.path} was hand-edited — the ${if (role == AppFileRole.ViewModel) "ViewModel" else "screen wiring"} was not updated for this change."
                    }
                }
            }
        }

        // ---- deleted screens ---------------------------------------------------
        for (sid in deletedIds) {
            listOfNotNull(prevUiByScreen[sid], prevWiringByScreen[sid], prevVmByScreen[sid]).forEach {
                plans += AppFilePlan(it.path, delete = true)
            }
        }

        // ---- AppTheme ----------------------------------------------------------
        val newTheme = newFiles["AppTheme.kt"]?.text
        when {
            newTheme == null && prevTheme != null -> plans += AppFilePlan(prevTheme.path, delete = true)
            newTheme != null && prevTheme == null ->
                plans += AppFilePlan("${dirPrefix}AppTheme.kt", createText = newTheme)
            newTheme != null && prevTheme != null -> {
                val nowText = filesNow[prevTheme.path]
                if (nowText != null && newTheme != nowText) {
                    if (prevTheme.canonical) {
                        plans += AppFilePlan(prevTheme.path, edits = listOf(TextEdit(0, nowText.length, newTheme)))
                    } else if (prevCanonicalFiles["AppTheme.kt"]?.text != newTheme) {
                        warnings += "${prevTheme.path} was hand-edited — theme changes were not written into it."
                    }
                }
            }
        }

        // ---- MainActivity --------------------------------------------------------
        val newMain = newFiles.getValue("MainActivity.kt").text
        if (prevMain == null) {
            plans += AppFilePlan("${dirPrefix}MainActivity.kt", createText = newMain)
        } else {
            val nowText = filesNow[prevMain.path]
            if (nowText != null && newMain != nowText) {
                if (prevMain.canonical) {
                    plans += AppFilePlan(prevMain.path, edits = listOf(TextEdit(0, nowText.length, newMain)))
                } else if (prevCanonicalFiles["MainActivity.kt"]?.text != newMain) {
                    warnings += "${prevMain.path} was hand-edited — navigation/theme changes were not written into it."
                }
            }
        }

        val renames = renamedIds.mapNotNull { id ->
            prevBaseById[id]?.let { ScreenRename(from = it, to = baseById.getValue(id)) }
        }
        return AppWriteBackPlan(plans, renames, warnings)
    }
}
