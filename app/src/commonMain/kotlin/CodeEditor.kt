package composer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import composer.codegen.CodeGen
import composer.codeparse.DesignParser
import composer.codeparse.ParsedDesign
import composer.model.Node
import composer.res.Res
import composer.res.jetbrainsmono_regular
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.Font

/**
 * The editable code view: type or paste Compose code and the design updates —
 * the inverse projection of CodeGen, powered by the same DesignParser as the
 * IDE plugin. Statements outside the supported grammar become locked RawCode
 * nodes (never dropped); code outside screen functions isn't representable and
 * is surfaced via a notice.
 *
 * Sync-loop design (the same echo-guard pattern as DesignerSession, with
 * generated code as the canonical encoding): [CodeSyncState.lastSynced] is
 * always `CodeGen.generate(root)` of the tree the buffer currently REPRESENTS.
 *  - user types → debounced parse → [EditorState.applyCodeEdit] → lastSynced
 *    updated from the tree AS APPLIED → the root-change echo compares equal and
 *    the user's typed formatting is left alone;
 *  - inspector/layers/undo edit → generated != lastSynced → the buffer resets
 *    to canonical code (the tree wins);
 *  - programmatic buffer resets parse back to `text == lastSynced` → no-op, so
 *    external edits never cause id churn or spurious undo steps.
 */
class CodeSyncState {
    var field by mutableStateOf(TextFieldValue(""))

    /** Canonical generation of the tree the buffer reflects — the echo guard. */
    var lastSynced: String? = null

    var parseError by mutableStateOf<String?>(null)

    /** The buffer holds top-level code regeneration would drop (helpers, classes…). */
    var dropNotice by mutableStateOf(false)
}

@Composable
internal fun jetBrainsMono(): FontFamily = FontFamily(Font(Res.font.jetbrainsmono_regular))

/** Disables focus-driven auto-scrolling; the caret follower owns the viewport. */
@OptIn(ExperimentalFoundationApi::class)
internal object NoBringIntoView : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

/** Parse the buffer and apply it to the design; keeps the last good tree on failure. */
internal fun parseAndApply(state: EditorState, sync: CodeSyncState, text: String) {
    if (text == sync.lastSynced) return // canonical text of the current root — no-op
    val parsed = DesignParser.parse(text)
    if (parsed == null) {
        sync.parseError =
            if (text.isBlank()) "Empty file — the design was kept. Add a @Composable function to define a screen."
            else "No screens found — the design was kept. A screen is a top-level @Composable fun with a { } body."
        return
    }
    val merged = mergeParsed(state.artboard, parsed)
    // Parsed ids always differ from the designer's, so tree equality can't catch
    // a formatting-only edit — canonical generation can: equal output means the
    // same design, and skipping the apply avoids an id-churn commit + auto-save.
    if (CodeGen.generate(merged) != CodeGen.generate(state.root)) {
        state.applyCodeEdit(merged)
    }
    // Record canonical AS APPLIED (post migrate/dedupe), so the root-change echo
    // compares equal and leaves the user's typed formatting alone.
    sync.lastSynced = CodeGen.generate(state.root)
    sync.parseError = null
    sync.dropNotice = parsed.hasNonScreenDeclarations
}

/**
 * Graft editor-only state — canvas geometry, themes, the component registry,
 * pretty layer names — from the [current] artboard onto [parsed]'s structure.
 * Code carries none of it, so a parsed tree arrives with defaults; without this
 * merge every code edit would scatter the screens and reset the theme. Screens
 * match by generated function name first, leftovers by order (handles renames).
 */
internal fun mergeParsed(current: Node.Artboard, parsed: ParsedDesign): Node.Artboard {
    val pArt = parsed.artboard
    val curScreens = current.composables.filterIsInstance<Node.Composable>()
    val curNames = CodeGen.screenFunctionNames(current) // parallel to curScreens
    val pScreens = pArt.composables.filterIsInstance<Node.Composable>()

    // Pass 1: match by function name (a parsed screen's layer name IS its fn name).
    val byName = curScreens.indices.associateBy { curNames[it] }
    val matched = arrayOfNulls<Int>(pScreens.size) // parsed index → current index
    val taken = mutableSetOf<Int>()
    pScreens.forEachIndexed { i, p ->
        byName[pArt.layerNames[p.id]]?.takeIf { taken.add(it) }?.let { matched[i] = it }
    }
    // Pass 2: leftovers pair up in order (a rename keeps its screen's geometry).
    val freeCur = curScreens.indices.filterNot { it in taken }.iterator()
    for (i in pScreens.indices) {
        if (matched[i] == null && freeCur.hasNext()) matched[i] = freeCur.next()
    }

    val idMap = mutableMapOf<String, String>() // current screen id → parsed id
    val merged = pScreens.mapIndexed { i, p ->
        val old = matched[i]?.let(curScreens::get) ?: return@mapIndexed p
        idMap[old.id] = p.id
        p.copy(x = old.x, y = old.y, width = old.width, height = old.height)
    }.toMutableList()
    // Truly-new screens: place right of everything kept (the parser's defaults
    // would overlap existing screens).
    var nextX = merged.filterIndexed { i, _ -> matched[i] != null }
        .maxOfOrNull { it.x + it.width + 60 } ?: 0
    for (i in merged.indices) {
        if (matched[i] == null) {
            merged[i] = merged[i].copy(x = nextX, y = 0)
            nextX += merged[i].width + 60
        }
    }

    // Pretty layer names survive when they still sanitize to the parsed fn name
    // ("Login Screen" stays "Login Screen" instead of becoming "LoginScreen").
    val names = pArt.layerNames.toMutableMap()
    idMap.forEach { (oldId, newId) ->
        val pretty = current.layerNames[oldId] ?: return@forEach
        val curIdx = curScreens.indexOfFirst { it.id == oldId }
        if (curIdx >= 0 && curNames[curIdx] == names[newId]) names[newId] = pretty
    }

    return current.copy( // keeps the artboard id, themes, and activeTheme
        composables = merged,
        layerNames = names,
        componentIds = (pArt.componentIds + current.componentIds.mapNotNull { idMap[it] }).distinct(),
    )
}
