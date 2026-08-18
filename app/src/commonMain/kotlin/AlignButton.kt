package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import composer.ui.SymbolIcon
import composer.ui.Tk

@Composable
internal fun AlignButton(symbol: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Tk.rXs))
            .background(if (selected) Tk.accent else Tk.panelAlt)
            .border(1.dp, if (selected) Tk.accent else Tk.border, RoundedCornerShape(Tk.rXs))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        SymbolIcon(symbol, Modifier.size(16.dp), tint = if (selected) Color.White else Tk.textSecondary)
    }
}
