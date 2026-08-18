package composer

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Invisible resize zone along one edge of the selection. Shows the matching
 * native resize [cursor] on hover (locked while dragging), and drags via
 * [windowAnchoredDrag] so tracking stays cursor-exact.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ResizeEdge(modifier: Modifier, cursor: String, scale: Float, onDelta: (Int, Int) -> Unit) {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var dragging by remember { mutableStateOf(false) }
    // pointerInput captures its lambdas once; route through state so recompositions
    // with fresh callbacks (changed bounds/selection context) stay live mid-gesture.
    val currentOnDelta by rememberUpdatedState(onDelta)
    Box(
        modifier
            .onGloballyPositioned { coords = it }
            .onPointerEvent(PointerEventType.Enter) { setCanvasCursor(cursor) }
            .onPointerEvent(PointerEventType.Exit) { if (!dragging) setCanvasCursor("default") }
            .windowAnchoredDrag(
                scale, { coords },
                onStart = { dragging = true; setCanvasCursor(cursor) },
                onEnd = { dragging = false; setCanvasCursor("default") },
                onDelta = { dx, dy -> currentOnDelta(dx, dy) },
            ),
    )
}
