package composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import composer.model.BoxAlignment
import composer.model.HAlignment
import composer.model.HArrangement
import composer.model.Node
import composer.model.VAlignment
import composer.model.VArrangement

/**
 * Non-modifier composable parameters — call-site arguments like arrangement,
 * alignment, and spacing that aren't part of the Modifier chain. Shown above the
 * Modifiers section for Column/Row/Box.
 */
@Composable
internal fun ParamsEditor(state: EditorState, node: Node) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
