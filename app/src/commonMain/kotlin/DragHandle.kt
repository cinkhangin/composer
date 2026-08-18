package composer

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import composer.ui.AppIcon
import composer.ui.AppIconKind
import composer.ui.Tk

@Composable
internal fun DragHandle(index: Int, dnd: ModifierDndState, onReorder: (Int, Int) -> Unit) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .onGloballyPositioned { c -> dnd.gripTops[index] = c.positionInWindow().y }
            .pointerInput(index) {
                detectDragGestures(
                    onDragStart = { dnd.start(index) },
                    onDrag = { change, _ ->
                        change.consume()
                        dnd.update((dnd.gripTops[index] ?: 0f) + change.position.y)
                    },
                    onDragEnd = { dnd.end(onReorder) },
                    onDragCancel = { dnd.cancel() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(AppIconKind.Grip, Modifier.size(14.dp), tint = Tk.textMuted)
    }
}
