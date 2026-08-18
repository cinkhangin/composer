package composer.model

enum class LayoutKind { Box, Column, Row }

fun Node.layoutKind(): LayoutKind? = when (this) {
    is Node.Box -> LayoutKind.Box
    is Node.Column -> LayoutKind.Column
    is Node.Row -> LayoutKind.Row
    else -> null
}

/** Change a basic layout container while retaining its identity, content and modifiers. */
fun Node.convertLayout(kind: LayoutKind): Node = when (kind) {
    LayoutKind.Box -> when (this) {
        is Node.Box -> this
        is Node.Column -> toBox()
        is Node.Row -> toBox()
        else -> this
    }
    LayoutKind.Column -> when (this) {
        is Node.Box -> toColumn()
        is Node.Column -> this
        is Node.Row -> toColumn()
        else -> this
    }
    LayoutKind.Row -> when (this) {
        is Node.Box -> toRow()
        is Node.Column -> toRow()
        is Node.Row -> this
        else -> this
    }
}

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

fun Node.Box.toRow(): Node.Row = Node.Row(
    id = id,
    children = children,
    horizontalArrangement = when (contentAlignment) {
        BoxAlignment.TopCenter, BoxAlignment.Center, BoxAlignment.BottomCenter -> HArrangement.Center
        BoxAlignment.TopEnd, BoxAlignment.CenterEnd, BoxAlignment.BottomEnd -> HArrangement.End
        else -> HArrangement.Start
    },
    verticalAlignment = when (contentAlignment) {
        BoxAlignment.CenterStart, BoxAlignment.Center, BoxAlignment.CenterEnd -> VAlignment.Center
        BoxAlignment.BottomStart, BoxAlignment.BottomCenter, BoxAlignment.BottomEnd -> VAlignment.Bottom
        else -> VAlignment.Top
    },
    modifier = modifier,
)

fun Node.Column.toBox(): Node.Box = Node.Box(
    id = id,
    children = children,
    contentAlignment = boxAlignment(verticalArrangement, horizontalAlignment),
    modifier = modifier,
)

fun Node.Row.toBox(): Node.Box = Node.Box(
    id = id,
    children = children,
    contentAlignment = boxAlignment(verticalAlignment, horizontalArrangement),
    modifier = modifier,
)

private fun boxAlignment(vertical: VArrangement, horizontal: HAlignment): BoxAlignment = boxAlignment(
    vertical = when (vertical) {
        VArrangement.Bottom -> VAlignment.Bottom
        VArrangement.Center -> VAlignment.Center
        else -> VAlignment.Top
    },
    horizontal = horizontal.toHArrangement(),
)

private fun boxAlignment(vertical: VAlignment, horizontal: HArrangement): BoxAlignment = when (
    vertical to horizontal
) {
    VAlignment.Top to HArrangement.Center -> BoxAlignment.TopCenter
    VAlignment.Top to HArrangement.End -> BoxAlignment.TopEnd
    VAlignment.Center to HArrangement.Start -> BoxAlignment.CenterStart
    VAlignment.Center to HArrangement.Center -> BoxAlignment.Center
    VAlignment.Center to HArrangement.End -> BoxAlignment.CenterEnd
    VAlignment.Bottom to HArrangement.Start -> BoxAlignment.BottomStart
    VAlignment.Bottom to HArrangement.Center -> BoxAlignment.BottomCenter
    VAlignment.Bottom to HArrangement.End -> BoxAlignment.BottomEnd
    else -> BoxAlignment.TopStart
}

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

private fun HAlignment.toHArrangement(): HArrangement = when (this) {
    HAlignment.Start -> HArrangement.Start
    HAlignment.Center -> HArrangement.Center
    HAlignment.End -> HArrangement.End
}
