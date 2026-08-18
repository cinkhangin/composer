package composer

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import composer.model.Node
import composer.model.childNodes
import composer.model.isContainer
import composer.ui.AppIconKind
import composer.ui.SectionHeader
import composer.ui.SquareIconButton

internal val ROW_HEIGHT = 28.dp

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
            Modifier.fillMaxWidth().height(42.dp).padding(start = 12.dp, end = 7.dp),
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
                .padding(horizontal = 4.dp)
                .padding(bottom = 6.dp),
        ) {
            TreeRow(state.root, depth = 0, state = state, expanded = expanded, dnd = dnd)
        }
    }
}

/** RawCode is source-preservation metadata, not a visible designer layer. */
internal fun Node.designerChildren(): List<Node> = childNodes().filterNot { it is Node.RawCode }

internal fun nodeDetail(node: Node): String? = when (node) {
    is Node.Text -> node.text
    is Node.Button -> node.children.filterIsInstance<Node.Text>().firstOrNull()?.text
    is Node.Image -> node.contentDescription.ifBlank { null }
    else -> null
}

// --- drag & drop state -----------------------------------------------------

internal data class RowBox(val top: Float, val height: Float, val isContainer: Boolean)

internal sealed interface Drop {
    data class Before(val id: String) : Drop
    data class After(val id: String) : Drop
    data class Inside(val id: String) : Drop
}

internal class TreeDndState {
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
