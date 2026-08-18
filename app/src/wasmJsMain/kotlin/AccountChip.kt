package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import composer.ui.Tk

@Composable
internal fun AccountChip() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Tk.r))
            .background(Tk.panelAlt)
            .border(1.dp, Tk.border, RoundedCornerShape(Tk.r))
            .padding(start = 5.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(Tk.accent), contentAlignment = Alignment.Center) {
            BasicText("G", style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
        }
        Column {
            BasicText("Guest", style = TextStyle(color = Tk.textPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Medium))
            BasicText("Local workspace", style = TextStyle(color = Tk.textMuted, fontSize = 10.5.sp))
        }
    }
}
