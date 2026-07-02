package composer.model

/**
 * Reusable-component helpers (pure). A *component* is a normal node registered
 * in [Node.Artboard.componentIds] (the *main*); [Node.Instance] nodes reference
 * it by id and render/emit it as one unit. The component's name is its layer
 * name — which is also the generated `@Composable fun` name.
 */

/** Registered component ids whose main node still exists, in registry order. */
fun Node.Artboard.validComponentIds(): List<String> =
    componentIds.filter { findById(it) != null }

/**
 * Every node id reachable from component [refId]'s subtree, EXPANDING instances
 * transitively — i.e. the full set of nodes the component would render. Used for
 * cycle detection; missing refs are skipped (they render placeholders).
 */
fun Node.expandedIds(refId: String): Set<String> {
    val ids = mutableSetOf<String>()
    val visitedRefs = mutableSetOf<String>()
    fun expand(id: String) {
        if (!visitedRefs.add(id)) return
        val main = findById(id) ?: return
        fun walk(node: Node) {
            ids += node.id
            if (node is Node.Instance) expand(node.refId)
            node.childNodes().forEach(::walk)
        }
        walk(main)
    }
    expand(refId)
    return ids
}

/**
 * May an instance of [refId] be inserted at a spot whose ancestor chain is
 * [ancestorIds] (root → parent)? False when it would create a cycle: the
 * insertion point lies inside a subtree that the component's own expansion
 * contains (including the main itself).
 */
fun Node.canInstantiate(refId: String, ancestorIds: List<String>): Boolean {
    if (findById(refId) == null) return false
    val expanded = expandedIds(refId)
    return ancestorIds.none { it in expanded }
}
