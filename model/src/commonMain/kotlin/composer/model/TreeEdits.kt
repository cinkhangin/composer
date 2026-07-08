package composer.model

/**
 * Pure structural edits over the design tree: insert, remove, move. Each returns
 * a new tree (structural sharing); none mutate. No Compose.
 */

/** True for nodes that can hold children. */
fun Node.isContainer(): Boolean = when (this) {
    is Node.Column, is Node.Row, is Node.Box, is Node.Card, is Node.Scaffold, is Node.Button,
    is Node.Fab, is Node.Dialog, is Node.BottomSheet, is Node.TopAppBar,
    is Node.TabRow, is Node.NavigationBar, is Node.BadgedBox, is Node.Canvas,
    is Node.Composable, is Node.Artboard, is Node.Slot -> true
    is Node.Text, is Node.Spacer, is Node.Image, is Node.Divider,
    is Node.Switch, is Node.Checkbox, is Node.RadioButton, is Node.Slider,
    is Node.Icon, is Node.IconButton, is Node.TextField,
    is Node.Tab, is Node.NavItem, is Node.Chip,
    is Node.Line, is Node.RectShape, is Node.CircleShape, is Node.EllipseShape, is Node.ArcShape,
    is Node.CircularProgress, is Node.LinearProgress, is Node.Instance,
    is Node.RawCode -> false
}

/**
 * Structural rule for the special layers: only [Node.Composable] screens may
 * sit directly under the [Node.Artboard], a Composable may sit ONLY there, and
 * a [Node.Slot] is permanent — created with its parent, never moved anywhere.
 * Everything else nests freely inside any container.
 */
/** Canvas draw-call leaves ([Node.Canvas] children). */
fun Node.isShape(): Boolean =
    this is Node.Line || this is Node.RectShape || this is Node.CircleShape ||
        this is Node.EllipseShape || this is Node.ArcShape

fun canParent(parent: Node, child: Node): Boolean = when {
    child is Node.Slot -> false       // slots are fixed to the parent that created them
    parent is Node.Artboard -> child is Node.Composable
    child is Node.Composable -> false // composables live under the artboard only
    child is Node.Artboard -> false   // the artboard is always the root
    // Typed item containers: a TabRow holds only Tabs (and vice versa), a
    // NavigationBar only NavItems — the generated slot APIs demand it.
    parent is Node.TabRow -> child is Node.Tab
    child is Node.Tab -> false
    parent is Node.NavigationBar -> child is Node.NavItem
    child is Node.NavItem -> false
    // A Canvas draws only shapes; shapes exist only inside a Canvas.
    parent is Node.Canvas -> child.isShape()
    child.isShape() -> false
    else -> true
}

/** The node whose children include [id], or null if [id] is the root or absent. */
fun Node.parentOf(id: String): Node? {
    for (child in childNodes()) {
        if (child.id == id) return this
        child.parentOf(id)?.let { return it }
    }
    return null
}

/** Index of [id] among its parent's **content** children, or -1 (slots aren't ordered). */
fun Node.indexInParent(id: String): Int {
    val parent = parentOf(id) ?: return -1
    return parent.contentChildren().indexOfFirst { it.id == id }
}

/** Insert [child] into the container [parentId]'s content at [index] (clamped). No-op if not a container. */
fun Node.insertChild(parentId: String, child: Node, index: Int = Int.MAX_VALUE): Node =
    replaceById(parentId) { parent ->
        if (!parent.isContainer()) return@replaceById parent
        val kids = parent.contentChildren().toMutableList()
        kids.add(index.coerceIn(0, kids.size), child)
        parent.withChildren(kids)
    }

/**
 * Remove the node with [id] wherever it appears. Scaffold slots are permanent
 * [Node.Slot] containers — removal recurses into them but never removes the
 * slot itself (delete a slot's children instead).
 */
fun Node.removeById(id: String): Node = when (this) {
    is Node.Scaffold -> copy(
        topBar = topBar?.removeById(id),
        bottomBar = bottomBar?.removeById(id),
        fab = fab?.removeById(id),
        children = children.filter { it.id != id }.map { it.removeById(id) },
    )
    is Node.TopAppBar -> copy(
        title = if (title?.id == id) null else title?.removeById(id),
        navigationIcon = if (navigationIcon?.id == id) null else navigationIcon?.removeById(id),
        actions = actions.filter { it.id != id }.map { it.removeById(id) },
    )
    else -> {
        val kids = contentChildren()
        if (kids.isEmpty()) this
        else {
            val newKids = kids.filter { it.id != id }.map { it.removeById(id) }
            if (newKids == kids) this else withChildren(newKids)
        }
    }
}

/** Move [id] by [delta] positions among its content siblings (-1 up, +1 down). No-op at the ends. */
fun Node.moveById(id: String, delta: Int): Node {
    val parent = parentOf(id) ?: return this
    val kids = parent.contentChildren()
    val from = kids.indexOfFirst { it.id == id }
    val to = from + delta
    if (from < 0 || to < 0 || to >= kids.size) return this
    val reordered = kids.toMutableList().apply { add(to, removeAt(from)) }
    return replaceById(parent.id) { it.withChildren(reordered) }
}

// --- drag & drop moves (tree view) ----------------------------------------

/**
 * Move [nodeId] to sit just before/after [targetId] among the target's siblings.
 * [offset] 0 = before, 1 = after. No-op if it would create a cycle or the target
 * is the root. Indices are recomputed after removal, so same-parent reorders work.
 */
private fun Node.moveRelativeTo(nodeId: String, targetId: String, offset: Int): Node {
    if (nodeId == targetId) return this
    val node = findById(nodeId) ?: return this
    if (node.findById(targetId) != null) return this // target is inside the dragged subtree
    val parent = parentOf(targetId) ?: return this   // can't drop relative to the root
    if (!canParent(parent, node)) return this        // artboard/composable structural rule
    val removed = removeById(nodeId)
    val siblings = removed.findById(parent.id)?.contentChildren() ?: return this
    val index = siblings.indexOfFirst { it.id == targetId }
    if (index < 0) return this
    return removed.insertChild(parent.id, node, index + offset)
}

fun Node.moveBefore(nodeId: String, targetId: String): Node = moveRelativeTo(nodeId, targetId, 0)

fun Node.moveAfter(nodeId: String, targetId: String): Node = moveRelativeTo(nodeId, targetId, 1)

/** Move [nodeId] to be the last child of container [containerId]. No-op on cycle / non-container. */
fun Node.moveInto(nodeId: String, containerId: String): Node {
    if (nodeId == containerId) return this
    val node = findById(nodeId) ?: return this
    if (node.findById(containerId) != null) return this // container is inside the dragged subtree
    val container = findById(containerId) ?: return this
    if (!container.isContainer()) return this
    if (!canParent(container, node)) return this // artboard/composable structural rule
    val removed = removeById(nodeId)
    val size = removed.findById(containerId)?.contentChildren()?.size ?: 0
    return removed.insertChild(containerId, node, size)
}
