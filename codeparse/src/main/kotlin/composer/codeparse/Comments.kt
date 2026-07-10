package composer.codeparse

/**
 * Offset-based ports of the PSI sibling walks that attached comments to
 * statements. All logic is driven by the source text, the sorted comment side
 * list, and statement ranges — semantics match the PSI versions (a leading
 * comment that shares a line with earlier code trails the PREVIOUS statement;
 * a trailing comment must have nothing but same-line blanks before it).
 */
internal fun startsItsLine(text: String, start: Int): Boolean {
    val lineStart = text.lastIndexOf('\n', start - 1) + 1
    return text.substring(lineStart, start).isBlank()
}

/** Comments in the run directly above statement [index] of [block]. */
internal fun attachedComments(input: ParseInput, block: KBlock, index: Int): List<KComment> {
    val stmt = block.statements[index]
    val prevEnd = if (index > 0) block.statements[index - 1].range.last + 1 else block.bodyRange.first
    val run = input.comments.filter { it.range.first >= prevEnd && it.range.last < stmt.range.first }
    // Drop leading comments that share a line with earlier code — they trail the
    // previous statement and were handled there.
    var drop = 0
    while (drop < run.size && !startsItsLine(input.text, run[drop].range.first)) drop++
    return run.drop(drop)
}

/** The last comment on statement [index]'s own line after it, or null. */
internal fun trailingComment(input: ParseInput, block: KBlock, index: Int): KComment? {
    val stmt = block.statements[index]
    val limit = block.statements.getOrNull(index + 1)?.range?.first ?: (block.bodyRange.last + 1)
    var pos = stmt.range.last + 1
    var last: KComment? = null
    for (c in input.comments) {
        if (c.range.first < pos) continue
        if (c.range.first >= limit) break
        val between = input.text.substring(pos, c.range.first)
        if (between.contains('\n') || between.isNotBlank()) break // newline or a real token (e.g. `;`)
        last = c
        pos = c.range.last + 1
    }
    return last
}

/**
 * Comments between the last statement and the block end (or in a statement-less
 * block) — the trailing run parseBlock preserves as one RawCode. Returns the
 * (startOffset, endOffset-exclusive) span of the run, or null.
 */
internal fun trailingBlockComments(input: ParseInput, block: KBlock): Pair<Int, Int>? {
    val from = block.statements.lastOrNull()?.range?.let { it.last + 1 } ?: block.bodyRange.first
    var first: KComment? = null
    var last: KComment? = null
    for (c in input.comments) {
        if (c.range.first < from) continue
        if (c.range.last > block.bodyRange.last) break
        // A same-line trailer of the last statement was already captured with it.
        if (first != null || startsItsLine(input.text, c.range.first)) {
            if (first == null) first = c
            last = c
        }
    }
    return if (first != null && last != null) first.range.first to last.range.last + 1 else null
}
