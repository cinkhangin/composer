package composer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
internal fun SVSquare(h: Float, s: Float, v: Float, onChange: (Float, Float) -> Unit) {
    val hue = hueColor(h)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(128.dp)
            .clip(RoundedCornerShape(6.dp))
            .pressDrag { pos, sz -> svApply(pos, sz, onChange) },
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, hue)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val cx = (s * size.width).coerceIn(0f, size.width)
        val cy = ((1f - v) * size.height).coerceIn(0f, size.height)
        drawCircle(Color.Black, radius = 9f, center = Offset(cx, cy), style = Stroke(width = 3f))
        drawCircle(Color.White, radius = 9f, center = Offset(cx, cy), style = Stroke(width = 1.5f))
    }
}
