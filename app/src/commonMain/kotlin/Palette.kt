package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeomSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import composer.model.IconKind
import composer.model.ModifierSpec
import composer.model.Node
import composer.model.TextWeight
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

private class PaletteItem(val type: String, val factory: (id: String) -> Node)
private class PaletteGroup(val label: String, val items: List<PaletteItem>)

// Grouped into labeled sections (rendered with dividers). Palette components
// carry NO default modifiers — a new component starts bare and SELECTED, so the
// user shapes it via the inspector (add modifiers) or the palette (add children).
// Empty containers render at zero size until given content or a Size modifier.
private val paletteGroups: List<PaletteGroup> = listOf(
    PaletteGroup("Basics", listOf(
        PaletteItem("Text") { id -> Node.Text(id, "Text") },
        PaletteItem("Button") { id -> Node.Button(id, children = listOf(Node.Text("${id}t", "Button"))) },
        PaletteItem("TextField") { id -> Node.TextField(id) },
        PaletteItem("Icon") { id -> Node.Icon(id) },
        PaletteItem("IconButton") { id -> Node.IconButton(id) },
        PaletteItem("Image") { id -> Node.Image(id) },
        PaletteItem("Divider") { id -> Node.Divider(id) },
        PaletteItem("Spacer") { id -> Node.Spacer(id) },
    )),
    PaletteGroup("Layout", listOf(
        PaletteItem("Layout") { id -> Node.Box(id) },
        PaletteItem("Card") { id -> Node.Card(id) },
    )),
    PaletteGroup("Navigation", listOf(
        PaletteItem("TabRow") { id ->
            Node.TabRow(id, children = listOf(Node.Tab("${id}a", "Tab 1"), Node.Tab("${id}b", "Tab 2")))
        },
        PaletteItem("Tab") { id -> Node.Tab(id) },
        PaletteItem("NavigationBar") { id ->
            Node.NavigationBar(
                id,
                children = listOf(
                    Node.NavItem("${id}a", "Home", "home"),
                    Node.NavItem("${id}b", "Search", "search"),
                    Node.NavItem("${id}c", "Profile", "person"),
                ),
            )
        },
        PaletteItem("NavItem") { id -> Node.NavItem(id) },
        PaletteItem("BadgedBox") { id ->
            Node.BadgedBox(id, children = listOf(Node.Icon("${id}i", symbol = "notifications")))
        },
    )),
    PaletteGroup("Controls", listOf(
        PaletteItem("Switch") { id -> Node.Switch(id, checked = true) },
        PaletteItem("Checkbox") { id -> Node.Checkbox(id, checked = true) },
        PaletteItem("RadioButton") { id -> Node.RadioButton(id, selected = true) },
        PaletteItem("Chip") { id -> Node.Chip(id) },
        PaletteItem("Slider") { id -> Node.Slider(id) },
        PaletteItem("CircularProgress") { id -> Node.CircularProgress(id) },
        PaletteItem("LinearProgress") { id -> Node.LinearProgress(id) },
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
    PaletteGroup("Drawing", listOf(
        // The one palette exception to "no default modifiers": a Canvas has no
        // intrinsic content size — without a Size it renders 0×0 and is unfindable.
        PaletteItem("Canvas") { id ->
            Node.Canvas(
                id,
                children = listOf(
                    Node.RectShape("${id}r", x = 10, y = 10, width = 120, height = 80, corner = 12),
                    Node.CircleShape("${id}c", cx = 150, cy = 120, radius = 40),
                ),
                modifier = listOf(ModifierSpec.Size(200, 200)),
            )
        },
        PaletteItem("Line") { id -> Node.Line(id) },
        PaletteItem("Rect") { id -> Node.RectShape(id) },
        PaletteItem("Circle") { id -> Node.CircleShape(id) },
        PaletteItem("Ellipse") { id -> Node.EllipseShape(id) },
        PaletteItem("Arc") { id -> Node.ArcShape(id) },
    )),
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
