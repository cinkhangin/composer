package composer

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import composer.ui.Tk

/**
 * Figma-style corner handle: a small white square with an accent border, with a
 * generous invisible hit area and a diagonal resize cursor.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun CornerHandle(modifier: Modifier, cursor: String, scale: Float, onDelta: (Int, Int) -> Unit) {
    val accent = Tk.accent
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var dragging by remember { mutableStateOf(false) }
    val currentOnDelta by rememberUpdatedState(onDelta)
    Box(
        modifier = modifier
            .size(16.dp) // hit area; the visible square is drawn centered
            .onGloballyPositioned { coords = it }
            .onPointerEvent(PointerEventType.Enter) { setCanvasCursor(cursor) }
            .onPointerEvent(PointerEventType.Exit) { if (!dragging) setCanvasCursor("default") }
            .windowAnchoredDrag(
                scale, { coords },
                onStart = { dragging = true; setCanvasCursor(cursor) },
                onEnd = { dragging = false; setCanvasCursor("default") },
                onDelta = { dx, dy -> currentOnDelta(dx, dy) },
            )
            .drawBehind {
                // 7dp white square + 1.25dp accent border (screen-space; plain dp at any zoom).
                val s = 7.dp.toPx()
                val bw = 1.25.dp.toPx()
                val tl = Offset((size.width - s) / 2f, (size.height - s) / 2f)
                drawRect(Color.White, topLeft = tl, size = Size(s, s))
                drawRect(accent, topLeft = tl, size = Size(s, s), style = Stroke(bw))
            },
    )
}
