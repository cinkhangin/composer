package composer.model

/**
 * Canonical position for APPEARANCE modifiers inserted by the inspector.
 *
 * Modifier order is meaning in Compose: `padding(16).background(c)` fills only
 * the area *inside* the padding (reads as a margin), while `background(c).padding(16)`
 * fills the whole box with the padding as interior spacing — the Figma expectation.
 * The idiomatic chain is `alpha → clip → background → border → padding/size/…`:
 * clip before background so the fill gets rounded, background before border so the
 * stroke draws on top of the fill, and everything before padding so the box (not
 * the content area) is what gets painted.
 *
 * Only NEW insertions are placed canonically — replacing an existing modifier keeps
 * its position, so hand-crafted orderings in the Advanced chain are respected.
 */
private fun appearanceRank(m: ModifierSpec): Int = when (m) {
    is ModifierSpec.Alpha -> 0
    is ModifierSpec.Clip -> 1
    is ModifierSpec.Background -> 2
    is ModifierSpec.Border -> 3
    else -> Int.MAX_VALUE // layout modifiers (padding, size, …) come after appearance
}

/**
 * Insert [spec] at its canonical appearance position: before the first modifier
 * that must come after it. Non-appearance modifiers append at the end.
 */
fun List<ModifierSpec>.insertOrdered(spec: ModifierSpec): List<ModifierSpec> {
    val rank = appearanceRank(spec)
    if (rank == Int.MAX_VALUE) return this + spec
    val at = indexOfFirst { appearanceRank(it) > rank }
    if (at < 0) return this + spec
    return toMutableList().apply { add(at, spec) }
}
