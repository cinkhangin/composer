package composer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compact themed text field — a label above a bordered input box, matching the
 * inspector's dropdowns (same 10dp/8dp box padding) so all controls are one height.
 * When [isError], text/border/label turn red but input is still accepted.
 */
@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    placeholder: String? = null,
    onFocusChange: (Boolean) -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor = when {
        isError -> Tk.danger
        focused -> Tk.accent
        else -> Tk.border
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        // BasicText with an explicit style, NOT material3 Text: Text inherits the
        // theme's bodyLarge lineHeight (24sp) even with a small fontSize, which made
        // every Field taller than EnumDropdown (whose label is a BasicText).
        BasicText(label, style = TextStyle(color = if (isError) Tk.danger else Tk.textMuted, fontSize = 11.sp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = if (isError) Tk.danger else Tk.textPrimary, fontSize = 13.sp),
            cursorBrush = SolidColor(if (isError) Tk.danger else Tk.accent),
            interactionSource = interaction,
            decorationBox = { inner ->
                if (value.isEmpty() && placeholder != null) {
                    BasicText(placeholder, style = TextStyle(color = Tk.textMuted, fontSize = 13.sp))
                }
                inner()
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { onFocusChange(it.isFocused) }
                .clip(RoundedCornerShape(Tk.rSm))
                .background(Tk.panelAlt)
                .border(1.dp, borderColor, RoundedCornerShape(Tk.rSm))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}
