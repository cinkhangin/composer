package composer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import composer.model.NavAction
import composer.model.Node
import composer.model.withNavAction
import composer.ui.Tk

/**
 * Edits a clickable node's [NavAction]: None / Back / Navigate + target screen.
 * Targets come from the artboard's screens (excluding the node's own screen);
 * the action stores the target's node id — codegen resolves the name at emit.
 */
@Composable
internal fun NavActionEditor(state: EditorState, selected: Node, action: NavAction) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val kind = when (action) {
            NavAction.None -> NavKind.None
            NavAction.Back -> NavKind.Back
            is NavAction.Navigate -> NavKind.Navigate
        }
        val ownScreen = state.screenOf(selected.id)?.id
        val targets = state.composables.filter { it.id != ownScreen }
        EnumDropdown("action", kind, NavKind.entries) { k ->
            val newAction = when (k) {
                NavKind.None -> NavAction.None
                NavKind.Back -> NavAction.Back
                NavKind.Navigate -> NavAction.Navigate(
                    (action as? NavAction.Navigate)?.screenId ?: targets.firstOrNull()?.id ?: "",
                )
            }
            state.update(selected.id, coalesceKey = "nav:${selected.id}") { it.withNavAction(newAction) }
        }
        if (action is NavAction.Navigate) {
            if (targets.isEmpty()) {
                BasicText(
                    "Add another screen to navigate to.",
                    style = TextStyle(color = Tk.textMuted, fontSize = 11.sp),
                )
            } else {
                val current = targets.firstOrNull { it.id == action.screenId }
                EnumDropdown(
                    "to screen",
                    current ?: targets.first(),
                    targets,
                    itemLabel = { state.layerName(it.id) ?: "Composable" },
                ) { t ->
                    state.update(selected.id, coalesceKey = "nav:${selected.id}") { it.withNavAction(NavAction.Navigate(t.id)) }
                }
                if (current == null) {
                    BasicText(
                        "The target screen no longer exists — the action is ignored until you pick one.",
                        style = TextStyle(color = Tk.textMuted, fontSize = 11.sp),
                    )
                }
            }
        }
    }
}
