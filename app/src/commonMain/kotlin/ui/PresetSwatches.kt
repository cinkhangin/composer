package composer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun PresetSwatches(onPick: (Long) -> Unit) {
    val swatches = listOf(
        0xFFFFFFFF, 0xFF000000, 0xFFE0E0E0, 0xFF9E9E9E,
        0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF3F51B5,
        0xFF2196F3, 0xFF00BCD4, 0xFF009688, 0xFF4CAF50,
        0xFFFFC107, 0xFFFF9800,
    )
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (c in swatches) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(Tk.rXs))
                    .background(Color(c))
                    .border(1.dp, Tk.borderStrong, RoundedCornerShape(Tk.rXs))
                    .clickable { onPick(c) },
            )
        }
    }
}
