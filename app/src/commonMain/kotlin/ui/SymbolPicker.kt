package composer.ui

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Searchable picker over the full bundled Material Symbols set (~3,900 icons).
 * Field-height trigger (current icon + name) opening a [TkMenu] with a search box
 * and a scrollable icon grid. [current] empty → shows [fallbackLabel] (the legacy
 * curated IconKind name) until a symbol is picked.
 */
@Composable
fun SymbolPickerField(label: String, current: String, fallbackLabel: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { Symbols.load() }

    Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
        BasicText(label, style = TextStyle(color = Tk.textMuted, fontSize = 11.sp))
        Box {
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Tk.rSm))
                    .background(Tk.panelAlt)
                    .border(1.dp, if (open) Tk.accent else if (hovered) Tk.borderStrong else Tk.border, RoundedCornerShape(Tk.rSm))
                    .hoverable(interaction)
                    .clickable(interactionSource = interaction, indication = null) { open = true }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (current.isNotEmpty()) SymbolIcon(current, Modifier.size(15.dp), tint = Tk.textPrimary)
                BasicText(
                    current.ifEmpty { fallbackLabel },
                    style = TextStyle(color = Tk.textPrimary, fontSize = 12.5.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                AppIcon(AppIconKind.ChevronDown, Modifier.size(12.dp), tint = Tk.textMuted)
            }
            TkMenu(expanded = open, onDismissRequest = { open = false; query = "" }) {
                Column(Modifier.width(258.dp).padding(horizontal = 6.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(
                        value = query,
                        onValueChange = { query = it },
                        label = if (Symbols.loaded) "Search ${Symbols.names.size} icons" else "Loading icons…",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val results = remember(query, Symbols.loaded) {
                        val q = query.trim().lowercase().replace(' ', '_')
                        val all = Symbols.names
                        (if (q.isEmpty()) all else all.filter { q in it }).take(96)
                    }
                    if (results.isEmpty() && Symbols.loaded) {
                        BasicText("No matching icons", style = TextStyle(color = Tk.textMuted, fontSize = 12.sp), modifier = Modifier.padding(4.dp))
                    }
                    Column(
                        modifier = Modifier.height(216.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        results.chunked(7).forEach { rowIcons ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                rowIcons.forEach { name ->
                                    SymbolCell(name, selected = name == current) {
                                        onPick(name)
                                        open = false
                                        query = ""
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
