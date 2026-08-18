package composer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import composer.ui.Tk
import kotlin.math.roundToInt

/** A fixed-size screen label anchored above its transformed frame. */
@Composable
internal fun ScreenLabel(
    name: String,
    selected: Boolean,
    frameTopLeft: Offset,
    onClick: () -> Unit,
) {
    val gap = with(LocalDensity.current) { 20.dp.toPx() }
    BasicText(
        text = name,
        style = TextStyle(
            color = if (selected) Tk.accent else Tk.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        ),
        maxLines = 1,
        modifier = Modifier
            .offset {
                IntOffset(
                    frameTopLeft.x.roundToInt(),
                    (frameTopLeft.y - gap).roundToInt(),
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    )
}
