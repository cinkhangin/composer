package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import composer.ui.HDivider
import composer.ui.SymbolIcon
import composer.ui.Tk
import composer.ui.TkMenu
import composer.ui.TkMenuItem

private data class ScreenPreset(val label: String, val width: Int, val height: Int)

private val screenPresets = listOf(
    ScreenPreset("Phone", DEFAULT_SCREEN_WIDTH, DEFAULT_SCREEN_HEIGHT),
    ScreenPreset("Tablet", 820, 1180),
    ScreenPreset("Desktop", 1440, 900),
)

/** Shared screen-size selector used by both the website and Android Studio designer toolbars. */
@Composable
internal fun ScreenSizeControl(state: EditorState, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val label = if (state.hasMixedScreenSizes) "Mixed sizes" else "${state.screenWidth} × ${state.screenHeight}"

    Box(modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(Tk.rSm))
                .background(if (open || hovered) Tk.elevated else Tk.panelAlt)
                .border(1.dp, if (open) Tk.accent else if (hovered) Tk.borderStrong else Tk.border, RoundedCornerShape(Tk.rSm))
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null) { open = true }
                .padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SymbolIcon("devices", Modifier.size(14.dp), tint = if (open) Tk.accent else Tk.textSecondary)
            BasicText(label, style = TextStyle(Tk.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium))
            SymbolIcon("keyboard_arrow_down", Modifier.size(13.dp), tint = Tk.textMuted)
        }

        TkMenu(expanded = open, onDismissRequest = { open = false }) {
            BasicText(
                "SCREEN SIZE · ALL COMPOSABLES",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                style = TextStyle(Tk.textMuted, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold),
            )
            screenPresets.forEach { preset ->
                val selected = !state.hasMixedScreenSizes &&
                    state.screenWidth == preset.width && state.screenHeight == preset.height
                TkMenuItem("${preset.label}  ${preset.width} × ${preset.height}", selected = selected) {
                    state.setScreenSize(preset.width, preset.height)
                    open = false
                }
            }
            HDivider()
            BasicText(
                "Custom maximum",
                modifier = Modifier.padding(start = 10.dp, top = 7.dp, end = 10.dp),
                style = TextStyle(Tk.textMuted, fontSize = 11.sp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScreenDimensionField("W", state.screenWidth, Modifier.width(94.dp)) {
                    state.setScreenSize(it, state.screenHeight)
                }
                ScreenDimensionField("H", state.screenHeight, Modifier.width(94.dp)) {
                    state.setScreenSize(state.screenWidth, it)
                }
            }
        }
    }
}

@Composable
private fun ScreenDimensionField(label: String, value: Int, modifier: Modifier, onChange: (Int) -> Unit) {
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
