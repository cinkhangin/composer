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
)

data class ParsedFunction(
    /** == the matching [Node.Composable]'s id. */
    val screenId: String,
    val functionName: String,
    /** Whole declaration: annotations through the closing brace (parse-time offsets). */
    val fnRange: IntRange,
    /** Structural hash of the parsed screen — write-back skips screens whose hash is unchanged. */
    val treeHash: Int,
)
