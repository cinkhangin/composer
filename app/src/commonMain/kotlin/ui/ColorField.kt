package composer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compact color input for the inspector: a Field-height trigger (swatch + hex,
 * or the theme-token name while a token is referenced) that opens the full
 * [ColorPicker] in a dropdown — the inline picker ate ~300dp of panel height.
 */
@Composable
fun ColorField(color: Long, showThemeSwatches: Boolean = true, onColorChange: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val token = if (showThemeSwatches) LocalThemeSwatches.current.firstOrNull { it.value == color } else null
    val display = token?.resolved ?: color
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Tk.rSm))
                .background(Tk.panelAlt)
                .border(1.dp, if (open) Tk.accent else Tk.border, RoundedCornerShape(Tk.rSm))
                .clickable { open = !open }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ColorPreview(display, Modifier.size(16.dp))
            BasicText(
                token?.name ?: "#${hex8(display)}",
                style = TextStyle(color = if (token != null) Tk.accent else Tk.textPrimary, fontSize = 13.sp),
            )
        }
        TkMenu(expanded = open, onDismissRequest = { open = false }) {
            Box(Modifier.width(260.dp).padding(horizontal = 6.dp, vertical = 4.dp)) {
                ColorPicker(color, showThemeSwatches, onColorChange)
            }
        }
    }
}
