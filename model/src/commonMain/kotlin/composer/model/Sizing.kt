package composer.model

/**
 * Figma-style per-axis sizing as a pure view over a node's modifier list. Each axis is
 * **Fixed** (a dp value), **Hug** (wrap content), or **Fill** (fill the parent). This is
 * an adapter only — the model still stores plain [ModifierSpec]s, so codegen, the
 * renderer, and persistence are unchanged.
 *
 * Mapping (width shown; height is symmetric):
 * - Fixed(n) ↔ [ModifierSpec.Width]`(n)` (or the width half of a legacy [ModifierSpec.Size]).
 * - Fill     ↔ [ModifierSpec.FillMaxWidth] — **except** on a linear parent's main axis
 *   (a Row for width, a Column for height), where Fill means [ModifierSpec.Weight]`(1f)`.
 * - Hug      ↔ no width constraint at all.
 *
 * [weightIsFill] tells the reader/writer whether `Modifier.weight` counts as Fill on this
 * axis — i.e. whether the parent is the linear container whose main axis this is.
 */
enum class SizeAxis { Width, Height }

sealed interface SizeMode {
    data class Fixed(val value: Int) : SizeMode
    data object Hug : SizeMode
    data object Fill : SizeMode
}

/** Whether `Modifier.weight` should read/write as Fill for [axis], given the node's [parent]. */
fun weightIsFill(axis: SizeAxis, parent: Node?): Boolean = when (axis) {
    SizeAxis.Width -> parent is Node.Row
    SizeAxis.Height -> parent is Node.Column
}

/** Read the current [SizeMode] of [axis] from this modifier list. */
fun List<ModifierSpec>.sizeMode(axis: SizeAxis, weightIsFill: Boolean): SizeMode {
    val fills = any {
        it is ModifierSpec.FillMaxSize ||
            (axis == SizeAxis.Width && it is ModifierSpec.FillMaxWidth) ||
            (axis == SizeAxis.Height && it is ModifierSpec.FillMaxHeight)
    }
    val weightFills = weightIsFill && any { it is ModifierSpec.Weight && it.value > 0f }
    if (fills || weightFills) return SizeMode.Fill

    val fixed = firstNotNullOfOrNull { spec ->
        when {
            axis == SizeAxis.Width && spec is ModifierSpec.Width -> spec.width
            axis == SizeAxis.Height && spec is ModifierSpec.Height -> spec.height
            spec is ModifierSpec.Size -> if (axis == SizeAxis.Width) spec.width else spec.height
            else -> null
        }
    }
    return if (fixed != null) SizeMode.Fixed(fixed) else SizeMode.Hug
}

/**
 * Return a new modifier list with [axis] set to [mode], leaving the other axis untouched.
 * Any legacy [ModifierSpec.Size]/[ModifierSpec.FillMaxSize] is first split into independent
 * per-axis specs so the two axes can be set separately.
 */
fun List<ModifierSpec>.withSizeMode(axis: SizeAxis, mode: SizeMode, weightIsFill: Boolean): List<ModifierSpec> {
    // 1. Decompose the both-axes specs into per-axis specs.
    val decomposed = flatMap { spec ->
        when (spec) {
            is ModifierSpec.Size -> listOf(ModifierSpec.Width(spec.width), ModifierSpec.Height(spec.height))
            is ModifierSpec.FillMaxSize -> listOf(ModifierSpec.FillMaxWidth(spec.fraction), ModifierSpec.FillMaxHeight(spec.fraction))
            else -> listOf(spec)
        }
    }
    // 2. Drop this axis's existing constraints (fixed, fill, and weight if it's the fill axis).
    val cleared = decomposed.filterNot { spec ->
        when (axis) {
            SizeAxis.Width -> spec is ModifierSpec.Width || spec is ModifierSpec.FillMaxWidth ||
                (weightIsFill && spec is ModifierSpec.Weight)
            SizeAxis.Height -> spec is ModifierSpec.Height || spec is ModifierSpec.FillMaxHeight ||
                (weightIsFill && spec is ModifierSpec.Weight)
        }
    }
    // 3. Append the new constraint for this axis.
    val added: ModifierSpec? = when (mode) {
        is SizeMode.Fixed -> when (axis) {
            SizeAxis.Width -> ModifierSpec.Width(mode.value.coerceAtLeast(0))
            SizeAxis.Height -> ModifierSpec.Height(mode.value.coerceAtLeast(0))
        }
        SizeMode.Fill -> when {
            weightIsFill -> ModifierSpec.Weight(1f)
            axis == SizeAxis.Width -> ModifierSpec.FillMaxWidth()
            else -> ModifierSpec.FillMaxHeight()
        }
        SizeMode.Hug -> null
    }
    return if (added != null) cleared + added else cleared
}

/** The Fixed dp value currently on [axis], or a sensible seed when switching to Fixed. */
fun List<ModifierSpec>.fixedSizeOr(axis: SizeAxis, fallback: Int): Int =
    (sizeMode(axis, weightIsFill = false) as? SizeMode.Fixed)?.value ?: fallback
