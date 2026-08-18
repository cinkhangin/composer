package composer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Pill button used across the toolbar. `primary` = filled accent; otherwise a ghost button. */
@Composable
fun ToolButton(
    label: String,
    enabled: Boolean = true,
    primary: Boolean = false,
    icon: AppIconKind? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val bg = when {
        !enabled -> Color.Transparent
        primary -> if (hovered) Tk.accentHover else Tk.accent
        hovered -> Tk.elevated
        else -> Tk.panelAlt
    }
    val fg = when {
        !enabled -> Tk.textMuted
        primary -> Color.White
        hovered -> Tk.textPrimary
        else -> Tk.textSecondary
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Tk.rSm))
            .background(bg)
            .then(if (!primary) Modifier.border(1.dp, if (hovered && enabled) Tk.borderStrong else Tk.border, RoundedCornerShape(Tk.rSm)) else Modifier)
            .heightIn(min = 32.dp)
            .hoverable(interaction, enabled)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) { onClick() }
            .padding(horizontal = if (label.isEmpty()) 8.dp else 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) AppIcon(icon, Modifier.size(14.dp), tint = fg)
            if (label.isNotEmpty()) Text(
                label,
                color = fg,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                // Trim the line-box padding and center the glyphs so the label sits level
                // with the icon (Compose Text otherwise renders slightly low).
                style = LocalTextStyle.current.copy(
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
                ),
            )
        }
    }
}
