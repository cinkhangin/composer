package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import composer.ui.AppIcon
import composer.ui.AppIconKind
import composer.ui.Tip
import composer.ui.Tk

/** Borderless hover-highlight icon button for toolbar history/theme actions. */
@Composable
internal fun TopIconButton(icon: AppIconKind, tip: String, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Tip(tip) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(Tk.rSm))
                .background(if (hovered && enabled) Tk.elevated else Color.Transparent)
                .hoverable(interaction, enabled)
                .clickable(enabled = enabled) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(
                icon,
                Modifier.size(16.dp),
                tint = when {
                    !enabled -> Tk.textMuted
                    hovered -> Tk.textPrimary
                    else -> Tk.textSecondary
                },
            )
        }
    }
}
