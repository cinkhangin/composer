package composer.model

/**
 * Direction conversion for auto-layout containers (Figma's vertical ↔ horizontal toggle).
 * Children, modifiers, spacing and id are preserved; the main-axis arrangement and
 * cross-axis alignment are mapped across so the visual intent survives the flip.
 */
fun Node.Column.toRow(): Node.Row = Node.Row(
    id = id,
    children = children,
    horizontalArrangement = verticalArrangement.toHArrangement(),
    verticalAlignment = horizontalAlignment.toVAlignment(),
    modifier = modifier,
    spacing = spacing,
)

fun Node.Row.toColumn(): Node.Column = Node.Column(
    id = id,
    children = children,
    verticalArrangement = horizontalArrangement.toVArrangement(),
    horizontalAlignment = verticalAlignment.toHAlignment(),
    modifier = modifier,
    spacing = spacing,
)

/** Box → Column, adopting auto layout with sensible defaults (mapping the box's alignment). */
fun Node.Box.toColumn(): Node.Column = Node.Column(
    id = id,
    children = children,
    verticalArrangement = when (contentAlignment) {
        BoxAlignment.CenterStart, BoxAlignment.Center, BoxAlignment.CenterEnd -> VArrangement.Center
        BoxAlignment.BottomStart, BoxAlignment.BottomCenter, BoxAlignment.BottomEnd -> VArrangement.Bottom
        else -> VArrangement.Top
    },
    horizontalAlignment = when (contentAlignment) {
        BoxAlignment.TopCenter, BoxAlignment.Center, BoxAlignment.BottomCenter -> HAlignment.Center
        BoxAlignment.TopEnd, BoxAlignment.CenterEnd, BoxAlignment.BottomEnd -> HAlignment.End
        else -> HAlignment.Start
    },
    modifier = modifier,
)

fun VArrangement.toHArrangement(): HArrangement = when (this) {
    VArrangement.Top -> HArrangement.Start
    VArrangement.Bottom -> HArrangement.End
    VArrangement.Center -> HArrangement.Center
    VArrangement.SpaceBetween -> HArrangement.SpaceBetween
    VArrangement.SpaceAround -> HArrangement.SpaceAround
    VArrangement.SpaceEvenly -> HArrangement.SpaceEvenly
}

fun HArrangement.toVArrangement(): VArrangement = when (this) {
    HArrangement.Start -> VArrangement.Top
    HArrangement.End -> VArrangement.Bottom
    HArrangement.Center -> VArrangement.Center
    HArrangement.SpaceBetween -> VArrangement.SpaceBetween
    HArrangement.SpaceAround -> VArrangement.SpaceAround
    HArrangement.SpaceEvenly -> VArrangement.SpaceEvenly
}

fun HAlignment.toVAlignment(): VAlignment = when (this) {
    HAlignment.Start -> VAlignment.Top
    HAlignment.Center -> VAlignment.Center
    HAlignment.End -> VAlignment.Bottom
}

fun VAlignment.toHAlignment(): HAlignment = when (this) {
    VAlignment.Top -> HAlignment.Start
    VAlignment.Center -> HAlignment.Center
    VAlignment.Bottom -> HAlignment.End
}
