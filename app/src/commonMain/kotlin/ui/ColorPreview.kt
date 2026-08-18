package composer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun ColorPreview(color: Long, modifier: Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, Tk.borderStrong, RoundedCornerShape(6.dp))) {
        checkerboard(6f)
        drawRect(Color(color))
    }
}
