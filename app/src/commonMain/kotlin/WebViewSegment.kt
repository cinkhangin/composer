package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import composer.ui.AppIcon
import composer.ui.AppIconKind
import composer.ui.Tk

@Composable
internal fun WebViewSegment(label: String, icon: AppIconKind, active: Boolean, onClick: () -> Unit) {
    val foreground = if (active) Color.White else Tk.textSecondary
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Tk.rSm))
            .background(if (active) Tk.accent else Tk.panelAlt)
            .border(1.dp, if (active) Tk.accent else Tk.border, RoundedCornerShape(Tk.rSm))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AppIcon(icon, Modifier.size(13.dp), foreground)
        BasicText(label, style = TextStyle(foreground, fontSize = 12.5.sp, fontWeight = FontWeight.Medium))
    }
}
