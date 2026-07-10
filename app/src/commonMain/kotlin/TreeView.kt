package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import composer.model.Node
import composer.model.childNodes
import composer.model.isContainer
import composer.model.typeName
import composer.ui.AppIcon
import composer.ui.AppIconKind
import composer.ui.ComponentGlyph
import composer.ui.SectionHeader
import composer.ui.SquareIconButton
import composer.ui.Tk

private val ROW_HEIGHT = 28.dp

/**
 * Layers panel — the design tree as an indented, collapsible list with
 * Figma-style drag & drop. Click a row to select; drag a row to reorder (drop
 * on the top/bottom half to place before/after a sibling) or to nest (drop in
 * the middle of a container). Two-way synced with the canvas via {selectedId}.
 */
@Composable
fun TreeView(state: EditorState, modifier: Modifier = Modifier, onCollapse: (() -> Unit)? = null) {
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val dnd = remember { TreeDndState() }
    val scroll = rememberScrollState()
    // Window-space viewport of the scrollable area (top, height) — measured OUTSIDE
    // verticalScroll so it's the clipped panel, not the full content height.
    var viewport by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    // Edge auto-scroll while dragging: rows scrolled out of the panel would
    // otherwise be unreachable drop targets.
    LaunchedEffect(dnd.draggingId) {
        if (dnd.draggingId == null) return@LaunchedEffect
        while (true) {
            val y = dnd.pointerY
            val vp = viewport
            if (y != null && vp != null) {
                val (top, height) = vp
                val edge = 48f
                val step = when {
                    y < top + edge -> -10f
                    y > top + height - edge -> 10f
                    else -> 0f
                }
                if (step != 0f) {
                    scroll.scrollBy(step)
                    dnd.update(y) // rows moved under the pointer — refresh the drop target
                }
            }
            withFrameNanos { }
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(46.dp).padding(start = 16.dp, end = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader("Layers", icon = AppIconKind.Layers, modifier = Modifier.weight(1f))
            onCollapse?.let { SquareIconButton(AppIconKind.CollapseLeft, tip = "Hide layers", onClick = it) }
        }
        Column(
            modifier = Modifier
                .onGloballyPositioned { c ->
                    viewport = c.positionInWindow().y to c.size.height.toFloat()
                }
                .verticalScroll(scroll)
                .padding(horizontal = 6.dp)
                .padding(bottom = 8.dp),
        ) {
            TreeRow(state.root, depth = 0, state = state, expanded = expanded, dnd = dnd)
        }
    }
}

@Composable
private fun TreeRow(
    node: Node,
    depth: Int,
    state: EditorState,
    expanded: MutableMap<String, Boolean>,
    dnd: TreeDndState,
) {
    RowItem(node, depth, state, expanded, dnd)
    if (expanded[node.id] != false) {
        for (child in node.childNodes()) TreeRow(child, depth + 1, state, expanded, dnd)
    }
}

@Composable
private fun RowItem(
    node: Node,
    depth: Int,
    state: EditorState,
    expanded: MutableMap<String, Boolean>,
    dnd: TreeDndState,
) {
    val children = node.childNodes()
    val isExpanded = expanded[node.id] != false
    val isSelected = state.selectedId == node.id
    val isDragging = dnd.draggingId == node.id
    val drop = dnd.drop

    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val tint = if (isSelected) Tk.accent else Tk.textSecondary

    val isInsideTarget = drop is Drop.Inside && drop.id == node.id
    val bg = when {
        isInsideTarget -> Tk.accentSoft
        isSelected -> Tk.accentSoft
        hovered -> Tk.elevated
        else -> Color.Transparent
    }

    // Drop the row's hit box when it leaves the composition — collapsed rows and
    // renumbered ids (the IDE round-trip re-ids every node) otherwise linger as
    // ghost bands that win the drop-target scan and no-op every move there.
    DisposableEffect(node.id) {
        onDispose { dnd.bounds.remove(node.id) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .onGloballyPositioned { c ->
                dnd.bounds[node.id] = RowBox(c.positionInWindow().y, c.size.height.toFloat(), node.isContainer())
            }
            .alpha(if (isDragging) 0.4f else 1f)
            .clip(RoundedCornerShape(Tk.rXs))
            .background(bg)
            .then(if (isInsideTarget) Modifier.border(1.dp, Tk.accent, RoundedCornerShape(Tk.rXs)) else Modifier)
            .hoverable(interaction)
            // Slots are permanent — they can't be dragged (drops into them still work).
            .then(if (node is Node.Slot) Modifier else Modifier.pointerInput(node.id) {
                detectDragGestures(
                    onDragStart = { dnd.start(node.id, state) },
                    onDrag = { change, _ ->
                        change.consume()
                        val top = dnd.bounds[node.id]?.top ?: 0f
                        dnd.update(top + change.position.y)
                    },
                    onDragEnd = { dnd.end(state) },
                    onDragCancel = { dnd.cancel() },
                )
            })
            .clickable(interactionSource = interaction, indication = null) { state.select(node.id) },
    ) {
        // Reorder drop indicators (line above / below this row).
        if (drop is Drop.Before && drop.id == node.id) DropLine(Modifier.align(Alignment.TopCenter))
        if (drop is Drop.After && drop.id == node.id) DropLine(Modifier.align(Alignment.BottomCenter))

        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Spacer(Modifier.width((depth * 14).dp))

            if (children.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            expanded[node.id] = !isExpanded
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(if (isExpanded) AppIconKind.ChevronDown else AppIconKind.ChevronRight, Modifier.size(11.dp), tint = Tk.textMuted)
                }
            } else {
                Spacer(Modifier.width(14.dp))
            }

            ComponentGlyph(node.typeName(), Modifier.size(13.dp), tint = tint)

            BasicText(
                // A slot row shows its Compose argument name (topBar, bottomBar, fab).
                if (node is Node.Slot) node.name else state.layerName(node.id) ?: node.typeName(),
                style = TextStyle(
                    color = if (isSelected) Tk.textPrimary else Tk.textSecondary,
                    fontSize = 12.5.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = nodeDetail(node)
            if (detail != null) {
                BasicText(
                    detail,
                    style = TextStyle(color = Tk.textMuted, fontSize = 11.sp, fontFamily = FontFamily.Default),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

@Composable
private fun DropLine(modifier: Modifier) {
    Box(modifier.fillMaxWidth().height(2.dp).padding(horizontal = 4.dp).background(Tk.accent))
}

private fun nodeDetail(node: Node): String? = when (node) {
    is Node.Text -> node.text
    is Node.Button -> node.children.filterIsInstance<Node.Text>().firstOrNull()?.text
    is Node.Image -> node.contentDescription.ifBlank { null }
    else -> null
}

// --- drag & drop state -----------------------------------------------------

private data class RowBox(val top: Float, val height: Float, val isContainer: Boolean)

private sealed interface Drop {
    data class Before(val id: String) : Drop
    data class After(val id: String) : Drop
    data class Inside(val id: String) : Drop
}

private class TreeDndState {
    var draggingId by mutableStateOf<String?>(null)
    var drop by mutableStateOf<Drop?>(null)
    val bounds = mutableStateMapOf<String, RowBox>()

    /** Pointer window-Y while dragging — drives the panel's edge auto-scroll. */
    var pointerY by mutableStateOf<Float?>(null)
        private set

    fun start(id: String, state: EditorState) {
        draggingId = id
        drop = null
        state.select(id)
    }

    /** Recompute the drop target from the pointer's window-Y. */
    fun update(windowY: Float) {
        val dragId = draggingId ?: return
        pointerY = windowY
        val entry = bounds.entries.firstOrNull { (id, b) ->
            id != dragId && windowY >= b.top && windowY < b.top + b.height
        }
        if (entry == null) {
            drop = null
            return
        }
        val (id, b) = entry
        val frac = (windowY - b.top) / b.height
        drop = when {
            b.isContainer && frac > 0.3f && frac < 0.7f -> Drop.Inside(id)
            frac < 0.5f -> Drop.Before(id)
            else -> Drop.After(id)
        }
    }

    fun end(state: EditorState) {
        val dragId = draggingId
        when (val d = drop) {
            is Drop.Before -> if (dragId != null) state.dropBefore(dragId, d.id)
            is Drop.After -> if (dragId != null) state.dropAfter(dragId, d.id)
            is Drop.Inside -> if (dragId != null) state.dropInto(dragId, d.id)
            null -> Unit
        }
        cancel()
    }

    fun cancel() {
        draggingId = null
        drop = null
        pointerY = null
    }
}
