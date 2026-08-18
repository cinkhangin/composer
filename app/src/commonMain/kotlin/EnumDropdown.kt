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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import composer.ui.AppIcon
import composer.ui.AppIconKind
import composer.ui.Tk
import composer.ui.TkMenu
import composer.ui.TkMenuItem

@Composable
fun <T> EnumDropdown(label: String, value: T, options: List<T>, modifier: Modifier = Modifier, itemLabel: (T) -> String = { Vocab.label(it) }, onChange: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        BasicText(label, style = TextStyle(color = Tk.textMuted, fontSize = 11.sp))
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Tk.rSm))
                    .background(if (hovered || open) Tk.elevated else Tk.panelAlt)
                    .border(1.dp, if (open) Tk.accent else if (hovered) Tk.borderStrong else Tk.border, RoundedCornerShape(Tk.rSm))
                    .hoverable(interaction)
                    .clickable(interactionSource = interaction, indication = null) { open = true }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(itemLabel(value), style = TextStyle(color = Tk.textPrimary, fontSize = 13.sp), modifier = Modifier.weight(1f))
                AppIcon(AppIconKind.ChevronDown, Modifier.size(12.dp), tint = if (open) Tk.accent else Tk.textMuted)
            }
            TkMenu(expanded = open, onDismissRequest = { open = false }) {
                for (option in options) {
                    TkMenuItem(itemLabel(option), selected = option == value) { onChange(option); open = false }
                }
            }
        }
    }
}
