package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import composer.ui.AppIconKind
import composer.ui.Island
import composer.ui.Tk
import composer.ui.TkMenu
import composer.ui.TkMenuItem
import composer.ui.ToolButton
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle

/**
 * Standalone mode can create composables here. Module discovery instead shows
 * the discovered count; function creation waits for stable annotation identity.
 */
@Composable
internal fun SizeBadge(state: EditorState, appMode: Boolean, modifier: Modifier = Modifier) {
    var componentMenuOpen by remember { mutableStateOf(false) }
    Island(modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (appMode) {
                BasicText(
                    "${state.composables.size} composables",
                    style = TextStyle(color = Tk.textSecondary, fontSize = 12.sp),
                )
            } else {
                ToolButton("Composable", icon = AppIconKind.Plus) { state.addComposable() }
            }
            val comps = state.componentDefs()
            if (comps.isNotEmpty()) {
                Box(Modifier.width(1.dp).height(20.dp).padding(horizontal = 2.dp).background(Tk.border))
                Box {
                    // A module can expose dozens of reusable composables. Keeping
                    // every name as a chip made this floating island wider than the
                    // canvas (and eventually overlapped or clipped the chips). A
                    // compact trigger keeps the palette stable; TkMenu supplies its
                    // own bounded, vertically scrollable list for large modules.
                    ToolButton(
                        label = if (comps.size == 1) comps.single().second else "Components (${comps.size})",
                        icon = AppIconKind.Layers,
                    ) { componentMenuOpen = true }
                    TkMenu(
                        expanded = componentMenuOpen,
                        onDismissRequest = { componentMenuOpen = false },
                    ) {
                        comps.forEach { (refId, name) ->
                            TkMenuItem(name) {
                                componentMenuOpen = false
                                state.insertInstanceOf(refId)
                            }
                        }
                    }
                }
            }
        }
    }
}
