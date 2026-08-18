package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import composer.ui.ComponentGlyph
import composer.ui.Tk

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaletteTool(type: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(type, fontSize = 12.sp) } },
        state = rememberTooltipState(),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(Tk.rSm))
                .background(if (hovered) Tk.elevated else Color.Transparent)
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null) { onClick() }
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(Tk.rXs)).background(Tk.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                ComponentGlyph(type, Modifier.size(17.dp), tint = if (hovered) Tk.accentHover else Tk.accent)
            }
        }
    }
}
