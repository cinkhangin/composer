package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import composer.ui.Tk

@Composable
internal fun WebProjectTitle(ws: Workspace) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    BasicTextField(
        value = ws.currentName,
        onValueChange = { ws.currentName = it },
        singleLine = true,
        interactionSource = interaction,
        cursorBrush = SolidColor(Tk.accent),
        textStyle = TextStyle(Tk.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        modifier = Modifier
            .widthIn(min = 80.dp, max = 220.dp)
            .clip(RoundedCornerShape(Tk.rSm))
            .background(if (hovered || focused) Tk.panelAlt else Color.Transparent)
            .border(
                1.dp,
                if (focused) Tk.accent else if (hovered) Tk.borderStrong else Color.Transparent,
                RoundedCornerShape(Tk.rSm),
            )
            .hoverable(interaction)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}
