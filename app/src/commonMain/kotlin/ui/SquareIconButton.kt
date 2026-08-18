package composer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Small square icon button for inline actions (move, delete, etc.). */
@Composable
fun SquareIconButton(
    icon: AppIconKind,
    enabled: Boolean = true,
    danger: Boolean = false,
    tip: String? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fg = when {
        !enabled -> Tk.textMuted
        danger -> Tk.danger
        hovered -> Tk.textPrimary
        else -> Tk.textSecondary
    }
    val bg = when {
        !enabled -> Color.Transparent
        hovered && danger -> Tk.dangerSoft
        hovered -> Tk.elevated
        else -> Tk.panelAlt
    }
    val body: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(Tk.rXs))
                .background(bg)
                .border(1.dp, if (hovered && enabled) Tk.borderStrong else Tk.border, RoundedCornerShape(Tk.rXs))
                .hoverable(interaction, enabled)
                .clickable(interactionSource = interaction, indication = null, enabled = enabled) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, Modifier.size(15.dp), tint = fg)
        }
    }
    if (tip != null) Tip(tip, body) else body()
}
