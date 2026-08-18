package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import composer.model.ModifierSpec
import composer.ui.AppIcon
import composer.ui.AppIconKind
import composer.ui.Tk

/** The "+" affordance between rows; doubles as the drop indicator during a drag. */
@Composable
internal fun InsertGap(
    index: Int,
    insertAt: Int?,
    dnd: ModifierDndState,
    existing: List<ModifierSpec>,
    canWeight: Boolean,
    alignSeed: ModifierSpec.Align?,
    onToggle: () -> Unit,
    onPick: (ModifierSpec) -> Unit,
) {
    if (dnd.draggingIndex != null) {
        if (dnd.dropIndex == index) {
            Box(Modifier.fillMaxWidth().height(3.dp).padding(horizontal = 4.dp).background(Tk.accent))
        } else {
            Spacer(Modifier.height(6.dp))
        }
        return
    }

    val active = insertAt == index
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lit = hovered || active

    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null) { onToggle() },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(if (lit) Tk.accentSoft else Color.Transparent))
            Box(
                Modifier.size(16.dp).clip(CircleShape).background(if (lit) Tk.accent else Tk.panelAlt).border(1.dp, if (lit) Tk.accent else Tk.border, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(if (active) AppIconKind.Close else AppIconKind.Plus, Modifier.size(10.dp), tint = if (lit) Color.White else Tk.textMuted)
            }
        }
        if (active) {
            AddChipsRow(existing, canWeight, alignSeed, onPick)
            Spacer(Modifier.height(2.dp))
        }
    }
}
