package composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import composer.model.Node
import composer.ui.AppIconKind
import composer.ui.SquareIconButton

@Composable
internal fun Arrange(state: EditorState, selected: Node, lockComposableStructure: Boolean = false) {
    val isRoot = selected.id == state.root.id
    val lockedFunction = lockComposableStructure && selected is Node.Composable
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SquareIconButton(AppIconKind.ArrowUp, enabled = !lockedFunction && state.canMove(selected.id, -1), tip = "Move up") { state.move(selected.id, -1) }
            SquareIconButton(AppIconKind.ArrowDown, enabled = !lockedFunction && state.canMove(selected.id, +1), tip = "Move down") { state.move(selected.id, +1) }
            Box(Modifier.weight(1f))
            SquareIconButton(AppIconKind.Duplicate, enabled = !lockedFunction, tip = "Duplicate (⌘D)") { state.duplicate() }
            SquareIconButton(AppIconKind.Trash, enabled = !isRoot && !lockedFunction, danger = true, tip = "Delete") { state.delete(selected.id) }
        }
    }
}
