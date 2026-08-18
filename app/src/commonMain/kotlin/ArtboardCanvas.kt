package composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import composer.model.Node
import composer.ui.Tk
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer

/**
 * The artboard canvas: every [Node.Composable] screen rendered as its own frame
 * at its (x, y) position, inside ONE zoomed layer and ONE shared coordinate
 * space — so the bounds map and hit-testing work across screens. The selection
 * overlay lives OUTSIDE this layer (screen space, see [Canvas]) so its chrome
 * survives extreme zoom.
 */
@Composable
internal fun ArtboardCanvas(
    state: EditorState,
    scale: Float,
    content: ContentBox,
    bounds: MutableMap<String, Rect>,
    onSpaceCoords: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    var spaceCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // The zoomed layer is ISLAND-SIZED (fillMaxSize), never sized to the content:
    // Modifier.size silently COERCES to the parent's max constraints, so a content
    // box wider/taller than the island (e.g. 3 mobile screens, or one desktop
    // screen) got clamped — shifting the scale origin and breaking every screen
    // -> overlay mapping. Content is centered arithmetically inside instead, and
    // each frame is placed by an unclampable custom layout (see ScreenFrame).
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .fillMaxSize(),
    ) {
        // Reference box: the shared coordinate space for the bounds measurement.
        // Not clipped — screens may extend past the island box.
        Box(
            modifier = Modifier.fillMaxSize().onGloballyPositioned {
                spaceCoords = it
                onSpaceCoords(it)
            },
        ) {
            for (screen in state.composables) {
                key(screen.id) {
                    ScreenFrame(state, screen, scale, content, spaceCoords, bounds)
                }
            }
            // Snap guides (screen drags): full-length lines at the matched artboard
            // coordinate, same centering math as ScreenFrame; stroke /scale so it
            // stays 1dp on screen at any zoom. Draw-only — no pointer handlers.
            val gv = state.snapGuideV
            val gh = state.snapGuideH
            val bars = state.spacingBars
            if (gv != null || gh != null || bars.isNotEmpty()) {
                val textMeasurer = rememberTextMeasurer()
                Box(
                    Modifier.matchParentSize().drawBehind {
                        val stroke = 1.dp.toPx() / scale
                        val guide = Tk.snapGuide
                        fun ax(v: Int) = (size.width - content.w.dp.toPx()) / 2f + (v - content.minX).dp.toPx()
                        fun ay(v: Int) = (size.height - content.h.dp.toPx()) / 2f + (v - content.minY).dp.toPx()
                        if (gv != null) drawLine(guide, Offset(ax(gv), 0f), Offset(ax(gv), size.height), stroke)
                        if (gh != null) drawLine(guide, Offset(0f, ay(gh)), Offset(size.width, ay(gh)), stroke)
                        // Spacing bars: gap segment + end ticks + the gap value —
                        // all /scale so the chrome stays constant-size on screen.
                        val tick = 4.dp.toPx() / scale
                        for (b in bars) {
                            val label = textMeasurer.measure(
                                b.value.toString(),
                                TextStyle(color = guide, fontSize = (10f / scale).sp, fontWeight = FontWeight.Medium),
                            )
                            if (b.horizontal) {
                                val y = ay(b.cross).let { it }
                                val x1 = ax(b.start)
                                val x2 = ax(b.end)
                                drawLine(guide, Offset(x1, y), Offset(x2, y), stroke)
                                drawLine(guide, Offset(x1, y - tick), Offset(x1, y + tick), stroke)
                                drawLine(guide, Offset(x2, y - tick), Offset(x2, y + tick), stroke)
                                drawText(label, topLeft = Offset((x1 + x2) / 2f - label.size.width / 2f, y - tick - label.size.height))
                            } else {
                                val x = ax(b.cross)
                                val y1 = ay(b.start)
                                val y2 = ay(b.end)
                                drawLine(guide, Offset(x, y1), Offset(x, y2), stroke)
                                drawLine(guide, Offset(x - tick, y1), Offset(x + tick, y1), stroke)
                                drawLine(guide, Offset(x - tick, y2), Offset(x + tick, y2), stroke)
                                drawText(label, topLeft = Offset(x + tick + stroke, (y1 + y2) / 2f - label.size.height / 2f))
                            }
                        }
                    },
                )
            }
        }
    }
}
