package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import composer.ui.Tk

@Composable
internal fun ThemeSwatch(label: String, color: Long, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.size(width = 48.dp, height = 46.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(Tk.rSm))
                .background(androidx.compose.ui.graphics.Color(color))
                .border(if (selected) 2.dp else 1.dp, if (selected) Tk.accent else Tk.border, RoundedCornerShape(Tk.rSm))
                .clickable { onClick() },
        )
        BasicText(
            label,
            style = TextStyle(color = if (selected) Tk.textPrimary else Tk.textMuted, fontSize = 8.5.sp),
            maxLines = 1,
        )
    }
}
