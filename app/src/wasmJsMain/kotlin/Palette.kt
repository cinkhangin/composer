package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeomSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import composer.model.IconKind
import composer.model.ModifierSpec.Size
import composer.model.Node
import composer.model.TextWeight
import composer.ui.ComponentGlyph
import composer.ui.Island
import composer.ui.Tk

/**
 * Figma-style floating component bar. Pinned to the bottom-center of the canvas;
 * clicking a tool inserts a new node relative to the current selection
 * (see [EditorState.insert]).
 */
@Composable
fun FloatingPalette(state: EditorState, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    Island(modifier) {
        // Edge fades signal that the bar scrolls horizontally when tools overflow —
        // without them, clipped icons at the island edge just look broken.
        Box(
            Modifier.drawWithContent {
                drawContent()
                val w = 28.dp.toPx()
                if (scroll.canScrollBackward) {
                    drawRect(
                        brush = Brush.horizontalGradient(0f to Tk.panel, 1f to Tk.panel.copy(alpha = 0f), endX = w),
                        size = GeomSize(w, size.height),
                    )
                }
                if (scroll.canScrollForward) {
                    drawRect(
                        brush = Brush.horizontalGradient(0f to Tk.panel.copy(alpha = 0f), 1f to Tk.panel, startX = size.width - w),
                        topLeft = Offset(size.width - w, 0f),
                        size = GeomSize(w, size.height),
                    )
                }
            },
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(scroll)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                paletteGroups.forEachIndexed { i, group ->
                    if (i > 0) Box(Modifier.size(width = 1.dp, height = 26.dp).padding(horizontal = 3.dp).background(Tk.border))
                    for (item in group.items) {
                        PaletteTool(item.type) { state.insert(item.factory) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaletteTool(type: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(type, fontSize = 12.sp) } },
        state = rememberTooltipState(),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(Tk.rSm))
                .background(if (hovered) Tk.elevated else Color.Transparent)
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null) { onClick() }
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(Tk.rXs)).background(Tk.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                ComponentGlyph(type, Modifier.size(17.dp), tint = if (hovered) Tk.accentHover else Tk.accent)
            }
        }
    }
}

private class PaletteItem(val type: String, val factory: (id: String) -> Node)
private class PaletteGroup(val label: String, val items: List<PaletteItem>)

// Grouped into labeled sections (rendered with dividers). Palette defaults: NO
// padding, uniform 48×48 Size where a default is needed (containers must have a
// size to be visible/selectable while empty; intrinsically-sized leaves get none).
private val paletteGroups: List<PaletteGroup> = listOf(
    PaletteGroup("Basics", listOf(
        PaletteItem("Text") { id -> Node.Text(id, "Text") },
        PaletteItem("Button") { id -> Node.Button(id, children = listOf(Node.Text("${id}t", "Button"))) },
        PaletteItem("TextField") { id -> Node.TextField(id, modifier = listOf(Size(48, 48))) },
        PaletteItem("Icon") { id -> Node.Icon(id) },
        PaletteItem("IconButton") { id -> Node.IconButton(id) },
        PaletteItem("Image") { id -> Node.Image(id, modifier = listOf(Size(48, 48))) },
        PaletteItem("Divider") { id -> Node.Divider(id) },
        PaletteItem("Spacer") { id -> Node.Spacer(id, modifier = listOf(Size(48, 48))) },
    )),
    PaletteGroup("Layout", listOf(
        PaletteItem("Column") { id -> Node.Column(id, modifier = listOf(Size(48, 48))) },
        PaletteItem("Row") { id -> Node.Row(id, modifier = listOf(Size(48, 48))) },
        PaletteItem("Box") { id -> Node.Box(id, modifier = listOf(Size(48, 48))) },
        PaletteItem("Card") { id -> Node.Card(id, modifier = listOf(Size(48, 48))) },
    )),
    PaletteGroup("Controls", listOf(
        PaletteItem("Switch") { id -> Node.Switch(id, checked = true) },
        PaletteItem("Checkbox") { id -> Node.Checkbox(id, checked = true) },
        PaletteItem("RadioButton") { id -> Node.RadioButton(id, selected = true) },
        PaletteItem("Slider") { id -> Node.Slider(id, modifier = listOf(Size(48, 48))) },
        PaletteItem("CircularProgress") { id -> Node.CircularProgress(id) },
        PaletteItem("LinearProgress") { id -> Node.LinearProgress(id, modifier = listOf(Size(48, 48))) },
    )),
    // Scaffolding / app-bar section
    PaletteGroup("Structure", listOf(
        // Scaffold ships with its three permanent slot containers (Compose's slot
        // arguments made visible in the Layers tree) — empty until the user fills them.
        // No default Size: the real Scaffold fills its max constraints on its own,
        // so it takes the whole screen in preview and generated code alike.
        PaletteItem("Scaffold") { id ->
            Node.Scaffold(
                id,
                topBar = Node.Slot("$id-topBar", "topBar"),
                bottomBar = Node.Slot("$id-bottomBar", "bottomBar"),
                fab = Node.Slot("$id-fab", "fab"),
            )
        },
        PaletteItem("Fab") { id -> Node.Fab(id, children = listOf(Node.Icon("${id}i", IconKind.Add))) },
        PaletteItem("TopAppBar") { id ->
            Node.TopAppBar(
                id,
                title = Node.Text("${id}t", "Title"),
                navigationIcon = Node.IconButton("${id}n"),
            )
        },
    )),
    // Overlays / modal section
    PaletteGroup("Overlays", listOf(
        PaletteItem("Dialog") { id ->
            Node.Dialog(
                id,
                children = listOf(
                    Node.Text("${id}t", "Dialog title", fontSize = 20, fontWeight = TextWeight.Bold),
                    Node.Text("${id}b", "Dialog body text."),
                ),
            )
        },
        PaletteItem("BottomSheet") { id ->
            Node.BottomSheet(
                id,
                children = listOf(Node.Text("${id}t", "Bottom sheet content")),
            )
        },
    )),
)
