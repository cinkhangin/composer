package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import composer.model.ModifierSpec
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

    // weight()/align() only compile inside the right scope, so only offer them there.
    // The scope kinds mirror codegen: Button children are RowScope, Dialog/BottomSheet
    // children live in the emitted wrapper Column.
    val parent = state.root.parentOf(selected.id)
    val canWeight = parent is Node.Row || parent is Node.Column
    val alignSeed: ModifierSpec.Align? = when (parent) {
        is Node.Box -> ModifierSpec.Align(box = composer.model.BoxAlignment.Center)
        is Node.Row, is Node.Button -> ModifierSpec.Align(vertical = composer.model.VAlignment.Center)
        is Node.Column, is Node.Dialog, is Node.BottomSheet -> ModifierSpec.Align(horizontal = composer.model.HAlignment.Center)
        else -> null
    }

    fun apply(next: List<ModifierSpec>, coalesceKey: String? = null) =
        state.update(selected.id, coalesceKey) { it.withModifier(next) }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (mods.isEmpty()) {
            BasicText("Nothing added yet — use + to add size, padding, fill or corners.", style = TextStyle(color = Tk.textMuted, fontSize = 12.sp), modifier = Modifier.padding(vertical = 4.dp))
        }

        for (i in mods.indices) {
            InsertGap(i, insertAt, dnd, mods, canWeight, alignSeed, onToggle = { insertAt = if (insertAt == i) null else i }) { spec ->
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
        InsertGap(mods.size, insertAt, dnd, mods, canWeight, alignSeed, onToggle = { insertAt = if (insertAt == mods.size) null else mods.size }) { spec ->
            apply(mods.insertedAt(mods.size, spec)); insertAt = null
        }
    }
}

// --- drag & drop state -----------------------------------------------------

internal data class RowSpan(val top: Float, val height: Float)

internal class ModifierDndState {
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

internal fun specName(spec: ModifierSpec): String = when (spec) {
    is ModifierSpec.External -> "source modifier"
    is ModifierSpec.ScaffoldPadding -> "scaffold padding"
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
    is ModifierSpec.Rotate -> "rotate"
    is ModifierSpec.Scale -> "scale"
    is ModifierSpec.Align -> "align"
    is ModifierSpec.ZIndex -> "zIndex"
    is ModifierSpec.Blur -> "blur"
    is FillMaxWidth -> "fillMaxWidth"
    is FillMaxHeight -> "fillMaxHeight"
    is FillMaxSize -> "fillMaxSize"
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
