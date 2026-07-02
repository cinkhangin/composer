package composer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A floating rounded "island" panel — the core of the island-style layout. Panes
 * are separate cards on the app background, separated by [Tk.gap], not dividers.
 */
@Composable
fun Island(
    modifier: Modifier = Modifier,
    color: Color = Tk.panel,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Tk.rLg)
    Column(
        modifier = modifier
            .clip(shape)
            .background(color)
            .border(1.dp, Tk.border, shape),
        content = content,
    )
}

/** Pill button used across the toolbar. `primary` = filled accent; otherwise a ghost button. */
@Composable
fun ToolButton(
    label: String,
    enabled: Boolean = true,
    primary: Boolean = false,
    icon: AppIconKind? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val bg = when {
        !enabled -> Color.Transparent
        primary -> if (hovered) Tk.accentHover else Tk.accent
        hovered -> Tk.elevated
        else -> Tk.panelAlt
    }
    val fg = when {
        !enabled -> Tk.textMuted
        primary -> Color.White
        hovered -> Tk.textPrimary
        else -> Tk.textSecondary
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Tk.rSm))
            .background(bg)
            .then(if (!primary) Modifier.border(1.dp, if (hovered && enabled) Tk.borderStrong else Tk.border, RoundedCornerShape(Tk.rSm)) else Modifier)
            .heightIn(min = 32.dp)
            .hoverable(interaction, enabled)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) { onClick() }
            .padding(horizontal = if (label.isEmpty()) 8.dp else 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) AppIcon(icon, Modifier.size(14.dp), tint = fg)
            if (label.isNotEmpty()) Text(
                label,
                color = fg,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                // Trim the line-box padding and center the glyphs so the label sits level
                // with the icon (Compose Text otherwise renders slightly low).
                style = LocalTextStyle.current.copy(
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
                ),
            )
        }
    }
}

/** Small square icon button for inline actions (move, delete, etc.). */
@Composable
fun SquareIconButton(
    icon: AppIconKind,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fg = when {
        !enabled -> Tk.textMuted
        danger -> Tk.danger
        hovered -> Tk.textPrimary
        else -> Tk.textSecondary
    }
    val bg = when {
        !enabled -> Color.Transparent
        hovered && danger -> Tk.dangerSoft
        hovered -> Tk.elevated
        else -> Tk.panelAlt
    }
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(Tk.rXs))
            .background(bg)
            .border(1.dp, if (hovered && enabled) Tk.borderStrong else Tk.border, RoundedCornerShape(Tk.rXs))
            .hoverable(interaction, enabled)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(icon, Modifier.size(15.dp), tint = fg)
    }
}

/** Uppercase section label used to head a group of controls, with an optional leading icon. */
@Composable
fun SectionHeader(text: String, icon: AppIconKind? = null, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        if (icon != null) AppIcon(icon, Modifier.size(13.dp), tint = Tk.textMuted)
        Text(
            text = text.uppercase(),
            color = Tk.textMuted,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
        )
    }
}

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

@Composable
fun VDivider() {
    Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 10.dp).background(Tk.border))
}

@Composable
fun HDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Tk.border))
}
