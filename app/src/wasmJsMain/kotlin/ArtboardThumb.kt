package composer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import composer.model.DesignJson
import composer.model.Node
import composer.ui.ComponentGlyph
import composer.ui.Tk

/**
 * The design's artboard map: its screens drawn to scale as rounded rects — a real
 * (if abstract) thumbnail, decoded from the stored JSON. Falls back to a glyph
 * when the file can't be decoded.
 */
@Composable
internal fun ArtboardThumb(file: FileMeta, modifier: Modifier = Modifier) {
    val screens = remember(file.id, file.updatedAt) {
        val root = runCatching { FileStore.loadDesign(file.id)?.let { DesignJson.decode(it) } }.getOrNull()
        when (root) {
            is Node.Artboard -> root.composables.filterIsInstance<Node.Composable>()
            is Node.Composable -> listOf(root) // pre-Artboard save
            else -> emptyList()
        }
    }
    if (screens.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            ComponentGlyph("Composable", Modifier.size(26.dp))
        }
        return
    }
    val fill = Tk.elevated
    val stroke = Tk.borderStrong
    Canvas(modifier) {
        val pad = 14.dp.toPx()
        val minX = screens.minOf { it.x }.toFloat()
        val minY = screens.minOf { it.y }.toFloat()
        val maxX = screens.maxOf { it.x + it.width }.toFloat()
        val maxY = screens.maxOf { it.y + it.height }.toFloat()
        val cw = (maxX - minX).coerceAtLeast(1f)
        val ch = (maxY - minY).coerceAtLeast(1f)
        val scale = minOf((size.width - 2 * pad) / cw, (size.height - 2 * pad) / ch)
        val ox = (size.width - cw * scale) / 2f
        val oy = (size.height - ch * scale) / 2f
        val r = CornerRadius(3.dp.toPx())
        for (s in screens) {
            val tl = Offset(ox + (s.x - minX) * scale, oy + (s.y - minY) * scale)
            val sz = Size(s.width * scale, s.height * scale)
            drawRoundRect(fill, topLeft = tl, size = sz, cornerRadius = r)
            drawRoundRect(stroke, topLeft = tl, size = sz, cornerRadius = r, style = Stroke(1.dp.toPx()))
        }
    }
}
