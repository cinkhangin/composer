package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import composer.ui.Tk

@Composable
internal fun ScreenDimensionField(label: String, value: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val fieldFocused by interaction.collectIsFocusedAsState()
    if (!focused && text != value.toString()) text = value.toString()
    val valid = text.toIntOrNull()?.let { it > 0 } == true

    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(label, style = TextStyle(Tk.textMuted, fontSize = 11.sp))
        BasicTextField(
            value = text,
            onValueChange = { next ->
                text = next
                next.toIntOrNull()?.takeIf { it > 0 }?.let(onChange)
            },
            singleLine = true,
            interactionSource = interaction,
            cursorBrush = SolidColor(if (valid) Tk.accent else Tk.danger),
            textStyle = TextStyle(if (valid) Tk.textPrimary else Tk.danger, fontSize = 12.sp),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focused = it.isFocused }
                .clip(RoundedCornerShape(Tk.rXs))
                .background(Tk.panel)
                .border(1.dp, if (!valid) Tk.danger else if (fieldFocused) Tk.accent else Tk.border, RoundedCornerShape(Tk.rXs))
                .padding(horizontal = 7.dp, vertical = 6.dp),
        )
    }
}
