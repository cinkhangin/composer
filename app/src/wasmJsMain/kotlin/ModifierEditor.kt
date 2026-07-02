package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import composer.model.CornerUnit
import composer.model.GradientDirection
import composer.model.ModifierSpec
import composer.model.PaddingMode
import composer.model.ModifierSpec.AspectRatio
import composer.model.ModifierSpec.Clip
import composer.model.ModifierSpec.Background
import composer.model.ModifierSpec.FillMaxHeight
import composer.model.ModifierSpec.FillMaxSize
import composer.model.ModifierSpec.FillMaxWidth
import composer.model.ModifierSpec.Offset
import composer.model.ModifierSpec.Padding
import composer.model.ModifierSpec.Size
import composer.model.ModifierSpec.Weight
import composer.model.Node
import composer.model.parentOf
import composer.model.withModifier
import composer.ui.AppIcon
import composer.ui.AppIconKind
import composer.ui.ColorField
import composer.ui.resolvePickerColor
import composer.ui.Field
import composer.ui.SquareIconButton
import composer.ui.Tk

/**
 * Visual editor for the selected node's modifier chain. Rows are **drag-reorderable**
 * (grab the ⠿ handle) and you can **insert between** any two rows via the "+" gaps.
 * Order is significant — it maps straight to the generated `Modifier.a().b()` chain
 * and the rendered result. All edits flow through [EditorState] for live preview.
 */
@Composable
fun ModifierEditor(state: EditorState, selected: Node) {
    val mods = selected.modifier
    var insertAt by remember(selected.id) { mutableStateOf<Int?>(null) }
    val dnd = remember(selected.id) { ModifierDndState() }

    // weight() only compiles inside a Row/Column, so only offer it there.
    val parent = state.root.parentOf(selected.id)
    val canWeight = parent is Node.Row || parent is Node.Column

    fun apply(next: List<ModifierSpec>, coalesceKey: String? = null) =
        state.update(selected.id, coalesceKey) { it.withModifier(next) }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (mods.isEmpty()) {
            BasicText("Nothing added yet — use + to add size, padding, fill or corners.", style = TextStyle(color = Tk.textMuted, fontSize = 12.sp), modifier = Modifier.padding(vertical = 4.dp))
        }

        for (i in mods.indices) {
            InsertGap(i, insertAt, dnd, mods, canWeight, onToggle = { insertAt = if (insertAt == i) null else i }) { spec ->
                apply(mods.insertedAt(i, spec)); insertAt = null
            }
            ModifierCard(
                index = i,
                spec = mods[i],
                dnd = dnd,
                onRemove = { apply(mods.without(i)); insertAt = null },
                onChange = { ns -> apply(mods.replaced(i, ns), coalesceKey = "mod:${selected.id}:$i") },
                onReorder = { from, to -> apply(mods.movedTo(from, to)) },
            )
        }
        InsertGap(mods.size, insertAt, dnd, mods, canWeight, onToggle = { insertAt = if (insertAt == mods.size) null else mods.size }) { spec ->
            apply(mods.insertedAt(mods.size, spec)); insertAt = null
        }
    }
}

@Composable
private fun ModifierCard(
    index: Int,
    spec: ModifierSpec,
    dnd: ModifierDndState,
    onRemove: () -> Unit,
    onChange: (ModifierSpec) -> Unit,
    onReorder: (Int, Int) -> Unit,
) {
    val isDragging = dnd.draggingIndex == index
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { c -> dnd.bounds[index] = RowSpan(c.positionInWindow().y, c.size.height.toFloat()) }
            .alpha(if (isDragging) 0.4f else 1f)
            .clip(RoundedCornerShape(Tk.rSm))
            .background(Tk.panelAlt)
            .border(1.dp, Tk.border, RoundedCornerShape(Tk.rSm))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            DragHandle(index, dnd, onReorder)
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(Tk.accent))
            BasicText(
                specName(spec),
                style = TextStyle(color = Tk.textPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            SquareIconButton(AppIconKind.Trash, danger = true, onClick = onRemove)
        }
        ModifierParams(spec, onChange)
    }
}

@Composable
private fun DragHandle(index: Int, dnd: ModifierDndState, onReorder: (Int, Int) -> Unit) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .onGloballyPositioned { c -> dnd.gripTops[index] = c.positionInWindow().y }
            .pointerInput(index) {
                detectDragGestures(
                    onDragStart = { dnd.start(index) },
                    onDrag = { change, _ ->
                        change.consume()
                        dnd.update((dnd.gripTops[index] ?: 0f) + change.position.y)
                    },
                    onDragEnd = { dnd.end(onReorder) },
                    onDragCancel = { dnd.cancel() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(AppIconKind.Grip, Modifier.size(14.dp), tint = Tk.textMuted)
    }
}

/** The "+" affordance between rows; doubles as the drop indicator during a drag. */
@Composable
private fun InsertGap(
    index: Int,
    insertAt: Int?,
    dnd: ModifierDndState,
    existing: List<ModifierSpec>,
    canWeight: Boolean,
    onToggle: () -> Unit,
    onPick: (ModifierSpec) -> Unit,
) {
    if (dnd.draggingIndex != null) {
        if (dnd.dropIndex == index) {
            Box(Modifier.fillMaxWidth().height(3.dp).padding(horizontal = 4.dp).background(Tk.accent))
        } else {
            Spacer(Modifier.height(6.dp))
        }
        return
    }

    val active = insertAt == index
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lit = hovered || active

    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null) { onToggle() },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(if (lit) Tk.accentSoft else Color.Transparent))
            Box(
                Modifier.size(16.dp).clip(CircleShape).background(if (lit) Tk.accent else Tk.panelAlt).border(1.dp, if (lit) Tk.accent else Tk.border, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(if (active) AppIconKind.Close else AppIconKind.Plus, Modifier.size(10.dp), tint = if (lit) Color.White else Tk.textMuted)
            }
        }
        if (active) {
            AddChipsRow(existing, canWeight, onPick)
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AddChipsRow(existing: List<ModifierSpec>, canWeight: Boolean, onAdd: (ModifierSpec) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        AddChip("padding") { onAdd(Padding(8)) }
        AddChip("size") { onAdd(Size(100, 40)) }
        AddChip("width") { onAdd(ModifierSpec.Width(100)) }
        AddChip("height") { onAdd(ModifierSpec.Height(48)) }
        AddChip("offset") { onAdd(Offset(0, 0)) }
        AddChip("background") { onAdd(Background(0xFF2196F3)) }
        if (canWeight && existing.none { it is Weight }) AddChip("weight") { onAdd(Weight(1f)) }
        if (existing.none { it is AspectRatio }) AddChip("aspectRatio") { onAdd(AspectRatio(1, 1)) }
        if (existing.none { it is Clip }) AddChip("clip") { onAdd(Clip(12)) }
        if (existing.none { it is ModifierSpec.Alpha }) AddChip("opacity") { onAdd(ModifierSpec.Alpha(0.5f)) }
        if (existing.none { it is ModifierSpec.Border }) AddChip("border") { onAdd(ModifierSpec.Border(1, 0xFF000000)) }
        if (existing.none { it is ModifierSpec.DropShadow }) AddChip("dropShadow") { onAdd(ModifierSpec.DropShadow()) }
        if (existing.none { it is ModifierSpec.InnerShadow }) AddChip("innerShadow") { onAdd(ModifierSpec.InnerShadow()) }
        if (FillMaxWidth !in existing) AddChip("fillW") { onAdd(FillMaxWidth) }
        if (FillMaxHeight !in existing) AddChip("fillH") { onAdd(FillMaxHeight) }
        if (FillMaxSize !in existing) AddChip("fillSize") { onAdd(FillMaxSize) }
    }
}

@Composable
private fun AddChip(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Tk.rXs))
            .background(if (hovered) Tk.elevated else Color.Transparent)
            .border(1.dp, if (hovered) Tk.borderStrong else Tk.border, RoundedCornerShape(Tk.rXs))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText("+ $label", style = TextStyle(color = if (hovered) Tk.textPrimary else Tk.textSecondary, fontSize = 12.sp))
    }
}

/**
 * Multi-stop gradient editor: a row of stop swatches (click to select, ✕ on the
 * selected one when 3+ stops, + appends a copy of the last stop) above ONE
 * [ColorPicker] editing the selected stop — stacking N full pickers would not fit.
 */
@Composable
private fun GradientStopsEditor(colors: List<Long>, onChange: (List<Long>) -> Unit) {
    var selected by remember { mutableStateOf(0) }
    val idx = selected.coerceIn(0, colors.lastIndex)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            colors.forEachIndexed { i, c ->
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(resolvePickerColor(c)))
                        .border(if (i == idx) 2.dp else 1.dp, if (i == idx) Tk.accent else Tk.border, RoundedCornerShape(6.dp))
                        .clickable { selected = i },
                )
            }
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Tk.panelAlt)
                    .border(1.dp, Tk.border, RoundedCornerShape(6.dp))
                    .clickable {
                        onChange(colors + colors.last())
                        selected = colors.size
                    },
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(AppIconKind.Plus, Modifier.size(12.dp), tint = Tk.textSecondary)
            }
            if (colors.size > 2) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Tk.panelAlt)
                        .border(1.dp, Tk.border, RoundedCornerShape(6.dp))
                        .clickable {
                            onChange(colors.toMutableList().apply { removeAt(idx) })
                            selected = (idx - 1).coerceAtLeast(0)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(AppIconKind.Close, Modifier.size(12.dp), tint = Tk.textSecondary)
                }
            }
        }
        ColorField(colors[idx]) { new ->
            onChange(colors.toMutableList().apply { set(idx, new) })
        }
    }
}

/**
 * Corner radius field with a dp / % unit toggle. Percent is Compose's
 * `RoundedCornerShape(percent)` — relative to the smaller side, clamped to 50
 * (= pill/circle), so it survives any resize.
 */
@Composable
private fun CornerField(corner: Int, unit: CornerUnit, onChange: (Int, CornerUnit) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        val label = if (unit == CornerUnit.Percent) "corner radius (%)" else "corner radius (dp)"
        IntField(label, corner, Modifier.weight(1f)) { c ->
            onChange(if (unit == CornerUnit.Percent) c.coerceAtMost(50) else c, unit)
        }
        SegRow(listOf(CornerUnit.Dp to "dp", CornerUnit.Percent to "%"), unit) { u ->
            onChange(if (u == CornerUnit.Percent) corner.coerceAtMost(50) else corner, u)
        }
    }
}

/** Small segmented toggle (the PaddingModeRow look, generic). */
@Composable
private fun <T> SegRow(options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((value, label) in options) {
            val sel = value == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Tk.rXs))
                    .background(if (sel) Tk.accent else Tk.panelAlt)
                    .border(1.dp, if (sel) Tk.accent else Tk.border, RoundedCornerShape(Tk.rXs))
                    .clickable { onPick(value) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(label, style = TextStyle(color = if (sel) Color.White else Tk.textSecondary, fontSize = 11.sp))
            }
        }
    }
}

@Composable
private fun PaddingModeRow(mode: PaddingMode, onPick: (PaddingMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (m in PaddingMode.entries) {
            val sel = m == mode
            val label = when (m) {
                PaddingMode.All -> "all"
                PaddingMode.Symmetric -> "H,V"
                PaddingMode.Sides -> "sides"
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Tk.rXs))
                    .background(if (sel) Tk.accent else Tk.panelAlt)
                    .border(1.dp, if (sel) Tk.accent else Tk.border, RoundedCornerShape(Tk.rXs))
                    .clickable { onPick(m) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(label, style = TextStyle(color = if (sel) Color.White else Tk.textSecondary, fontSize = 11.sp))
            }
        }
    }
}

@Composable
private fun ModifierParams(spec: ModifierSpec, onChange: (ModifierSpec) -> Unit) {
    when (spec) {
        is Padding -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PaddingModeRow(spec.mode) { m -> onChange(spec.copy(mode = m)) }
            when (spec.mode) {
                PaddingMode.All -> IntField("all (dp)", spec.all, Modifier.fillMaxWidth()) { onChange(spec.copy(all = it)) }
                PaddingMode.Symmetric -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IntField("horizontal", spec.horizontal, Modifier.weight(1f)) { onChange(spec.copy(horizontal = it)) }
                    IntField("vertical", spec.vertical, Modifier.weight(1f)) { onChange(spec.copy(vertical = it)) }
                }
                PaddingMode.Sides -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IntField("start", spec.start, Modifier.weight(1f)) { onChange(spec.copy(start = it)) }
                    IntField("top", spec.top, Modifier.weight(1f)) { onChange(spec.copy(top = it)) }
                    IntField("end", spec.end, Modifier.weight(1f)) { onChange(spec.copy(end = it)) }
                    IntField("bottom", spec.bottom, Modifier.weight(1f)) { onChange(spec.copy(bottom = it)) }
                }
            }
        }

        is Size -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IntField("width", spec.width, Modifier.weight(1f)) { onChange(Size(it, spec.height)) }
            IntField("height", spec.height, Modifier.weight(1f)) { onChange(Size(spec.width, it)) }
        }

        is ModifierSpec.Width -> IntField("width", spec.width, Modifier.fillMaxWidth()) { onChange(ModifierSpec.Width(it)) }

        is ModifierSpec.Height -> IntField("height", spec.height, Modifier.fillMaxWidth()) { onChange(ModifierSpec.Height(it)) }

        is Offset -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IntField("x", spec.x, Modifier.weight(1f), allowNegative = true) { onChange(Offset(it, spec.y)) }
            IntField("y", spec.y, Modifier.weight(1f), allowNegative = true) { onChange(Offset(spec.x, it)) }
        }

        is Weight -> FloatField("weight", spec.value, Modifier.fillMaxWidth()) { onChange(Weight(it)) }

        is Clip -> CornerField(spec.corner, spec.cornerUnit) { c, u -> onChange(Clip(c, u)) }

        is ModifierSpec.Alpha -> FloatField("opacity (0–1)", spec.value, Modifier.fillMaxWidth()) { onChange(ModifierSpec.Alpha(it.coerceIn(0f, 1f))) }

        is ModifierSpec.Border -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IntField("width (dp)", spec.width, Modifier.fillMaxWidth()) { onChange(spec.copy(width = it)) }
            ColorField(spec.color) { onChange(spec.copy(color = it)) }
            CornerField(spec.corner, spec.cornerUnit) { c, u -> onChange(spec.copy(corner = c, cornerUnit = u)) }
        }

        is AspectRatio -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            IntField("width", spec.width, Modifier.weight(1f)) { onChange(AspectRatio(it, spec.height)) }
            BasicText(":", style = TextStyle(color = Tk.textMuted, fontSize = 14.sp), modifier = Modifier.padding(bottom = 9.dp))
            IntField("height", spec.height, Modifier.weight(1f)) { onChange(AspectRatio(spec.width, it)) }
        }

        is Background -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val gradient = spec.gradientStops().isNotEmpty()
            SegRow(listOf(false to "solid", true to "gradient"), gradient) { grad ->
                // Toggling on seeds two stops from the solid color (no visual jump);
                // toggling off keeps the solid color and drops the stop list.
                onChange(spec.copy(colors = if (grad) listOf(spec.color, spec.color) else emptyList()))
            }
            if (gradient) {
                GradientStopsEditor(spec.colors) { onChange(spec.copy(colors = it)) }
                SegRow(
                    GradientDirection.entries.map { it to it.name.lowercase() },
                    spec.direction,
                ) { onChange(spec.copy(direction = it)) }
            } else {
                ColorField(spec.color) { onChange(spec.copy(color = it)) }
            }
            CornerField(spec.corner, spec.cornerUnit) { c, u -> onChange(spec.copy(corner = c, cornerUnit = u)) }
        }

        is ModifierSpec.DropShadow -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorField(spec.color) { onChange(spec.copy(color = it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IntField("blur (dp)", spec.radius, Modifier.weight(1f)) { onChange(spec.copy(radius = it)) }
                IntField("spread", spec.spread, Modifier.weight(1f), allowNegative = true) { onChange(spec.copy(spread = it)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IntField("x", spec.offsetX, Modifier.weight(1f), allowNegative = true) { onChange(spec.copy(offsetX = it)) }
                IntField("y", spec.offsetY, Modifier.weight(1f), allowNegative = true) { onChange(spec.copy(offsetY = it)) }
            }
            CornerField(spec.corner, spec.cornerUnit) { c, u -> onChange(spec.copy(corner = c, cornerUnit = u)) }
        }

        is ModifierSpec.InnerShadow -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorField(spec.color) { onChange(spec.copy(color = it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IntField("blur (dp)", spec.radius, Modifier.weight(1f)) { onChange(spec.copy(radius = it)) }
                IntField("spread", spec.spread, Modifier.weight(1f), allowNegative = true) { onChange(spec.copy(spread = it)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IntField("x", spec.offsetX, Modifier.weight(1f), allowNegative = true) { onChange(spec.copy(offsetX = it)) }
                IntField("y", spec.offsetY, Modifier.weight(1f), allowNegative = true) { onChange(spec.copy(offsetY = it)) }
            }
            CornerField(spec.corner, spec.cornerUnit) { c, u -> onChange(spec.copy(corner = c, cornerUnit = u)) }
        }

        FillMaxWidth, FillMaxHeight, FillMaxSize -> Unit
    }
}

@Composable
private fun FloatField(label: String, value: Float, modifier: Modifier = Modifier, onChange: (Float) -> Unit) {
    NumberFieldImpl(label, value.toString(), modifier, parse = { it.toFloatOrNull() }, onChange = onChange)
}

@Composable
private fun IntField(label: String, value: Int, modifier: Modifier = Modifier, allowNegative: Boolean = false, onChange: (Int) -> Unit) {
    NumberFieldImpl(
        label, value.toString(), modifier,
        parse = { it.toIntOrNull()?.takeIf { n -> allowNegative || n >= 0 } },
        onChange = onChange,
    )
}

/**
 * Numeric field that accepts any text. A valid parse commits; invalid input (including
 * empty) shows red and leaves the stored value untouched (so it reverts to the previous
 * value). While focused the user owns the text — it is never overwritten mid-edit; it
 * re-syncs to [valueText] on blur or an external change.
 */
@Composable
private fun <T> NumberFieldImpl(
    label: String,
    valueText: String,
    modifier: Modifier,
    parse: (String) -> T?,
    onChange: (T) -> Unit,
) {
    var text by remember { mutableStateOf(valueText) }
    var focused by remember { mutableStateOf(false) }
    if (!focused && text != valueText) text = valueText
    Field(
        value = text,
        isError = parse(text) == null,
        onValueChange = { v -> text = v; parse(v)?.let(onChange) },
        onFocusChange = { f -> focused = f; if (!f) text = valueText },
        label = label,
        modifier = modifier,
    )
}

// --- drag & drop state -----------------------------------------------------

private data class RowSpan(val top: Float, val height: Float)

private class ModifierDndState {
    var draggingIndex by mutableStateOf<Int?>(null)
    var dropIndex by mutableStateOf<Int?>(null) // insertion gap index, 0..size
    val bounds = mutableStateMapOf<Int, RowSpan>()
    val gripTops = mutableStateMapOf<Int, Float>()

    fun start(index: Int) {
        draggingIndex = index
        dropIndex = null
    }

    fun update(windowY: Float) {
        if (draggingIndex == null) return
        val entry = bounds.entries.firstOrNull { (_, b) -> windowY >= b.top && windowY < b.top + b.height } ?: return
        val (i, b) = entry
        val frac = (windowY - b.top) / b.height
        dropIndex = if (frac < 0.5f) i else i + 1
    }

    fun end(reorder: (Int, Int) -> Unit) {
        val from = draggingIndex
        val gap = dropIndex
        if (from != null && gap != null) {
            val to = if (gap > from) gap - 1 else gap
            if (to != from) reorder(from, to)
        }
        cancel()
    }

    fun cancel() {
        draggingIndex = null
        dropIndex = null
    }
}

// --- naming + immutable list helpers --------------------------------------

private fun specName(spec: ModifierSpec): String = when (spec) {
    is Padding -> "padding"
    is Size -> "size"
    is ModifierSpec.Width -> "width"
    is ModifierSpec.Height -> "height"
    is Offset -> "offset"
    is Background -> "background"
    is Weight -> "weight"
    is AspectRatio -> "aspectRatio"
    is Clip -> "clip"
    is ModifierSpec.Alpha -> "alpha"
    is ModifierSpec.Border -> "border"
    is ModifierSpec.DropShadow -> "dropShadow"
    is ModifierSpec.InnerShadow -> "innerShadow"
    FillMaxWidth -> "fillMaxWidth"
    FillMaxHeight -> "fillMaxHeight"
    FillMaxSize -> "fillMaxSize"
}

private fun List<ModifierSpec>.insertedAt(i: Int, spec: ModifierSpec): List<ModifierSpec> =
    toMutableList().apply { add(i.coerceIn(0, size), spec) }

private fun List<ModifierSpec>.movedTo(from: Int, to: Int): List<ModifierSpec> {
    val m = toMutableList()
    val item = m.removeAt(from)
    m.add(to.coerceIn(0, m.size), item)
    return m
}

private fun List<ModifierSpec>.without(i: Int): List<ModifierSpec> =
    toMutableList().apply { removeAt(i) }

private fun List<ModifierSpec>.replaced(i: Int, spec: ModifierSpec): List<ModifierSpec> =
    toMutableList().apply { this[i] = spec }
