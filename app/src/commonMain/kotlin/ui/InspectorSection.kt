package composer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A Figma-style collapsible inspector section: a header row (uppercase title, expand
 * chevron, optional trailing "+" action) over a body of controls. Expanded state is
 * remembered per [title] across selections. Sections are separated by a top divider so
 * the inspector reads as a stack of property groups rather than one long form.
 */
@Composable
fun InspectorSection(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    startExpanded: Boolean = true,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(startExpanded) }
    val interaction = remember { MutableInteractionSource() }
    Column(modifier = modifier.fillMaxWidth()) {
        HDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clickable(interactionSource = interaction, indication = null) { expanded = !expanded }
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AppIcon(
                if (expanded) AppIconKind.ChevronDown else AppIconKind.ChevronRight,
                Modifier.size(12.dp),
                tint = Tk.textMuted,
            )
            Box(Modifier.weight(1f)) { SectionHeader(title) }
            if (trailing != null) trailing()
        }
        if (expanded) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                content()
            }
        }
    }
}
