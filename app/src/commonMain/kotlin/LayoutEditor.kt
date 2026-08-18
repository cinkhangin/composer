package composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import composer.model.BoxAlignment
import composer.model.HAlignment
import composer.model.HArrangement
import composer.model.LayoutKind
import composer.model.Node
import composer.model.VAlignment
import composer.model.VArrangement
import composer.model.layoutKind

/** Basic container type plus the parameters specific to that layout. */
@Composable
internal fun LayoutEditor(state: EditorState, node: Node) {
    val kind = node.layoutKind() ?: return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EnumDropdown("Type", kind, LayoutKind.entries) { state.setContainerLayout(node.id, it) }
        when (node) {
            is Node.Column -> {
                EnumDropdown("verticalArrangement", node.verticalArrangement, VArrangement.entries, itemLabel = { it.name }) { v ->
                    state.update(node.id) { (it as Node.Column).copy(verticalArrangement = v) }
                }
                EnumDropdown("horizontalAlignment", node.horizontalAlignment, HAlignment.entries, itemLabel = { it.name }) { v ->
                    state.update(node.id) { (it as Node.Column).copy(horizontalAlignment = v) }
                }
                NumField("spacing (Arrangement.spacedBy, dp)", node.spacing, autoLabel = "None") { v ->
                    state.update(node.id, coalesceKey = "spacing:${node.id}") { (it as Node.Column).copy(spacing = v) }
                }
            }
            is Node.Row -> {
                EnumDropdown("horizontalArrangement", node.horizontalArrangement, HArrangement.entries, itemLabel = { it.name }) { v ->
                    state.update(node.id) { (it as Node.Row).copy(horizontalArrangement = v) }
                }
                EnumDropdown("verticalAlignment", node.verticalAlignment, VAlignment.entries, itemLabel = { it.name }) { v ->
                    state.update(node.id) { (it as Node.Row).copy(verticalAlignment = v) }
                }
                NumField("spacing (Arrangement.spacedBy, dp)", node.spacing, autoLabel = "None") { v ->
                    state.update(node.id, coalesceKey = "spacing:${node.id}") { (it as Node.Row).copy(spacing = v) }
                }
            }
            is Node.Box -> {
                EnumDropdown("contentAlignment", node.contentAlignment, BoxAlignment.entries, itemLabel = { it.name }) { v ->
                    state.update(node.id) { (it as Node.Box).copy(contentAlignment = v) }
                }
            }
            else -> Unit
        }
    }
}
