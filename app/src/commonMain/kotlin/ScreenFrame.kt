package composer

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import composer.model.Node
import composer.render.LocalDesignRoot
import composer.render.LocalPreviewParameters
import composer.render.RenderNode
import composer.render.toColorScheme
import kotlin.math.roundToInt

/**
 * One composable's frame at its artboard position.
 * The transparent device viewport clips drawing to the selected screen size,
 * while the registered/selected surface inside still hugs rendered content.
 * The viewport paints no background. Its name label is rendered in the unscaled
 * screen-space overlay by [Canvas].
 */
@Composable
internal fun ScreenFrame(
    state: EditorState,
    screen: Node.Composable,
    scale: Float,
    content: ContentBox,
    spaceCoords: LayoutCoordinates?,
    bounds: MutableMap<String, Rect>,
) {
    fun register(id: String, coords: LayoutCoordinates) {
        spaceCoords?.let { sc ->
            val tl = sc.localPositionOf(coords, Offset.Zero)
            bounds[id] = Rect(tl.x, tl.y, tl.x + coords.size.width, tl.y + coords.size.height)
        }
    }
    Box(
        modifier = Modifier
            // Unclampable placement: measure the frame at its EXACT size (a screen
            // can be larger than the island — Modifier.size would silently coerce)
            // and place it so the frozen content box is centered in the island.
            .layout { measurable, constraints ->
                // The viewport uses the screen maximum; its registered surface
                // remains a separate content-hugging box inside the mask.
                val w = screen.width.dp.roundToPx()
                val h = screen.height.dp.roundToPx()
                val placeable = measurable.measure(Constraints(maxWidth = w, maxHeight = h))
                val ox = ((constraints.maxWidth - content.w.dp.toPx()) / 2f + (screen.x - content.minX).dp.toPx()).roundToInt()
                val oy = ((constraints.maxHeight - content.h.dp.toPx()) / 2f + (screen.y - content.minY).dp.toPx()).roundToInt()
                layout(0, 0) { placeable.place(ox, oy) }
            },
    ) {
        // Keep the mask separate from the measured selection surface: the viewport
        // is always the device maximum, while the surface still hugs its content.
        Box(
            modifier = Modifier
                .size(screen.width.dp, screen.height.dp)
                .clipToBounds(),
        ) {
            // The design's own theme wraps the preview (WYSIWYG with generated
            // MaterialTheme). The viewport paints nothing, so unfilled areas show
            // the neutral canvas surface.
            MaterialTheme(colorScheme = state.theme.toColorScheme()) {
                // MaterialTheme alone does NOT set LocalContentColor (only Surface does) —
                // default-colored Text/Icon follows the theme like a themed app surface.
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onBackground,
                    LocalDesignRoot provides state.root,
                    LocalPreviewParameters provides screen.preview?.parameters.orEmpty()
                        .associate { it.name to it.expression },
                ) {
                    Box(
                        modifier = Modifier
                            // Register the measured surface, not the fixed viewport.
                            // This remains full screen for fill/equal-size content,
                            // hugs smaller content, and is 0×0 when content is empty.
                            .onGloballyPositioned { register(screen.id, it) }
                            .pixelGrid(scale)
                            // A tap on a gap (no child consumed it) selects the composable.
                            .pointerInput(screen.id) { detectTapGestures { state.select(screen.id) } },
                    ) {
                        screen.children.forEach { child ->
                            RenderNode(child, state.selectedId, onSelect = state::selectAt, onBounds = ::register)
                        }
                    }
                }
            }
        }
    }
}
