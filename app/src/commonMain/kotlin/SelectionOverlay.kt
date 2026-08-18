package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import composer.ui.Tk
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle

/**
 * Move grip + resize handles laid over the selected node — in SCREEN space
 * (a sibling of the zoomed layer). The overlay is a FULL-CANVAS box and every
 * piece of chrome is positioned at the [screen] rect but CLIPPED to the
 * [viewport]: at deep zoom the selection rect can be hundreds of thousands of
 * px, far beyond what Compose layout constraints can represent — a box sized
 * to the selection gets silently clamped to the parent (it rendered as a flat
 * line). Clipping keeps every layout node viewport-sized at ANY zoom, while
 * the outline itself is a float-px draw (no layout limits).
 * [scale] converts drag travel (window px) into design dp; [frameCoords]
 * (the pre-scale space) converts drill taps for hit-testing.
 */
@Composable
internal fun SelectionOverlay(
    screen: Rect,
    viewport: Size,
    dimsLabel: String,
    density: Density,
    cornerPx: Float,
    scale: Float,
    frameCoords: LayoutCoordinates?,
    anchorMove: Boolean,
    onMove: (Int, Int) -> Unit,
    onResize: (Int, Int) -> Unit,
    onDrill: (Offset) -> Unit,
    // Body-grip drag override + end hook: screens route the body drag through the
    // SNAPPED move (edge/corner anchor-compensation keeps plain onMove — mixing
    // snap into resize deltas would fight the handles).
    onBodyMove: ((Int, Int) -> Unit)? = null,
    onBodyMoveEnd: () -> Unit = {},
    resizable: Boolean = true,
) {
    // The body's real layout coordinates — used to convert a tap to frame space exactly,
    // instead of deriving it from `screen` (which drifted from the body's actual placement).
    var bodyCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val accent = Tk.accent
    val onePx = with(density) { 1.dp.toPx() }
    val gap = onePx // 1dp — pushes the outline OUTSIDE the component
    val outer = Rect(screen.left - gap, screen.top - gap, screen.right + gap, screen.bottom + gap)
    fun pxToDp(v: Float) = with(density) { v.toDp() }

    Box(Modifier.fillMaxSize()) {
        // Outline: drawn over the full canvas in float px — huge rects are fine here.
        Box(
            Modifier.matchParentSize().drawBehind {
                val sw = onePx
                val inset = sw / 2f
                val tl = Offset(outer.left + inset, outer.top + inset)
                val sz = Size(outer.width - sw, outer.height - sw)
                if (cornerPx > 0f) {
                    val r = (cornerPx + gap - inset).coerceAtLeast(0f)
                    drawRoundRect(accent, topLeft = tl, size = sz, cornerRadius = CornerRadius(r), style = Stroke(sw))
                } else {
                    drawRect(accent, topLeft = tl, size = sz, style = Stroke(sw))
                }
            },
        )

        // Body (move/drill): the selection ∩ viewport, so its layout stays small.
        // A tap drills one level deeper into the child under the cursor; drag moves
        // the node (5dp dead-zone so jitter clicks don't write an Offset; below the
        // threshold nothing is consumed and the tap detector sees a clean tap).
        val bl = screen.left.coerceIn(0f, viewport.width)
        val bt = screen.top.coerceIn(0f, viewport.height)
        val br = screen.right.coerceIn(0f, viewport.width)
        val bb = screen.bottom.coerceIn(0f, viewport.height)
        if (br - bl >= 1f && bb - bt >= 1f) {
            Box(
                Modifier
                    .offset { IntOffset(bl.roundToInt(), bt.roundToInt()) }
                    .size(pxToDp(br - bl), pxToDp(bb - bt))
                    .onGloballyPositioned { bodyCoords = it }
                    .pointerInput(frameCoords) {
                        detectTapGestures(onTap = { local ->
                            val fc = frameCoords
                            val bc = bodyCoords
                            if (fc != null && bc != null) onDrill(fc.localPositionOf(bc, local))
                        })
                    }
                    .windowAnchoredDrag(scale, { bodyCoords }, thresholdDp = 5f, onEnd = onBodyMoveEnd) { dx, dy ->
                        (onBodyMove ?: onMove)(dx, dy)
                    },
            )
        }

        // Figma-style resize chrome: invisible EDGE zones (straddling the outline,
        // native resize cursor on hover) + four white corner squares — each placed
        // only along its edge's VISIBLE segment. Composables are NOT resizable
        // (they hug content; their preset is picked in the inspector) — [resizable]
        // skips all of this for them.
        val vy0 = outer.top.coerceAtLeast(0f)
        val vy1 = outer.bottom.coerceAtMost(viewport.height)
        val vx0 = outer.left.coerceAtLeast(0f)
        val vx1 = outer.right.coerceAtMost(viewport.width)
        if (resizable) {
        val thick = 10f * onePx // edge hit thickness (10dp), straddling the outline

        @Composable
        fun edge(x: Float, y: Float, w: Float, h: Float, cursor: String, onDelta: (Int, Int) -> Unit) {
            if (w < 1f || h < 1f) return
            ResizeEdge(
                Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .size(pxToDp(w), pxToDp(h)),
                cursor, scale, onDelta,
            )
        }
        if (outer.right >= -thick && outer.right <= viewport.width + thick) {
            edge(outer.right - thick / 2f, vy0, thick, vy1 - vy0, "ew-resize") { dx, _ -> onResize(dx, 0) }
        }
        if (outer.left >= -thick && outer.left <= viewport.width + thick) {
            edge(outer.left - thick / 2f, vy0, thick, vy1 - vy0, "ew-resize") { dx, _ ->
                onResize(-dx, 0); if (anchorMove) onMove(dx, 0)
            }
        }
        if (outer.bottom >= -thick && outer.bottom <= viewport.height + thick) {
            edge(vx0, outer.bottom - thick / 2f, vx1 - vx0, thick, "ns-resize") { _, dy -> onResize(0, dy) }
        }
        if (outer.top >= -thick && outer.top <= viewport.height + thick) {
            edge(vx0, outer.top - thick / 2f, vx1 - vx0, thick, "ns-resize") { _, dy ->
                onResize(0, -dy); if (anchorMove) onMove(0, dy)
            }
        }

        val cornerHalf = 8f * onePx // half of the 16dp corner hit box
        @Composable
        fun corner(cx: Float, cy: Float, cursor: String, onDelta: (Int, Int) -> Unit) {
            if (cx < -cornerHalf || cx > viewport.width + cornerHalf) return
            if (cy < -cornerHalf || cy > viewport.height + cornerHalf) return
            CornerHandle(
                Modifier.offset { IntOffset((cx - cornerHalf).roundToInt(), (cy - cornerHalf).roundToInt()) },
                cursor, scale, onDelta,
            )
        }
        corner(outer.left, outer.top, "nwse-resize") { dx, dy ->
            onResize(-dx, -dy); if (anchorMove) onMove(dx, dy)
        }
        corner(outer.right, outer.top, "nesw-resize") { dx, dy ->
            onResize(dx, -dy); if (anchorMove) onMove(0, dy)
        }
        corner(outer.left, outer.bottom, "nesw-resize") { dx, dy ->
            onResize(-dx, dy); if (anchorMove) onMove(dx, 0)
        }
        corner(outer.right, outer.bottom, "nwse-resize") { dx, dy ->
            onResize(dx, dy)
        }
        }

        // Figma-style dimensions pill under the selection's bottom edge (when that
        // edge is on screen); centered on the visible span of the selection. A
        // viewport narrower than the pill (IDE tool window) inverts the clamp
        // range — coerceIn would throw and kill the composition, so skip it.
        if (outer.bottom >= 0f && outer.bottom <= viewport.height - 12f * onePx &&
            viewport.width >= 120f * onePx
        ) {
            val cx = ((vx0 + vx1) / 2f).coerceIn(60f * onePx, viewport.width - 60f * onePx)
            Box(
                modifier = Modifier
                    .offset { IntOffset((cx - 60f * onePx).roundToInt(), (outer.bottom + 8f * onePx).roundToInt()) }
                    .size(pxToDp(120f * onePx), pxToDp(24f * onePx)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(accent)
                        .padding(horizontal = 6.dp, vertical = 2.5.dp),
                ) {
                    BasicText(
                        dimsLabel,
                        style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
