package composer.codeparse

import composer.codegen.CodeGen
import composer.model.Node
import composer.model.childNodes
import composer.model.validComponentIds

/** One text replacement; offsets into the parse-time text, [end] exclusive. */
data class TextEdit(val start: Int, val end: Int, val replacement: String)

/** A screen function the plan renames ([from] → [to]). */
data class ScreenRename(val from: String, val to: String)

/** Edits to apply (unordered) — apply descending by [TextEdit.start]. */
data class WriteBackPlan(val edits: List<TextEdit>, val renames: List<ScreenRename> = emptyList())

/**
 * Designer edits → minimal text edits, honoring the preservation contract:
 *  - a screen whose (geometry-normalized) tree AND name are unchanged is skipped
 *    → byte-identical;
 *  - a changed screen's whole function declaration is regenerated
 *    (codegen-canonical formatting — the designer reformats only what it edits);
 *  - screens the designer added append at EOF; deleted screens delete their
 *    declaration (undoable in the IDE);
 *  - imports are merged APPEND-ONLY: never removed or reordered — removal needs
 *    resolve and could strip imports used only inside RawCode.
 * Renaming a screen regenerates every screen that instantiates it (their call
 * sites live in generated bodies); call sites in OTHER FILES are not updated
 * (no resolve) — each rename is reported in [WriteBackPlan.renames] so the
 * plugin can surface that caveat.
 */
object WriteBackPlanner {

    fun plan(text: String, previous: ParsedDesign, edited: Node.Artboard): WriteBackPlan {
        val prevById = previous.functions.associateBy { it.screenId }
        val editedScreens = edited.composables.filterIsInstance<Node.Composable>()
        val editedIds = editedScreens.map { it.id }.toSet()

        // ---- function names ------------------------------------------------
        // Non-screen functions own their names outright; screens keeping their
        // name claim it in pass 1; renamed/new screens dedupe in pass 2 (a
        // deleted or renamed-away screen's old name becomes free).
        val screenFnNames = previous.functions.map { it.functionName }.toSet()
        val used = mutableSetOf("AppTheme")
        used += previous.topLevelFunctionNames.filterNot { it in screenFnNames }
        val names = mutableMapOf<String, String>() // screenId -> final fn name
        // Pass 1: unchanged-name screens keep their names.
        for (screen in editedScreens) {
            val prev = prevById[screen.id]
            val preferred = preferredName(edited, editedScreens, screen.id)
            if (prev != null && prev.functionName == preferred) names[screen.id] = preferred
        }
        // Pass 2: renamed/new screens dedupe against the rest.
        for (screen in editedScreens) {
            if (screen.id in names) continue
            val base = preferredName(edited, editedScreens, screen.id)
            var candidate = base
            var n = 2
            while (names.containsValue(candidate) || candidate in used) {
                candidate = "$base$n"
                n++
            }
            names[screen.id] = candidate
        }

        // ---- change detection ------------------------------------------------
        val componentFns = edited.validComponentIds().mapNotNull { id -> names[id]?.let { id to it } }.toMap()
        val renamedIds = editedScreens.mapNotNull { s ->
            prevById[s.id]?.takeIf { it.functionName != names[s.id] }?.screenId
        }.toSet()

        fun referencesRenamed(screen: Node.Composable): Boolean {
            fun walk(n: Node): Boolean =
                (n is Node.Instance && n.refId in renamedIds) || n.childNodes().any(::walk)
            return walk(screen)
        }

        val edits = mutableListOf<TextEdit>()
        val newImports = sortedSetOf<String>()

        for (screen in editedScreens) {
            val prev = prevById[screen.id]
            val changed = prev == null ||
                prev.treeHash != ParsedFunction.hashOf(screen) ||
                prev.functionName != names.getValue(screen.id) ||
                referencesRenamed(screen)
            if (!changed) continue
            // New screens get an empty signature; existing ones keep theirs verbatim
            // (parameters aren't modeled — bodies using them are RawCode).
            val code = CodeGen.screenFunction(screen, names.getValue(screen.id), componentFns, params = prev?.paramList ?: "()")
            newImports += code.imports
            if (prev == null) {
                // Append at EOF, separated by exactly one blank line.
                val prefix = when {
                    text.isEmpty() -> ""
                    text.endsWith("\n\n") -> ""
                    text.endsWith("\n") -> "\n"
                    else -> "\n\n"
                }
                edits += TextEdit(text.length, text.length, prefix + code.text + "\n")
            } else {
                // Skip no-op rewrites (e.g. only editor geometry moved).
                val original = text.substring(prev.fnRange.first, prev.fnRange.last + 1)
                if (original != code.text) {
                    edits += TextEdit(prev.fnRange.first, prev.fnRange.last + 1, code.text)
                }
            }
        }

        // Deleted screens: remove the declaration plus its trailing blank separator.
        for (prev in previous.functions) {
            if (prev.screenId in editedIds) continue
            var end = prev.fnRange.last + 1
            while (end < text.length && text[end] == '\n') end++
            edits += TextEdit(prev.fnRange.first, end, "")
        }

        // ---- import merge (append-only) --------------------------------------
        if (edits.isNotEmpty() && newImports.isNotEmpty()) {
            val starPkgs = previous.existingImports.filter { it.endsWith(".*") }.map { it.removeSuffix(".*") }
            val existingExact = previous.existingImports.toSet()
            val boundSimpleNames = previous.existingImports
                .filterNot { it.endsWith(".*") }
                .map { it.substringAfterLast('.') }
                .toSet()
            val toAdd = newImports.filter { fqn ->
                fqn !in existingExact &&
                    fqn.substringBeforeLast('.') !in starPkgs &&
                    // a same-simple-name import from elsewhere already binds the name —
                    // adding ours would be a redeclaration error
                    fqn.substringAfterLast('.') !in boundSimpleNames
            }
            if (toAdd.isNotEmpty()) {
                val block = toAdd.joinToString("") { "\nimport $it" }
                val prefix = if (previous.existingImports.isEmpty() && previous.importInsertOffset > 0) "\n" else ""
                edits += TextEdit(previous.importInsertOffset, previous.importInsertOffset, prefix + block)
            }
        }

        val renames = renamedIds.mapNotNull { id ->
            prevById[id]?.let { ScreenRename(from = it.functionName, to = names.getValue(id)) }
        }
        return WriteBackPlan(edits, renames)
    }

    /** Apply [plan] to [text] (tests / non-IDE callers). */
    fun apply(text: String, plan: WriteBackPlan): String {
        var result = text
        for (e in plan.edits.sortedByDescending { it.start }) {
            result = result.substring(0, e.start) + e.replacement + result.substring(e.end)
        }
        return result
    }

    private fun preferredName(artboard: Node.Artboard, screens: List<Node.Composable>, id: String): String {
        val index = screens.indexOfFirst { it.id == id }
        return artboard.layerNames[id]?.let { CodeGen.sanitizeName(it) } ?: "Composable${index + 1}"
    }
}
