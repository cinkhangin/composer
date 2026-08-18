package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import composer.ui.AppIcon
import composer.ui.AppIconKind
import composer.ui.Tk

/** Dashed "blank canvas" card — doubles as the empty state. */
@Composable
internal fun NewDesignCard(onNew: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val stroke = if (hovered) Tk.accent else Tk.borderStrong
    Column(
        modifier = Modifier
            .width(CARD_WIDTH)
            .height(CARD_HEIGHT)
            .clip(RoundedCornerShape(Tk.r))
            .background(if (hovered) Tk.panel else Color.Transparent)
            .drawBehind {
                val sw = 1.dp.toPx()
                drawRoundRect(
                    color = stroke,
                    topLeft = Offset(sw / 2f, sw / 2f),
                    size = Size(size.width - sw, size.height - sw),
                    cornerRadius = CornerRadius(Tk.r.toPx()),
                    style = Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 7f))),
                )
            }
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onNew() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Tk.accentSoft), contentAlignment = Alignment.Center) {
            AppIcon(AppIconKind.Plus, Modifier.size(20.dp), tint = Tk.accent)
        }
        Spacer(Modifier.height(10.dp))
        BasicText("New design", style = TextStyle(color = Tk.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium))
        Spacer(Modifier.height(2.dp))
        BasicText("Blank canvas", style = TextStyle(color = Tk.textMuted, fontSize = 11.5.sp))
    }
}
