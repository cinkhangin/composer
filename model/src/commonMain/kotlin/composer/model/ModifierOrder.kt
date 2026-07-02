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
    is ModifierSpec.DropShadow -> 1 // shadow draws behind → must precede background
    is ModifierSpec.Clip -> 2
    is ModifierSpec.Background -> 3
    is ModifierSpec.Border -> 4
    is ModifierSpec.InnerShadow -> 5 // draws on top of the fill → after background/border
    else -> Int.MAX_VALUE // layout modifiers (padding, size, …) come after appearance
}

/**
 * The chain in DRAW order: [ModifierSpec.DropShadow]s hoisted to the front and
 * [ModifierSpec.InnerShadow]s sunk to the end (both keep their relative order;
 * everything else is untouched).
 *
 * A drop shadow is *by definition* behind the component — if it sat after
 * `background` in the applied chain it would paint over the fill, which is never
 * what a designer means. Same logic inverted for inner shadows (they sit on top
 * of the fill, so before `background` they'd be covered). Renderer AND codegen
 * both apply this, so the canvas and the generated code stay WYSIWYG no matter
 * where the user drags the shadow row.
 */
fun List<ModifierSpec>.drawOrder(): List<ModifierSpec> =
    filterIsInstance<ModifierSpec.DropShadow>() +
        filterNot { it is ModifierSpec.DropShadow || it is ModifierSpec.InnerShadow } +
        filterIsInstance<ModifierSpec.InnerShadow>()

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
