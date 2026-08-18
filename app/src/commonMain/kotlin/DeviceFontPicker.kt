package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import composer.LocalFonts
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

/** Pick a device-installed font (Local Font Access API; Chromium only). Overrides fontFamily. */
@Composable
internal fun DeviceFontPicker(current: String, onPick: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        BasicText("Device font (overrides Font)", style = TextStyle(color = Tk.textMuted, fontSize = 11.sp))
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Tk.rSm))
                    .background(Tk.panelAlt)
                    .border(1.dp, Tk.border, RoundedCornerShape(Tk.rSm))
                    .clickable(enabled = LocalFonts.supported) {
                        busy = true
                        scope.launch { LocalFonts.query(); busy = false; open = LocalFonts.available.isNotEmpty() }
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val label = when {
                    !LocalFonts.supported -> "Installed fonts unavailable"
                    busy -> "Loading…"
                    current.isNotEmpty() -> current
                    else -> "Browse installed fonts…"
                }
                BasicText(
                    label,
                    style = TextStyle(color = if (current.isNotEmpty()) Tk.textPrimary else Tk.textMuted, fontSize = 13.sp),
                    modifier = Modifier.weight(1f),
                )
                if (current.isNotEmpty()) {
                    AppIcon(AppIconKind.Close, Modifier.size(12.dp).clickable { onPick("") }, tint = Tk.textMuted)
                }
            }
            TkMenu(expanded = open, onDismissRequest = { open = false }) {
                // Lazy + height-capped: hundreds of installed fonts would otherwise
                // make the menu screen-tall AND eagerly load every font's bytes.
                // Only composed (≈visible) rows load their font, so each name renders
                // in its own typeface as you scroll (default font until loaded).
                // FIXED size (not heightIn): DropdownMenu measures its content with
                // IntrinsicSize, and LazyColumn (SubcomposeLayout) can't answer
                // intrinsics — an explicit size modifier answers for it.
                val menuHeight = (LocalFonts.available.size * 31).coerceAtMost(320).dp
                LazyColumn(Modifier.width(260.dp).height(menuHeight)) {
                    items(LocalFonts.available, key = { it }) { f ->
                        LaunchedEffect(f) { LocalFonts.load(f) }
                        TkMenuItem(f, selected = f == current, fontFamily = LocalFonts.loaded[f]) {
                            onPick(f); open = false
                        }
                    }
                }
            }
        }
    }
}
