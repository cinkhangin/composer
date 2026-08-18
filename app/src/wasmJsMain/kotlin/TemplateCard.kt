package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import composer.ui.ComponentGlyph
import composer.ui.Tk

@Composable
internal fun TemplateCard(template: Template, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Tk.r))
            .background(if (hovered) Tk.elevated else Tk.panel)
            .border(1.dp, if (hovered) Tk.borderStrong else Tk.border, RoundedCornerShape(Tk.r))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onOpen() },
    ) {
        // Preview band: a quiet accent wash with the template's glyph.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .background(Brush.verticalGradient(listOf(Tk.accentSoft, Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(Tk.panel), contentAlignment = Alignment.Center) {
                ComponentGlyph(template.glyph, Modifier.size(20.dp))
            }
        }
        Column(Modifier.padding(horizontal = 14.dp).padding(bottom = 14.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(template.name, style = TextStyle(color = Tk.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium))
            BasicText(
                template.description,
                style = TextStyle(color = Tk.textMuted, fontSize = 11.5.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
