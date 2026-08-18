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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun AlphaSlider(a: Float, h: Float, s: Float, v: Float, onChange: (Float) -> Unit) {
    val (r, g, b) = hsvToRgb(h, s, v)
    val opaque = Color(r, g, b)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(RoundedCornerShape(8.dp))
            .pressDrag { pos, sz -> alphaApply(pos, sz, onChange) },
    ) {
        checkerboard(6f)
        drawRect(Brush.horizontalGradient(listOf(opaque.copy(alpha = 0f), opaque)))
        drawThumb(a * size.width, size.height)
    }
}
