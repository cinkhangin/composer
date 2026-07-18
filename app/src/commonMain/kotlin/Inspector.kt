package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import composer.LocalFonts
import composer.pickImageFile
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import kotlin.math.roundToInt
import composer.model.BoxAlignment
import composer.model.ButtonVariant
import composer.model.DesignTheme
import composer.model.HAlignment
import composer.model.HArrangement
import composer.model.ChipVariant
import composer.model.IconKind
import composer.model.ModifierSpec
import composer.model.NavAction
import composer.model.Node
import composer.model.navAction
import composer.model.withNavAction
import composer.model.TextAlignment
import composer.model.TopAppBarVariant
import composer.model.parentOf
import composer.model.withModifier
import composer.model.TextFontFamily
import composer.model.TextWeight
import composer.model.VAlignment
import composer.model.VArrangement
import composer.model.typeName
import composer.ui.AppIcon
import composer.ui.ColorField
import composer.ui.AppIconKind
import composer.ui.ComponentGlyph
import composer.ui.Field
import composer.ui.SymbolPickerField
import composer.ui.HDivider
import composer.ui.InspectorSection
import composer.ui.SectionHeader
import composer.ui.SquareIconButton
import composer.ui.SymbolIcon
import composer.ui.Tk
import composer.ui.TkMenu
import composer.ui.TkMenuItem
import composer.ui.ToolButton

/**
 * Inspector for the selected node: edits its properties (Text.text, Button.label)
 * and its full modifier chain (M5), plus arrange/delete actions. Everything flows
 * back into [EditorState], so the canvas and code panel update live.
 */
@Composable
fun Inspector(
    state: EditorState,
    modifier: Modifier = Modifier,
    onCollapse: (() -> Unit)? = null,
    lockComposableStructure: Boolean = false,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(46.dp).padding(start = 16.dp, end = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader("Inspector", icon = AppIconKind.Sliders, modifier = Modifier.weight(1f))
            onCollapse?.let { SquareIconButton(AppIconKind.CollapseRight, tip = "Hide inspector", onClick = it) }
        }
        HDivider()

        val selected = state.selected
        if (selected == null) {
            EmptyState()
            return@Column
        }

        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NodeBadge(
                state,
                selected,
                isRoot = selected.id == state.root.id,
                lockComposableStructure = lockComposableStructure,
            )

            if (selected is Node.Artboard) {
                BasicText(
                    "The artboard holds your screens and the design's Material theme.",
                    style = TextStyle(color = Tk.textMuted, fontSize = 12.sp),
                )
                HDivider()
                if (lockComposableStructure) {
                    BasicText(
                        "Theme declarations stay source-owned until Composer annotations define their identity.",
                        style = TextStyle(color = Tk.textMuted, fontSize = 12.sp),
                    )
                } else {
                    ThemeEditor(state)
                }
            } else if (selected is Node.Composable) {
                ComposableEditor(state, selected)
                if (lockComposableStructure) {
                    InspectorSection("Source function") {
                        BasicText(
                            "Body edits are synchronized. Creating, deleting, renaming, or changing function ownership waits for stable Composer annotations.",
                            style = TextStyle(color = Tk.textMuted, fontSize = 12.sp),
                        )
                    }
                } else {
                    InspectorSection("Component") {
                        if (state.isComponent(selected.id)) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                BasicText(
                                    "Reusable component \"${state.componentName(selected.id)}\" — insert instances from the palette; edits here apply to every instance.",
                                    style = TextStyle(color = Tk.textMuted, fontSize = 12.sp),
                                )
                                ToolButton("Remove from components") { state.removeComponent(selected.id) }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                BasicText(
                                    "Make this composable reusable — instances of it can be placed inside other composables.",
                                    style = TextStyle(color = Tk.textMuted, fontSize = 12.sp),
                                )
                                ToolButton("Create component") { state.createComponent(selected.id) }
                            }
                        }
                    }
                }
                InspectorSection("Arrange") {
                    Arrange(state, selected, lockComposableStructure)
                }
            } else if (selected is Node.Slot) {
                // A permanent Compose slot argument (e.g. Scaffold's topBar). It holds
                // content but has no properties of its own and can't be deleted/moved.
                BasicText(
                    "The ${selected.name} slot of its parent. Add components into it (palette or drag in the " +
                        "Layers tree); empty slots are omitted from the generated code. The slot itself can't be deleted.",
                    style = TextStyle(color = Tk.textMuted, fontSize = 12.sp),
                )
            } else if (selected is Node.SourceContainer) {
                BasicText(
                    "${selected.name} is a locked source-backed wrapper. Composer renders its supported children as a static template while preserving the wrapper's runtime Kotlin exactly.",
                    style = TextStyle(color = Tk.textMuted, fontSize = 12.sp),
                )
            } else {
              // Reusable components: instances show their link; mains show their
              // status; anything eligible offers "Create component".
              if (selected is Node.Instance) {
                InspectorSection("Component") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BasicText(
                            "Instance of \"${state.componentName(selected.refId)}\" — edits to that composable apply everywhere.",
                            style = TextStyle(color = Tk.textMuted, fontSize = 12.sp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ToolButton("Go to composable") { state.select(selected.refId) }
                            ToolButton("Detach") { state.detachInstance(selected.id) }
                        }
                    }
                }
              }
              if (selected.hasContentProps()) InspectorSection("Content") {
                when (selected) {
                    is Node.Text -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Field(
                            value = selected.text,
                            onValueChange = { v ->
                                state.update(selected.id, coalesceKey = "text:${selected.id}") {
                                    (it as Node.Text).copy(text = v, textExpression = "")
                                }
                            },
                            label = "Text",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumField("Size", selected.fontSize, Modifier.weight(1f), autoLabel = "Auto") { v ->
                                state.update(selected.id) { (it as Node.Text).copy(fontSize = v) }
                            }
                            NumField("Line height", selected.lineHeight, Modifier.weight(1f), autoLabel = "Auto") { v ->
                                state.update(selected.id) { (it as Node.Text).copy(lineHeight = v) }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EnumDropdown("Weight", selected.fontWeight, TextWeight.entries, Modifier.weight(1f)) { w ->
                                state.update(selected.id) { (it as Node.Text).copy(fontWeight = w) }
                            }
                            EnumDropdown("Font", selected.fontFamily, TextFontFamily.entries, Modifier.weight(1f)) { f ->
                                state.update(selected.id) { (it as Node.Text).copy(fontFamily = f) }
                            }
                        }
                        TextAlignField(selected.textAlign) { a ->
                            state.update(selected.id) { (it as Node.Text).copy(textAlign = a) }
                        }
                        DeviceFontPicker(selected.customFont) { name ->
                            state.update(selected.id) { (it as Node.Text).copy(customFont = name) }
                        }
                        BoolField("Custom color", selected.color != null) { on ->
                            state.update(selected.id) { (it as Node.Text).copy(color = if (on) (selected.color ?: 0xFF000000L) else null) }
                        }
                        selected.color?.let { c ->
                            ColorField(c) { nc -> state.update(selected.id, coalesceKey = "textcolor:${selected.id}") { (it as Node.Text).copy(color = nc) } }
                        }
                    }

                    is Node.Button -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        EnumDropdown("Style", selected.variant, ButtonVariant.entries) { v ->
                            state.update(selected.id) { (it as Node.Button).copy(variant = v) }
                        }
                        BasicText(
                            "A container — its caption is a Text child. Add Text/Icon inside via the palette or Layers tree.",
                            style = TextStyle(color = Tk.textMuted, fontSize = 11.sp),
                        )
                    }

                    is Node.Image -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val scope = rememberCoroutineScope()
                        val isLocal = selected.url.startsWith("data:")
                        Field(
                            value = if (isLocal) "" else selected.url,
                            onValueChange = { v -> state.update(selected.id, coalesceKey = "imgurl:${selected.id}") { (it as Node.Image).copy(url = v) } },
                            label = "Image URL",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val urlFailed = !isLocal && selected.url.isNotEmpty() && LocalImages.isFailed(selected.url)
                            BasicText(
                                when {
                                    isLocal -> "✓ local image selected"
                                    urlFailed -> "⚠ couldn't load this URL (blocked or not an image)"
                                    else -> "or pick a local file"
                                },
                                style = TextStyle(
                                    color = when {
                                        isLocal -> Tk.accent
                                        urlFailed -> Tk.danger
                                        else -> Tk.textMuted
                                    },
                                    fontSize = 12.sp,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            ToolButton("Pick file") { scope.launch { pickImageFile()?.let { du -> state.update(selected.id) { (it as Node.Image).copy(url = du) } } } }
                            if (selected.url.isNotEmpty()) ToolButton("Clear") { state.update(selected.id) { (it as Node.Image).copy(url = "") } }
                        }
                        Field(
                            value = selected.contentDescription,
                            onValueChange = { v -> state.update(selected.id, coalesceKey = "desc:${selected.id}") { (it as Node.Image).copy(contentDescription = v) } },
                            label = "Alt text",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    is Node.Icon -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (selected.sourceImageExpression.isNotEmpty()) {
                            BasicText(
                                "Source icon: ${selected.sourceImageExpression}",
                                style = TextStyle(color = Tk.textMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                            )
                        } else {
                            SymbolPickerField("Icon", selected.symbol, fallbackLabel = selected.icon.name) { name ->
                                state.update(selected.id) { (it as Node.Icon).copy(symbol = name) }
                            }
                        }
                        Field(
                            value = selected.contentDescription,
                            onValueChange = { v -> state.update(selected.id, coalesceKey = "desc:${selected.id}") { (it as Node.Icon).copy(contentDescription = v) } },
                            label = "Alt text",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    is Node.IconButton -> if (selected.contentExpression.isNotEmpty()) {
                        BasicText(
                            "Source-backed icon button content is preserved in Kotlin.",
                            style = TextStyle(color = Tk.textMuted, fontSize = 11.sp),
                        )
                    } else SymbolPickerField("Icon", selected.symbol, fallbackLabel = selected.icon.name) { name ->
                        state.update(selected.id) { (it as Node.IconButton).copy(symbol = name) }
                    }

                    is Node.TextField -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Field(
                            value = selected.value,
                            onValueChange = { v -> state.update(selected.id, coalesceKey = "tfval:${selected.id}") { (it as Node.TextField).copy(value = v) } },
                            label = "Value",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Field(
                            value = selected.placeholder,
                            onValueChange = { v -> state.update(selected.id, coalesceKey = "tfph:${selected.id}") { (it as Node.TextField).copy(placeholder = v) } },
                            label = "Label",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    is Node.Switch -> BoolField("On", selected.checked) { c ->
                        state.update(selected.id) { (it as Node.Switch).copy(checked = c) }
                    }

                    is Node.Checkbox -> BoolField("Checked", selected.checked) { c ->
                        state.update(selected.id) { (it as Node.Checkbox).copy(checked = c) }
                    }

                    is Node.RadioButton -> BoolField("Selected", selected.selected) { c ->
                        state.update(selected.id) { (it as Node.RadioButton).copy(selected = c) }
                    }

                    is Node.Slider -> SliderField(selected.value) { v ->
                        state.update(selected.id, coalesceKey = "slider:${selected.id}") { (it as Node.Slider).copy(value = v) }
                    }

                    // Column/Row/Box layout is edited in the dedicated "Layout" section below.
                    is Node.Column, is Node.Row, is Node.Box -> Unit

                    is Node.Scaffold -> BasicText(
                        "topBar, bottomBar & fab are permanent slots in the Layers tree — select one and add components into it.",
                        style = TextStyle(color = Tk.textMuted, fontSize = 11.sp),
                    )

                    is Node.TopAppBar -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        EnumDropdown("Style", selected.variant, TopAppBarVariant.entries) { v ->
                            state.update(selected.id) { (it as Node.TopAppBar).copy(variant = v) }
                        }
                        SlotRow("Navigation icon", selected.navigationIcon != null) { p -> state.setTopBarNavIcon(selected.id, p) }
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            BasicText(
                                "Actions (${selected.actions.size})",
                                style = TextStyle(color = Tk.textSecondary, fontSize = 13.sp),
                                modifier = Modifier.weight(1f),
                            )
                            ToolButton("Add action", primary = true) { state.insert { id -> Node.IconButton(id) } }
                        }
                        BasicText(
                            "Title & action items are child components — select them in the Layers tree to edit or remove.",
                            style = TextStyle(color = Tk.textMuted, fontSize = 11.sp),
                        )
                    }

                    // Opaque preserved code (IDE plugin) — read-only: edited in the
                    // code editor, never here.
                    is Node.RawCode -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BasicText(
                            "Code the designer can't edit — preserved verbatim. Edit it in the code editor.",
                            style = TextStyle(color = Tk.textMuted, fontSize = 11.sp),
                        )
                        BasicText(
                            selected.code.ifBlank { "(empty)" },
                            style = TextStyle(color = Tk.textSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                            maxLines = 12,
                        )
                    }

                    is Node.TabRow -> NumField("selected tab", selected.selectedIndex, Modifier.fillMaxWidth()) { v ->
                        state.update(selected.id) { n -> (n as Node.TabRow).copy(selectedIndex = v.coerceIn(0, (n.children.size - 1).coerceAtLeast(0))) }
                    }

                    is Node.Tab -> Field(
                        value = selected.label,
                        onValueChange = { v -> state.update(selected.id, coalesceKey = "tab:${selected.id}") { (it as Node.Tab).copy(label = v) } },
                        label = "Label",
                        modifier = Modifier.fillMaxWidth(),
                    )

                    is Node.NavigationBar -> NumField("selected item", selected.selectedIndex, Modifier.fillMaxWidth()) { v ->
                        state.update(selected.id) { n -> (n as Node.NavigationBar).copy(selectedIndex = v.coerceIn(0, (n.children.size - 1).coerceAtLeast(0))) }
                    }

                    is Node.NavItem -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Field(
                            value = selected.label,
                            onValueChange = { v -> state.update(selected.id, coalesceKey = "nav:${selected.id}") { (it as Node.NavItem).copy(label = v) } },
                            label = "Label",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SymbolPickerField("Icon", selected.symbol, fallbackLabel = "pick an icon") { name ->
                            state.update(selected.id) { (it as Node.NavItem).copy(symbol = name) }
                        }
                    }

                    is Node.Chip -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Field(
                            value = selected.label,
                            onValueChange = { v -> state.update(selected.id, coalesceKey = "chip:${selected.id}") { (it as Node.Chip).copy(label = v) } },
                            label = "Label",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        EnumDropdown("Style", selected.variant, ChipVariant.entries) { v ->
                            state.update(selected.id) { (it as Node.Chip).copy(variant = v) }
                        }
                        if (selected.variant == ChipVariant.Filter || selected.variant == ChipVariant.Input) {
                            BoolField("Selected", selected.selected) { v ->
                                state.update(selected.id) { (it as Node.Chip).copy(selected = v) }
                            }
                        }
                        SymbolPickerField("Leading icon", selected.symbol, fallbackLabel = "none") { name ->
                            state.update(selected.id) { (it as Node.Chip).copy(symbol = name) }
                        }
                    }

                    is Node.BadgedBox -> Field(
                        value = selected.badge,
                        onValueChange = { v -> state.update(selected.id, coalesceKey = "badge:${selected.id}") { (it as Node.BadgedBox).copy(badge = v) } },
                        label = "Badge text (empty = dot)",
                        modifier = Modifier.fillMaxWidth(),
                    )

                    is Node.Canvas -> BasicText(
                        "A drawing surface — add Line/Rect/Circle/Ellipse/Arc shapes from the palette. " +
                            "Shapes have no layout bounds; select them in the Layers tree. Size the canvas with a Size modifier.",
                        style = TextStyle(color = Tk.textMuted, fontSize = 12.sp),
                    )

                    is Node.Line -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumField("x1", selected.x1, Modifier.weight(1f), allowNegative = true) { v -> state.update(selected.id) { (it as Node.Line).copy(x1 = v) } }
                            NumField("y1", selected.y1, Modifier.weight(1f), allowNegative = true) { v -> state.update(selected.id) { (it as Node.Line).copy(y1 = v) } }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumField("x2", selected.x2, Modifier.weight(1f), allowNegative = true) { v -> state.update(selected.id) { (it as Node.Line).copy(x2 = v) } }
                            NumField("y2", selected.y2, Modifier.weight(1f), allowNegative = true) { v -> state.update(selected.id) { (it as Node.Line).copy(y2 = v) } }
                        }
                        ColorField(selected.color, showThemeSwatches = false) { c -> state.update(selected.id) { (it as Node.Line).copy(color = c) } }
                        NumField("stroke (dp)", selected.strokeWidth, Modifier.fillMaxWidth()) { v -> state.update(selected.id) { (it as Node.Line).copy(strokeWidth = v) } }
                    }

                    is Node.RectShape -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumField("x", selected.x, Modifier.weight(1f), allowNegative = true) { v -> state.update(selected.id) { (it as Node.RectShape).copy(x = v) } }
                            NumField("y", selected.y, Modifier.weight(1f), allowNegative = true) { v -> state.update(selected.id) { (it as Node.RectShape).copy(y = v) } }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumField("width", selected.width, Modifier.weight(1f)) { v -> state.update(selected.id) { (it as Node.RectShape).copy(width = v) } }
                            NumField("height", selected.height, Modifier.weight(1f)) { v -> state.update(selected.id) { (it as Node.RectShape).copy(height = v) } }
                        }
                        ColorField(selected.color, showThemeSwatches = false) { c -> state.update(selected.id) { (it as Node.RectShape).copy(color = c) } }
                        BoolField("Filled", selected.filled) { v -> state.update(selected.id) { (it as Node.RectShape).copy(filled = v) } }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumField("stroke", selected.strokeWidth, Modifier.weight(1f)) { v -> state.update(selected.id) { (it as Node.RectShape).copy(strokeWidth = v) } }
                            NumField("corner", selected.corner, Modifier.weight(1f)) { v -> state.update(selected.id) { (it as Node.RectShape).copy(corner = v) } }
                        }
                    }

                    is Node.CircleShape -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumField("center x", selected.cx, Modifier.weight(1f), allowNegative = true) { v -> state.update(selected.id) { (it as Node.CircleShape).copy(cx = v) } }
                            NumField("center y", selected.cy, Modifier.weight(1f), allowNegative = true) { v -> state.update(selected.id) { (it as Node.CircleShape).copy(cy = v) } }
                        }
                        NumField("radius", selected.radius, Modifier.fillMaxWidth()) { v -> state.update(selected.id) { (it as Node.CircleShape).copy(radius = v) } }
                        ColorField(selected.color, showThemeSwatches = false) { c -> state.update(selected.id) { (it as Node.CircleShape).copy(color = c) } }
                        BoolField("Filled", selected.filled) { v -> state.update(selected.id) { (it as Node.CircleShape).copy(filled = v) } }
                        if (!selected.filled) {
                            NumField("stroke (dp)", selected.strokeWidth, Modifier.fillMaxWidth()) { v -> state.update(selected.id) { (it as Node.CircleShape).copy(strokeWidth = v) } }
                        }
                    }

                    is Node.EllipseShape -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumField("x", selected.x, Modifier.weight(1f), allowNegative = true) { v -> state.update(selected.id) { (it as Node.EllipseShape).copy(x = v) } }
                            NumField("y", selected.y, Modifier.weight(1f), allowNegative = true) { v -> state.update(selected.id) { (it as Node.EllipseShape).copy(y = v) } }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumField("width", selected.width, Modifier.weight(1f)) { v -> state.update(selected.id) { (it as Node.EllipseShape).copy(width = v) } }
                            NumField("height", selected.height, Modifier.weight(1f)) { v -> state.update(selected.id) { (it as Node.EllipseShape).copy(height = v) } }
                        }
                        ColorField(selected.color, showThemeSwatches = false) { c -> state.update(selected.id) { (it as Node.EllipseShape).copy(color = c) } }
                        BoolField("Filled", selected.filled) { v -> state.update(selected.id) { (it as Node.EllipseShape).copy(filled = v) } }
                        if (!selected.filled) {
                            NumField("stroke (dp)", selected.strokeWidth, Modifier.fillMaxWidth()) { v -> state.update(selected.id) { (it as Node.EllipseShape).copy(strokeWidth = v) } }
                        }
                    }

                    is Node.ArcShape -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumField("x", selected.x, Modifier.weight(1f), allowNegative = true) { v -> state.update(selected.id) { (it as Node.ArcShape).copy(x = v) } }
                            NumField("y", selected.y, Modifier.weight(1f), allowNegative = true) { v -> state.update(selected.id) { (it as Node.ArcShape).copy(y = v) } }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumField("width", selected.width, Modifier.weight(1f)) { v -> state.update(selected.id) { (it as Node.ArcShape).copy(width = v) } }
                            NumField("height", selected.height, Modifier.weight(1f)) { v -> state.update(selected.id) { (it as Node.ArcShape).copy(height = v) } }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumField("start °", selected.startAngle, Modifier.weight(1f), allowNegative = true) { v -> state.update(selected.id) { (it as Node.ArcShape).copy(startAngle = v) } }
                            NumField("sweep °", selected.sweepAngle, Modifier.weight(1f), allowNegative = true) { v -> state.update(selected.id) { (it as Node.ArcShape).copy(sweepAngle = v) } }
                        }
                        ColorField(selected.color, showThemeSwatches = false) { c -> state.update(selected.id) { (it as Node.ArcShape).copy(color = c) } }
                        BoolField("Filled (pie)", selected.filled) { v -> state.update(selected.id) { (it as Node.ArcShape).copy(filled = v) } }
                        if (!selected.filled) {
                            NumField("stroke (dp)", selected.strokeWidth, Modifier.fillMaxWidth()) { v -> state.update(selected.id) { (it as Node.ArcShape).copy(strokeWidth = v) } }
                        }
                    }

                    is Node.Spacer, is Node.Divider, is Node.Card, is Node.Fab,
                    is Node.Composable, is Node.Artboard, is Node.Slot,
                    is Node.Dialog, is Node.BottomSheet, is Node.Instance,
                    is Node.CircularProgress, is Node.LinearProgress,
                    is Node.SourceContainer -> Unit
                }
              }
              // Navigation: what this component's onClick does in the generated app.
              selected.navAction()?.let { action ->
                InspectorSection("On click") {
                    NavActionEditor(state, selected, action)
                }
              }
              // Non-modifier composable parameters (arrangement/alignment/spacing) —
              // these are call-site arguments, not Modifier calls, so they sit in
              // their own section above the modifier chain.
              if (selected is Node.Column || selected is Node.Row || selected is Node.Box) {
                InspectorSection("Parameters") {
                    ParamsEditor(state, selected)
                }
              }
              // Modifier-first editing: the ordered chain is the primary surface,
              // mirroring how the generated Compose code reads.
              InspectorSection("Modifiers") {
                ModifierEditor(state, selected)
              }
              InspectorSection("Arrange") {
                Arrange(state, selected)
              }
            }
        }
    }
}

/**
 * Per-design Material theme editor (shown when the artboard is selected). A dark/light
 * base toggle plus a swatch grid of the editable [DesignTheme] color tokens; tapping a
 * swatch focuses it in the [ColorPicker] below. Edits flow to [EditorState.setTheme],
 * so the preview and generated `MaterialTheme { }` update live.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ThemeEditor(state: EditorState) {
    val theme = state.theme
    var active by remember { mutableStateOf(DesignTheme.TOKENS.first()) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Themes")
        // Named theme list: switch by clicking a chip; "+" duplicates the active
        // theme as a starting point. The active theme drives the preview and the
        // generated AppTheme default.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            state.themes.forEachIndexed { i, named ->
                ToolButton(named.name, primary = i == state.activeTheme) { state.setActiveTheme(i) }
            }
            ToolButton("", icon = AppIconKind.Plus) { state.addTheme() }
        }
        // Rename + delete for the active theme.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(
                value = state.themes.getOrNull(state.activeTheme)?.name ?: "",
                onValueChange = { state.renameTheme(state.activeTheme, it) },
                label = "Theme name (used in generated code)",
                modifier = Modifier.weight(1f),
            )
            if (state.themes.size > 1) {
                SquareIconButton(AppIconKind.Trash, danger = true, tip = "Delete theme") { state.deleteTheme(state.activeTheme) }
            }
        }
        BoolField("Dark base scheme", theme.dark) { state.setTheme(theme.copy(dark = it), coalesceKey = null) }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            for (token in DesignTheme.TOKENS) {
                // effective() shows what actually renders (dark builder defaults for
                // untouched tokens of a dark theme) — matches the canvas.
                ThemeSwatch(token, theme.effective(token), selected = token == active) { active = token }
            }
        }
        BasicText("Editing: $active", style = TextStyle(color = Tk.textMuted, fontSize = 11.sp))
        ColorField(theme.effective(active), showThemeSwatches = false) { c -> state.setTheme(theme.set(active, c), coalesceKey = "theme:$active") }
    }
}

@Composable
private fun ThemeSwatch(label: String, color: Long, selected: Boolean, onClick: () -> Unit) {
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

/** Whether a node has its own content properties (so a "Content" section is worth showing). */
private fun Node.hasContentProps(): Boolean = when (this) {
    is Node.Text, is Node.Button, is Node.Image, is Node.Icon, is Node.IconButton,
    is Node.TextField, is Node.Switch, is Node.Checkbox, is Node.RadioButton,
    is Node.Slider, is Node.Scaffold, is Node.TopAppBar, is Node.RawCode,
    is Node.TabRow, is Node.Tab, is Node.NavigationBar, is Node.NavItem,
    is Node.Chip, is Node.BadgedBox, is Node.Canvas,
    is Node.Line, is Node.RectShape, is Node.CircleShape,
    is Node.EllipseShape, is Node.ArcShape -> true
    else -> false
}

/**
 * Non-modifier composable parameters — call-site arguments like arrangement,
 * alignment, and spacing that aren't part of the Modifier chain. Shown above the
 * Modifiers section for Column/Row/Box.
 */
@Composable
private fun ParamsEditor(state: EditorState, node: Node) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (node) {
            is Node.Column -> {
                EnumDropdown("verticalArrangement", node.verticalArrangement, VArrangement.entries, itemLabel = { it.name }) { v ->
                    state.update(node.id) { (it as Node.Column).copy(verticalArrangement = v) }
                }
                EnumDropdown("horizontalAlignment", node.horizontalAlignment, HAlignment.entries, itemLabel = { it.name }) { v ->
                    state.update(node.id) { (it as Node.Column).copy(horizontalAlignment = v) }
                }
                NumField("spacing (Arrangement.spacedBy, dp)", node.spacing, autoLabel = "None") { v ->
                    state.update(node.id, coalesceKey = "spacing:${node.id}") { (it as Node.Column).copy(spacing = v) }
                }
            }
            is Node.Row -> {
                EnumDropdown("horizontalArrangement", node.horizontalArrangement, HArrangement.entries, itemLabel = { it.name }) { v ->
                    state.update(node.id) { (it as Node.Row).copy(horizontalArrangement = v) }
                }
                EnumDropdown("verticalAlignment", node.verticalAlignment, VAlignment.entries, itemLabel = { it.name }) { v ->
                    state.update(node.id) { (it as Node.Row).copy(verticalAlignment = v) }
                }
                NumField("spacing (Arrangement.spacedBy, dp)", node.spacing, autoLabel = "None") { v ->
                    state.update(node.id, coalesceKey = "spacing:${node.id}") { (it as Node.Row).copy(spacing = v) }
                }
            }
            is Node.Box -> {
                EnumDropdown("contentAlignment", node.contentAlignment, BoxAlignment.entries, itemLabel = { it.name }) { v ->
                    state.update(node.id) { (it as Node.Box).copy(contentAlignment = v) }
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun TextAlignField(value: TextAlignment, onChange: (TextAlignment) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        BasicText("Align", style = TextStyle(color = Tk.textMuted, fontSize = 11.sp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AlignButton("format_align_left", value == TextAlignment.Start) { onChange(TextAlignment.Start) }
            AlignButton("format_align_center", value == TextAlignment.Center) { onChange(TextAlignment.Center) }
            AlignButton("format_align_right", value == TextAlignment.End) { onChange(TextAlignment.End) }
            AlignButton("format_align_justify", value == TextAlignment.Justify) { onChange(TextAlignment.Justify) }
        }
    }
}

@Composable
private fun AlignButton(symbol: String, selected: Boolean, onClick: () -> Unit) {
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

/** Artboard position editor for a screen (a [Node.Composable]) + its function-name note. */
@Composable
private fun ComposableEditor(state: EditorState, screen: Node.Composable) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText(
            "One @Composable function. Its layer name becomes the generated function name.",
            style = TextStyle(color = Tk.textMuted, fontSize = 12.sp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            NumField("X", screen.x, Modifier.weight(1f), allowNegative = true) { state.setComposablePos(screen.id, it, screen.y) }
            NumField("Y", screen.y, Modifier.weight(1f), allowNegative = true) { state.setComposablePos(screen.id, screen.x, it) }
        }
        BasicText(
            "Screen size is shared by all composables and controlled from the designer toolbar.",
            style = TextStyle(color = Tk.textMuted, fontSize = 11.sp),
        )
    }
}

/**
 * Integer field that accepts free typing. A valid number commits; an invalid one
 * (including empty) shows red and leaves the stored value untouched. While focused the
 * user owns the text — it is never overwritten, even if [onChange] clamps the value
 * (e.g. frame size). On blur (or an external change) it re-syncs to the stored value.
 *
 * [autoLabel] handles the model's "0 = inherit/auto" convention like Figma: a zero
 * value shows as an empty field with the label (e.g. "Auto") as placeholder, and
 * clearing the field commits 0 (back to auto) instead of erroring.
 */
@Composable
private fun NumField(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    allowNegative: Boolean = false,
    autoLabel: String? = null,
    onChange: (Int) -> Unit,
) {
    fun show(v: Int) = if (autoLabel != null && v == 0) "" else v.toString()
    var text by remember { mutableStateOf(show(value)) }
    var focused by remember { mutableStateOf(false) }
    if (!focused && text != show(value)) text = show(value)
    fun parse(t: String): Int? = when {
        t.isEmpty() && autoLabel != null -> 0
        else -> t.toIntOrNull()?.let { if (allowNegative || it >= 0) it else null }
    }
    Field(
        value = text,
        isError = parse(text) == null,
        onValueChange = { v -> text = v; parse(v)?.let(onChange) },
        onFocusChange = { f -> focused = f; if (!f) text = show(value) },
        label = label,
        placeholder = autoLabel,
        modifier = modifier,
    )
}

/** Pick a device-installed font (Local Font Access API; Chromium only). Overrides fontFamily. */
@Composable
private fun DeviceFontPicker(current: String, onPick: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        BasicText("Device font (overrides Font)", style = TextStyle(color = Tk.textMuted, fontSize = 11.sp))
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Tk.rSm))
                    .background(Tk.panelAlt)
                    .border(1.dp, Tk.border, RoundedCornerShape(Tk.rSm))
                    .clickable(enabled = LocalFonts.supported) {
                        busy = true
                        scope.launch { LocalFonts.query(); busy = false; open = LocalFonts.available.isNotEmpty() }
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val label = when {
                    !LocalFonts.supported -> "Installed fonts unavailable"
                    busy -> "Loading…"
                    current.isNotEmpty() -> current
                    else -> "Browse installed fonts…"
                }
                BasicText(
                    label,
                    style = TextStyle(color = if (current.isNotEmpty()) Tk.textPrimary else Tk.textMuted, fontSize = 13.sp),
                    modifier = Modifier.weight(1f),
                )
                if (current.isNotEmpty()) {
                    AppIcon(AppIconKind.Close, Modifier.size(12.dp).clickable { onPick("") }, tint = Tk.textMuted)
                }
            }
            TkMenu(expanded = open, onDismissRequest = { open = false }) {
                // Lazy + height-capped: hundreds of installed fonts would otherwise
                // make the menu screen-tall AND eagerly load every font's bytes.
                // Only composed (≈visible) rows load their font, so each name renders
                // in its own typeface as you scroll (default font until loaded).
                // FIXED size (not heightIn): DropdownMenu measures its content with
                // IntrinsicSize, and LazyColumn (SubcomposeLayout) can't answer
                // intrinsics — an explicit size modifier answers for it.
                val menuHeight = (LocalFonts.available.size * 31).coerceAtMost(320).dp
                LazyColumn(Modifier.width(260.dp).height(menuHeight)) {
                    items(LocalFonts.available, key = { it }) { f ->
                        LaunchedEffect(f) { LocalFonts.load(f) }
                        TkMenuItem(f, selected = f == current, fontFamily = LocalFonts.loaded[f]) {
                            onPick(f); open = false
                        }
                    }
                }
            }
        }
    }
}

private enum class NavKind { None, Back, Navigate }

/**
 * Edits a clickable node's [NavAction]: None / Back / Navigate + target screen.
 * Targets come from the artboard's screens (excluding the node's own screen);
 * the action stores the target's node id — codegen resolves the name at emit.
 */
@Composable
private fun NavActionEditor(state: EditorState, selected: Node, action: NavAction) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val kind = when (action) {
            NavAction.None -> NavKind.None
            NavAction.Back -> NavKind.Back
            is NavAction.Navigate -> NavKind.Navigate
        }
        val ownScreen = state.screenOf(selected.id)?.id
        val targets = state.composables.filter { it.id != ownScreen }
        EnumDropdown("action", kind, NavKind.entries) { k ->
            val newAction = when (k) {
                NavKind.None -> NavAction.None
                NavKind.Back -> NavAction.Back
                NavKind.Navigate -> NavAction.Navigate(
                    (action as? NavAction.Navigate)?.screenId ?: targets.firstOrNull()?.id ?: "",
                )
            }
            state.update(selected.id, coalesceKey = "nav:${selected.id}") { it.withNavAction(newAction) }
        }
        if (action is NavAction.Navigate) {
            if (targets.isEmpty()) {
                BasicText(
                    "Add another screen to navigate to.",
                    style = TextStyle(color = Tk.textMuted, fontSize = 11.sp),
                )
            } else {
                val current = targets.firstOrNull { it.id == action.screenId }
                EnumDropdown(
                    "to screen",
                    current ?: targets.first(),
                    targets,
                    itemLabel = { state.layerName(it.id) ?: "Composable" },
                ) { t ->
                    state.update(selected.id, coalesceKey = "nav:${selected.id}") { it.withNavAction(NavAction.Navigate(t.id)) }
                }
                if (current == null) {
                    BasicText(
                        "The target screen no longer exists — the action is ignored until you pick one.",
                        style = TextStyle(color = Tk.textMuted, fontSize = 11.sp),
                    )
                }
            }
        }
    }
}

@Composable
fun <T> EnumDropdown(label: String, value: T, options: List<T>, modifier: Modifier = Modifier, itemLabel: (T) -> String = { Vocab.label(it) }, onChange: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        BasicText(label, style = TextStyle(color = Tk.textMuted, fontSize = 11.sp))
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Tk.rSm))
                    .background(if (hovered || open) Tk.elevated else Tk.panelAlt)
                    .border(1.dp, if (open) Tk.accent else if (hovered) Tk.borderStrong else Tk.border, RoundedCornerShape(Tk.rSm))
                    .hoverable(interaction)
                    .clickable(interactionSource = interaction, indication = null) { open = true }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(itemLabel(value), style = TextStyle(color = Tk.textPrimary, fontSize = 13.sp), modifier = Modifier.weight(1f))
                AppIcon(AppIconKind.ChevronDown, Modifier.size(12.dp), tint = if (open) Tk.accent else Tk.textMuted)
            }
            TkMenu(expanded = open, onDismissRequest = { open = false }) {
                for (option in options) {
                    TkMenuItem(itemLabel(option), selected = option == value) { onChange(option); open = false }
                }
            }
        }
    }
}

@Composable
private fun SlotRow(label: String, present: Boolean, onSet: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, style = TextStyle(color = Tk.textSecondary, fontSize = 13.sp), modifier = Modifier.weight(1f))
        if (present) ToolButton("Remove") { onSet(false) } else ToolButton("Add", primary = true) { onSet(true) }
    }
}

@Composable
private fun BoolField(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, style = TextStyle(color = Tk.textSecondary, fontSize = 13.sp), modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderField(value: Float, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BasicText("value: ${(value * 100).roundToInt()}%", style = TextStyle(color = Tk.textSecondary, fontSize = 13.sp))
        Slider(value = value, onValueChange = onChange)
    }
}

@Composable
private fun NodeBadge(state: EditorState, node: Node, isRoot: Boolean, lockComposableStructure: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(Tk.rSm)).background(Tk.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            ComponentGlyph(node.typeName(), Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            if (isRoot) {
                BasicText(
                    state.layerName(node.id) ?: "Artboard",
                    style = TextStyle(color = Tk.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                )
                BasicText(
                    if (state.layerName(node.id) != null) "Application" else "Design root",
                    style = TextStyle(color = Tk.textMuted, fontSize = 11.sp),
                )
            } else if (lockComposableStructure && node is Node.Composable) {
                BasicText(
                    state.layerName(node.id) ?: node.typeName(),
                    style = TextStyle(color = Tk.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                )
                BasicText("Source composable", style = TextStyle(color = Tk.textMuted, fontSize = 11.sp))
            } else {
                BasicTextField(
                    value = state.layerName(node.id) ?: node.typeName(),
                    onValueChange = { state.renameLayer(node.id, it) },
                    singleLine = true,
                    textStyle = TextStyle(color = Tk.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    cursorBrush = SolidColor(Tk.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
                BasicText("${node.typeName()} · rename", style = TextStyle(color = Tk.textMuted, fontSize = 11.sp))
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        BasicText(
            "Select a node on the canvas\nto edit its properties.",
            style = TextStyle(color = Tk.textMuted, fontSize = 13.sp),
        )
    }
}

@Composable
private fun Arrange(state: EditorState, selected: Node, lockComposableStructure: Boolean = false) {
    val isRoot = selected.id == state.root.id
    val lockedFunction = lockComposableStructure && selected is Node.Composable
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SquareIconButton(AppIconKind.ArrowUp, enabled = !lockedFunction && state.canMove(selected.id, -1), tip = "Move up") { state.move(selected.id, -1) }
            SquareIconButton(AppIconKind.ArrowDown, enabled = !lockedFunction && state.canMove(selected.id, +1), tip = "Move down") { state.move(selected.id, +1) }
            Box(Modifier.weight(1f))
            SquareIconButton(AppIconKind.Duplicate, enabled = !lockedFunction, tip = "Duplicate (⌘D)") { state.duplicate() }
            SquareIconButton(AppIconKind.Trash, enabled = !isRoot && !lockedFunction, danger = true, tip = "Delete") { state.delete(selected.id) }
        }
    }
}
