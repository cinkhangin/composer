package composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import composer.ui.AppIconKind
import composer.ui.Theme
import composer.ui.Tk
import composer.ui.ToolButton

/** Headline + primary actions over a soft accent glow anchored to the top. */
@Composable
internal fun Hero(onNew: () -> Unit, onImport: () -> Unit, importError: String?) {
    val glow = Tk.accent.copy(alpha = if (Theme.isDark) 0.14f else 0.08f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(glow, Color.Transparent),
                        center = Offset(size.width / 2f, -size.height * 0.35f),
                        radius = size.width * 0.55f,
                    ),
                )
            }
            .padding(top = 64.dp, bottom = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BasicText(
            "Design visually. Ship Compose.",
            style = TextStyle(color = Tk.textPrimary, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
        )
        BasicText(
            "Draw your screens on a canvas — Composer generates clean,\ndeterministic Compose Multiplatform code.",
            style = TextStyle(color = Tk.textSecondary, fontSize = 14.5.sp, textAlign = TextAlign.Center, lineHeight = 21.sp),
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolButton("New design", primary = true, icon = AppIconKind.Plus, onClick = onNew)
            ToolButton("Import JSON", icon = AppIconKind.File, onClick = onImport)
        }
        if (importError != null) {
            BasicText(importError, style = TextStyle(color = Tk.danger, fontSize = 12.5.sp))
        }
    }
}
