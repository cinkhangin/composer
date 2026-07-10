package composer.codeparse

import composer.model.Node

/**
 * The result of parsing one Kotlin file: the design tree plus the bookkeeping
 * write-back needs to regenerate ONLY what the designer actually changed.
 *
 * Node ids are session-local (`p1, p2, …` in document order — code carries no
 * ids); they're stable across re-parses of an unchanged file, so selection and
 * screen matching survive the parse → edit → re-parse loop within a session.
 */
data class ParsedDesign(
    /** Artboard with one [Node.Composable] screen per top-level `@Composable` fun. */
    val artboard: Node.Artboard,
    /** Per-screen source bookkeeping, parallel to `artboard.composables`. */
    val functions: List<ParsedFunction>,
    /** Imported FQNs (star imports recorded as `"pkg.*"`), in file order. */
    val existingImports: List<String>,
    /** Offset where new imports are inserted (end of the import list / after package / 0). */
    val importInsertOffset: Int,
    /** ALL named top-level functions (screens or not) — write-back name dedup. */
    val topLevelFunctionNames: List<String>,
    /**
     * Best-effort node id → source text range (parse-time offsets, end exclusive)
     * of the source element that produced the node. Screens map to their whole
     * function declaration; statement-level nodes to their statement (a swallowed
     * hoisted-state decl extends its consumer's range). Used for selection sync.
     */
    val sourceRanges: Map<String, IntRange> = emptyMap(),
    /**
     * True when the file contains top-level code besides screen functions
     * (helpers, theme vals, classes) — code a wholesale regeneration from the
     * design tree would drop. The web editor surfaces this as a notice.
     */
    val hasNonScreenDeclarations: Boolean = false,
)

data class ParsedFunction(
    /** == the matching [Node.Composable]'s id. */
    val screenId: String,
    val functionName: String,
    /** Whole declaration: annotations through the closing brace (parse-time offsets). */
    val fnRange: IntRange,
    /**
     * Structural hash of the parsed screen with editor-only canvas geometry
     * normalized out — dragging a screen on the artboard must NOT count as a
     * code edit. Write-back skips screens whose hash is unchanged.
     */
    val treeHash: Int,
    /**
     * The declaration's verbatim parameter-list text, parens included (e.g.
     * `(modifier: Modifier = Modifier)`). Write-back regenerates a changed
     * screen's BODY but preserves this signature exactly — the model has no
     * notion of parameters, and statements that use them are RawCode anyway.
     */
    val paramList: String = "()",
) {
    companion object {
        fun hashOf(screen: composer.model.Node.Composable): Int =
            screen.copy(x = 0, y = 0, width = 390, height = 844).hashCode()
    }
}
