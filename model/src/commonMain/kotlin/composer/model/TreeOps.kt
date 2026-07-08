package composer.model

/**
 * Pure, immutable tree operations over the design tree. No Compose, no state —
 * editing produces a new tree. Used by the editor's state holder.
 */

/** All descendable children — content **plus** Scaffold slots. For search/selection/tree. */
fun Node.childNodes(): List<Node> = when (this) {
    is Node.Column -> children
    is Node.Row -> children
    is Node.Box -> children
    is Node.Card -> children
    is Node.Fab -> children
    is Node.Dialog -> children
    is Node.BottomSheet -> children
    is Node.Slot -> children
    is Node.Button -> children
    is Node.TabRow -> children
    is Node.NavigationBar -> children
    is Node.BadgedBox -> children
    is Node.Canvas -> children
    is Node.Composable -> children
    is Node.Artboard -> composables
    is Node.Scaffold -> listOfNotNull(topBar, bottomBar, fab) + children
    is Node.TopAppBar -> listOfNotNull(navigationIcon, title) + actions
    is Node.Text, is Node.Spacer, is Node.Image, is Node.Divider,
    is Node.Switch, is Node.Checkbox, is Node.RadioButton, is Node.Slider,
    is Node.Icon, is Node.IconButton, is Node.TextField,
    is Node.Tab, is Node.NavItem, is Node.Chip,
    is Node.Line, is Node.RectShape, is Node.CircleShape, is Node.EllipseShape, is Node.ArcShape,
    is Node.CircularProgress, is Node.LinearProgress, is Node.Instance,
    is Node.RawCode -> emptyList()
}

/** The reorderable **content** children (Scaffold → content; TopAppBar → actions; else [childNodes]). */
fun Node.contentChildren(): List<Node> = when (this) {
    is Node.Scaffold -> children
    is Node.TopAppBar -> actions
    else -> childNodes()
}

/** Return a copy of [this] with its **content** children replaced (Scaffold slots untouched). */
fun Node.withChildren(children: List<Node>): Node = when (this) {
    is Node.Column -> copy(children = children)
    is Node.Row -> copy(children = children)
    is Node.Box -> copy(children = children)
    is Node.Card -> copy(children = children)
    is Node.Fab -> copy(children = children)
    is Node.Dialog -> copy(children = children)
    is Node.BottomSheet -> copy(children = children)
    is Node.Scaffold -> copy(children = children)
    is Node.TopAppBar -> copy(actions = children)
    is Node.Slot -> copy(children = children)
    is Node.Button -> copy(children = children)
    is Node.TabRow -> copy(children = children)
    is Node.NavigationBar -> copy(children = children)
    is Node.BadgedBox -> copy(children = children)
    is Node.Canvas -> copy(children = children)
    is Node.Composable -> copy(children = children)
    is Node.Artboard -> copy(composables = children)
    is Node.Text, is Node.Spacer, is Node.Image, is Node.Divider,
    is Node.Switch, is Node.Checkbox, is Node.RadioButton, is Node.Slider,
    is Node.Icon, is Node.IconButton, is Node.TextField,
    is Node.Tab, is Node.NavItem, is Node.Chip,
    is Node.Line, is Node.RectShape, is Node.CircleShape, is Node.EllipseShape, is Node.ArcShape,
    is Node.CircularProgress, is Node.LinearProgress, is Node.Instance,
    is Node.RawCode -> this
}

/** Apply [transform] to every direct child (content and Scaffold slots), preserving structure. */
fun Node.mapChildren(transform: (Node) -> Node): Node = when (this) {
    is Node.Column -> copy(children = children.map(transform))
    is Node.Row -> copy(children = children.map(transform))
    is Node.Box -> copy(children = children.map(transform))
    is Node.Card -> copy(children = children.map(transform))
    is Node.Fab -> copy(children = children.map(transform))
    is Node.Dialog -> copy(children = children.map(transform))
    is Node.BottomSheet -> copy(children = children.map(transform))
    is Node.Slot -> copy(children = children.map(transform))
    is Node.Button -> copy(children = children.map(transform))
    is Node.TabRow -> copy(children = children.map(transform))
    is Node.NavigationBar -> copy(children = children.map(transform))
    is Node.BadgedBox -> copy(children = children.map(transform))
    is Node.Canvas -> copy(children = children.map(transform))
    is Node.Composable -> copy(children = children.map(transform))
    is Node.Artboard -> copy(composables = composables.map(transform))
    is Node.Scaffold -> copy(
        topBar = topBar?.let(transform),
        bottomBar = bottomBar?.let(transform),
        fab = fab?.let(transform),
        children = children.map(transform),
    )
    is Node.TopAppBar -> copy(
        title = title?.let(transform),
        navigationIcon = navigationIcon?.let(transform),
        actions = actions.map(transform),
    )
    is Node.Text, is Node.Spacer, is Node.Image, is Node.Divider,
    is Node.Switch, is Node.Checkbox, is Node.RadioButton, is Node.Slider,
    is Node.Icon, is Node.IconButton, is Node.TextField,
    is Node.Tab, is Node.NavItem, is Node.Chip,
    is Node.Line, is Node.RectShape, is Node.CircleShape, is Node.EllipseShape, is Node.ArcShape,
    is Node.CircularProgress, is Node.LinearProgress, is Node.Instance,
    is Node.RawCode -> this
}

/** Depth-first search for the node with [id]. */
fun Node.findById(id: String): Node? {
    if (this.id == id) return this
    for (child in childNodes()) child.findById(id)?.let { return it }
    return null
}

/** Return a new tree with the node matching [id] passed through [transform]. */
fun Node.replaceById(id: String, transform: (Node) -> Node): Node {
    if (this.id == id) return transform(this)
    return mapChildren { it.replaceById(id, transform) }
}

/** Corner rounding of this node's background shape (value + unit), or null if rectangular / none. */
fun Node.backgroundCorner(): Pair<Int, CornerUnit>? =
    modifier.firstNotNullOfOrNull { it as? ModifierSpec.Background }
        ?.takeIf { it.corner > 0 }
        ?.let { it.corner to it.cornerUnit }

/** This node's (x, y) position from its Offset modifier, or (0, 0) if none. */
fun Node.offsetXY(): Pair<Int, Int> {
    val o = modifier.firstNotNullOfOrNull { it as? ModifierSpec.Offset }
    return (o?.x ?: 0) to (o?.y ?: 0)
}

/** Human-readable component name, e.g. "Column". */
fun Node.typeName(): String = when (this) {
    is Node.Text -> "Text"
    is Node.Button -> "Button"
    is Node.Spacer -> "Spacer"
    is Node.Column -> "Column"
    is Node.Row -> "Row"
    is Node.Box -> "Box"
    is Node.Image -> "Image"
    is Node.Divider -> "Divider"
    is Node.Icon -> "Icon"
    is Node.IconButton -> "IconButton"
    is Node.TextField -> "TextField"
    is Node.Card -> "Card"
    is Node.Scaffold -> "Scaffold"
    is Node.Fab -> "Fab"
    is Node.Dialog -> "Dialog"
    is Node.BottomSheet -> "BottomSheet"
    is Node.TopAppBar -> "TopAppBar"
    is Node.Slot -> "Slot"
    is Node.Instance -> "Instance"
    is Node.Composable -> "Composable"
    is Node.Artboard -> "Artboard"
    is Node.Switch -> "Switch"
    is Node.Checkbox -> "Checkbox"
    is Node.RadioButton -> "RadioButton"
    is Node.Slider -> "Slider"
    is Node.CircularProgress -> "CircularProgress"
    is Node.LinearProgress -> "LinearProgress"
    is Node.RawCode -> "RawCode"
    is Node.TabRow -> "TabRow"
    is Node.Tab -> "Tab"
    is Node.NavigationBar -> "NavigationBar"
    is Node.NavItem -> "NavItem"
    is Node.Chip -> "Chip"
    is Node.BadgedBox -> "BadgedBox"
    is Node.Canvas -> "Canvas"
    is Node.Line -> "Line"
    is Node.RectShape -> "Rect"
    is Node.CircleShape -> "Circle"
    is Node.EllipseShape -> "Ellipse"
    is Node.ArcShape -> "Arc"
}

/** Return a copy of [this] with its id replaced. */
fun Node.withId(id: String): Node = when (this) {
    is Node.Text -> copy(id = id)
    is Node.Button -> copy(id = id)
    is Node.Spacer -> copy(id = id)
    is Node.Image -> copy(id = id)
    is Node.Divider -> copy(id = id)
    is Node.Icon -> copy(id = id)
    is Node.IconButton -> copy(id = id)
    is Node.TextField -> copy(id = id)
    is Node.Column -> copy(id = id)
    is Node.Row -> copy(id = id)
    is Node.Box -> copy(id = id)
    is Node.Card -> copy(id = id)
    is Node.Fab -> copy(id = id)
    is Node.Dialog -> copy(id = id)
    is Node.BottomSheet -> copy(id = id)
    is Node.Scaffold -> copy(id = id)
    is Node.TopAppBar -> copy(id = id)
    is Node.Slot -> copy(id = id)
    is Node.Instance -> copy(id = id)
    is Node.Composable -> copy(id = id)
    is Node.Artboard -> copy(id = id)
    is Node.Switch -> copy(id = id)
    is Node.Checkbox -> copy(id = id)
    is Node.RadioButton -> copy(id = id)
    is Node.Slider -> copy(id = id)
    is Node.CircularProgress -> copy(id = id)
    is Node.LinearProgress -> copy(id = id)
    is Node.RawCode -> copy(id = id)
    is Node.TabRow -> copy(id = id)
    is Node.Tab -> copy(id = id)
    is Node.NavigationBar -> copy(id = id)
    is Node.NavItem -> copy(id = id)
    is Node.Chip -> copy(id = id)
    is Node.BadgedBox -> copy(id = id)
    is Node.Canvas -> copy(id = id)
    is Node.Line -> copy(id = id)
    is Node.RectShape -> copy(id = id)
    is Node.CircleShape -> copy(id = id)
    is Node.EllipseShape -> copy(id = id)
    is Node.ArcShape -> copy(id = id)
}

/** Return a copy of [this] with its modifier chain replaced. */
fun Node.withModifier(modifier: List<ModifierSpec>): Node = when (this) {
    is Node.Text -> copy(modifier = modifier)
    is Node.Button -> copy(modifier = modifier)
    is Node.Spacer -> copy(modifier = modifier)
    is Node.Icon -> copy(modifier = modifier)
    is Node.IconButton -> copy(modifier = modifier)
    is Node.TextField -> copy(modifier = modifier)
    is Node.Column -> copy(modifier = modifier)
    is Node.Row -> copy(modifier = modifier)
    is Node.Box -> copy(modifier = modifier)
    is Node.Image -> copy(modifier = modifier)
    is Node.Divider -> copy(modifier = modifier)
    is Node.Card -> copy(modifier = modifier)
    is Node.Fab -> copy(modifier = modifier)
    is Node.Dialog -> copy(modifier = modifier)
    is Node.BottomSheet -> copy(modifier = modifier)
    is Node.Scaffold -> copy(modifier = modifier)
    is Node.TopAppBar -> copy(modifier = modifier)
    is Node.Slot -> copy(modifier = modifier)
    is Node.Instance -> copy(modifier = modifier)
    is Node.Composable -> copy(modifier = modifier)
    is Node.Artboard -> copy(modifier = modifier)
    is Node.Switch -> copy(modifier = modifier)
    is Node.Checkbox -> copy(modifier = modifier)
    is Node.RadioButton -> copy(modifier = modifier)
    is Node.Slider -> copy(modifier = modifier)
    is Node.CircularProgress -> copy(modifier = modifier)
    is Node.LinearProgress -> copy(modifier = modifier)
    is Node.RawCode -> copy(modifier = modifier)
    is Node.TabRow -> copy(modifier = modifier)
    is Node.Tab -> copy(modifier = modifier)
    is Node.NavigationBar -> copy(modifier = modifier)
    is Node.NavItem -> copy(modifier = modifier)
    is Node.Chip -> copy(modifier = modifier)
    is Node.BadgedBox -> copy(modifier = modifier)
    is Node.Canvas -> copy(modifier = modifier)
    is Node.Line -> copy(modifier = modifier)
    is Node.RectShape -> copy(modifier = modifier)
    is Node.CircleShape -> copy(modifier = modifier)
    is Node.EllipseShape -> copy(modifier = modifier)
    is Node.ArcShape -> copy(modifier = modifier)
}
