package composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import composer.ui.Tk

/**
 * Figma-style hover preview: a passive accent outline around the node a click
 * would select. Pure draw — NO pointer handlers, so it never eats a tap/drag.
 * Same screen-space float-px technique as the SelectionOverlay outline (layout
 * stays viewport-sized; huge zoomed rects are fine as draws).
 */
@Composable
internal fun HoverOutline(screen: Rect, cornerPx: Float, density: Density) {
    val accent = Tk.accent
    val onePx = with(density) { 1.dp.toPx() }
    val gap = onePx // match the selection outline: 1dp OUTSIDE the component
    val outer = Rect(screen.left - gap, screen.top - gap, screen.right + gap, screen.bottom + gap)
    Box(
        Modifier.fillMaxSize().drawBehind {
            val sw = onePx * 1.5f // slightly heavier than the selection stroke, like Figma
            val inset = sw / 2f
            val tl = Offset(outer.left + inset, outer.top + inset)
            val sz = Size(outer.width - sw, outer.height - sw)
            if (sz.width <= 0f || sz.height <= 0f) return@drawBehind
            if (cornerPx > 0f) {
                val r = (cornerPx + gap - inset).coerceAtLeast(0f)
                drawRoundRect(accent, topLeft = tl, size = sz, cornerRadius = CornerRadius(r), style = Stroke(sw))
            } else {
                drawRect(accent, topLeft = tl, size = sz, style = Stroke(sw))
            }
        },
    )
}
