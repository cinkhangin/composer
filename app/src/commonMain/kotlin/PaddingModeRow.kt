package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import composer.model.PaddingMode
import composer.ui.Tk

@Composable
internal fun PaddingModeRow(mode: PaddingMode, onPick: (PaddingMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (m in PaddingMode.entries) {
            val sel = m == mode
            val label = when (m) {
                PaddingMode.All -> "all"
                PaddingMode.Symmetric -> "H,V"
                PaddingMode.Sides -> "sides"
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Tk.rXs))
                    .background(if (sel) Tk.accent else Tk.panelAlt)
                    .border(1.dp, if (sel) Tk.accent else Tk.border, RoundedCornerShape(Tk.rXs))
                    .clickable { onPick(m) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(label, style = TextStyle(color = if (sel) Color.White else Tk.textSecondary, fontSize = 11.sp))
            }
        }
    }
}
