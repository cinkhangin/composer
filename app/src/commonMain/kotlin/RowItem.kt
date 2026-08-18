package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import composer.model.isContainer
import composer.model.typeName
import composer.ui.AppIcon
import composer.ui.AppIconKind
import composer.ui.ComponentGlyph
import composer.ui.Tk

@Composable
internal fun RowItem(
    node: Node,
    depth: Int,
    state: EditorState,
    expanded: MutableMap<String, Boolean>,
    dnd: TreeDndState,
) {
    val children = node.designerChildren()
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
