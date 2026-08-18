package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import composer.ui.AppIcon
import composer.ui.AppIconKind
import composer.ui.ColorField
import composer.ui.resolvePickerColor
import composer.ui.Tk

/**
 * Multi-stop gradient editor: a row of stop swatches (click to select, ✕ on the
 * selected one when 3+ stops, + appends a copy of the last stop) above ONE
 * [ColorPicker] editing the selected stop — stacking N full pickers would not fit.
 */
@Composable
internal fun GradientStopsEditor(colors: List<Long>, onChange: (List<Long>) -> Unit) {
    var selected by remember { mutableStateOf(0) }
    val idx = selected.coerceIn(0, colors.lastIndex)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            colors.forEachIndexed { i, c ->
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(resolvePickerColor(c)))
                        .border(if (i == idx) 2.dp else 1.dp, if (i == idx) Tk.accent else Tk.border, RoundedCornerShape(6.dp))
                        .clickable { selected = i },
                )
            }
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Tk.panelAlt)
                    .border(1.dp, Tk.border, RoundedCornerShape(6.dp))
                    .clickable {
                        onChange(colors + colors.last())
                        selected = colors.size
                    },
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(AppIconKind.Plus, Modifier.size(12.dp), tint = Tk.textSecondary)
            }
            if (colors.size > 2) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Tk.panelAlt)
                        .border(1.dp, Tk.border, RoundedCornerShape(6.dp))
                        .clickable {
                            onChange(colors.toMutableList().apply { removeAt(idx) })
                            selected = (idx - 1).coerceAtLeast(0)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(AppIconKind.Close, Modifier.size(12.dp), tint = Tk.textSecondary)
                }
            }
        }
        ColorField(colors[idx]) { new ->
            onChange(colors.toMutableList().apply { set(idx, new) })
        }
    }
}
