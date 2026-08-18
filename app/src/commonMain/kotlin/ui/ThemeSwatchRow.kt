package composer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The design theme's tokens as picks. Picking one stores the token REFERENCE —
 * the color follows the theme (and codegen emits `MaterialTheme.colorScheme.<token>`).
 */
@Composable
internal fun ThemeSwatchRow(swatches: List<ThemeSwatch>, selected: Long, onPick: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText("Theme", style = TextStyle(color = Tk.textMuted, fontSize = 11.sp))
            swatches.firstOrNull { it.value == selected }?.let {
                BasicText(it.name, style = TextStyle(color = Tk.accent, fontSize = 11.sp))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (sw in swatches) {
                val sel = sw.value == selected
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(Tk.rXs))
                        .background(Color(sw.resolved))
                        .border(if (sel) 2.dp else 1.dp, if (sel) Tk.accent else Tk.borderStrong, RoundedCornerShape(Tk.rXs))
                        .clickable { onPick(sw.value) },
                )
            }
        }
    }
}
