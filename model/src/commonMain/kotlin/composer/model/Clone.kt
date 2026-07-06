package composer.model

/**
 * Deep-copy [this] subtree, assigning every node a fresh id from [newId].
 * Used for copy/paste and duplicate so pasted nodes never collide with
 * existing ids. Pure — no Compose.
 */
/**
 * Return a tree where every node id is unique. The first occurrence of an id is
 * kept; any later node sharing it is renamed. Used when loading designs so the
 * editor never has two nodes with the same id (which would break selection).
 */
fun Node.dedupeIds(): Node {
    val used = HashSet<String>()
    var counter = 0
    fun unique(preferred: String): String {
        if (used.add(preferred)) return preferred
        var id: String
        do { id = "dup${counter++}" } while (!used.add(id))
        return id
    }
    fun visit(node: Node): Node {
        val id = unique(node.id)
        return node.withId(id).mapChildren { visit(it) }
    }
    return visit(this)
}

fun Node.cloneWithNewIds(newId: () -> String): Node = when (this) {
    is Node.Text -> copy(id = newId())
    is Node.Instance -> copy(id = newId()) // keeps refId — a copy is another instance
    is Node.Spacer -> copy(id = newId())
    is Node.Image -> copy(id = newId())
    is Node.Divider -> copy(id = newId())
    is Node.Icon -> copy(id = newId())
    is Node.IconButton -> copy(id = newId())
    is Node.TextField -> copy(id = newId())
    is Node.Switch -> copy(id = newId())
    is Node.Checkbox -> copy(id = newId())
    is Node.RadioButton -> copy(id = newId())
    is Node.Slider -> copy(id = newId())
    is Node.CircularProgress -> copy(id = newId())
    is Node.LinearProgress -> copy(id = newId())
    is Node.RawCode -> copy(id = newId())
    is Node.Column -> copy(id = newId(), children = children.map { it.cloneWithNewIds(newId) })
    is Node.Row -> copy(id = newId(), children = children.map { it.cloneWithNewIds(newId) })
    is Node.Box -> copy(id = newId(), children = children.map { it.cloneWithNewIds(newId) })
    is Node.Card -> copy(id = newId(), children = children.map { it.cloneWithNewIds(newId) })
    is Node.Fab -> copy(id = newId(), children = children.map { it.cloneWithNewIds(newId) })
    is Node.Dialog -> copy(id = newId(), children = children.map { it.cloneWithNewIds(newId) })
    is Node.BottomSheet -> copy(id = newId(), children = children.map { it.cloneWithNewIds(newId) })
    is Node.Scaffold -> copy(
        id = newId(),
        topBar = topBar?.cloneWithNewIds(newId),
        bottomBar = bottomBar?.cloneWithNewIds(newId),
        fab = fab?.cloneWithNewIds(newId),
        children = children.map { it.cloneWithNewIds(newId) },
    )
    is Node.TopAppBar -> copy(
        id = newId(),
        title = title?.cloneWithNewIds(newId),
        navigationIcon = navigationIcon?.cloneWithNewIds(newId),
        actions = actions.map { it.cloneWithNewIds(newId) },
    )
    is Node.Slot -> copy(id = newId(), children = children.map { it.cloneWithNewIds(newId) })
    is Node.Button -> copy(id = newId(), children = children.map { it.cloneWithNewIds(newId) })
    is Node.Composable -> copy(id = newId(), children = children.map { it.cloneWithNewIds(newId) })
    is Node.Artboard -> copy(id = newId(), composables = composables.map { it.cloneWithNewIds(newId) })
}
