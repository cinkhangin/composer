package composer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compact menu row: hover highlight, optional [selected] state (accent label +
 * trailing check), optional [danger] tinting for destructive actions.
 */
@Composable
fun TkMenuItem(
    label: String,
    selected: Boolean = false,
    danger: Boolean = false,
    fontFamily: FontFamily? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(min = 168.dp)
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(Tk.rXs))
            .background(if (hovered) Tk.elevated else Tk.panelAlt.copy(alpha = 0f))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(
            label,
            style = TextStyle(
                color = when {
                    danger -> Tk.danger
                    selected -> Tk.accent
                    else -> Tk.textPrimary
                },
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                fontFamily = fontFamily ?: FontFamily.Default,
            ),
            modifier = Modifier.weight(1f),
        )
        if (selected) SymbolIcon("check", Modifier.size(14.dp), tint = Tk.accent)
    }
}
