package composer

import androidx.compose.runtime.Composable
import composer.model.Node

@Composable
internal fun TreeRow(
    node: Node,
    depth: Int,
    state: EditorState,
    expanded: MutableMap<String, Boolean>,
    dnd: TreeDndState,
) {
    RowItem(node, depth, state, expanded, dnd)
    if (expanded[node.id] != false) {
        for (child in node.designerChildren()) TreeRow(child, depth + 1, state, expanded, dnd)
    }
}
