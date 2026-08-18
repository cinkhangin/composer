package composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import composer.pickImageFile
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import composer.model.ButtonVariant
import composer.model.ChipVariant
import composer.model.Node
import composer.model.navAction
import composer.model.TopAppBarVariant
import composer.model.TextFontFamily
import composer.model.TextWeight
import composer.ui.ColorField
import composer.ui.AppIconKind
import composer.ui.Field
import composer.ui.SymbolPickerField
import composer.ui.HDivider
import composer.ui.InspectorSection
import composer.ui.SectionHeader
import composer.ui.SquareIconButton
import composer.ui.Tk
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

internal enum class NavKind { None, Back, Navigate }
