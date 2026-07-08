package composer.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The design tree — the single source of truth (see GOAL.md). The canvas and
 * the generated code are both projections of this immutable tree.
 *
 * Pure data — no Compose dependency. Serializable for persistence (M6).
 */
@Serializable
sealed interface Node {
    val id: String
    val modifier: List<ModifierSpec>

    @Serializable
    @SerialName("Text")
    data class Text(
        override val id: String,
        val text: String,
        override val modifier: List<ModifierSpec> = emptyList(),
        val fontSize: Int = 0, // sp; 0 = inherit
        val fontWeight: TextWeight = TextWeight.Normal,
        val fontFamily: TextFontFamily = TextFontFamily.Default,
        val color: Long? = null, // ARGB; null = inherit
        val lineHeight: Int = 0, // sp; 0 = auto (fontSize × 1.2)
        val customFont: String = "", // device font family name (Local Font Access); overrides fontFamily
        val textAlign: TextAlignment = TextAlignment.Start,
    ) : Node

    @Serializable
    @SerialName("Button")
    /**
     * A CONTAINER (like the real M3 Button, whose content is a RowScope lambda):
     * it has NO label of its own — put Text/Icon/anything inside as children.
     * Sizing follows Material3's own metrics (40dp height, content width) unless
     * the user adds Size modifiers. [variant] picks Button/ElevatedButton/
     * FilledTonalButton/OutlinedButton/TextButton.
     *
     * [label] exists ONLY to deserialize pre-container saves; [normalizeButtons]
     * converts it into a Text child and clears it. Never set it in new code.
     */
    data class Button(
        override val id: String,
        val label: String = "",
        override val modifier: List<ModifierSpec> = emptyList(),
        val variant: ButtonVariant = ButtonVariant.Filled,
        val children: List<Node> = emptyList(),
    ) : Node

    @Serializable
    @SerialName("Spacer")
    data class Spacer(
        override val id: String,
        override val modifier: List<ModifierSpec> = emptyList(),
    ) : Node

    @Serializable
    @SerialName("Column")
    data class Column(
        override val id: String,
        val children: List<Node> = emptyList(),
        val verticalArrangement: VArrangement = VArrangement.Top,
        val horizontalAlignment: HAlignment = HAlignment.Start,
        override val modifier: List<ModifierSpec> = emptyList(),
        val spacing: Int = 0, // dp; > 0 → Arrangement.spacedBy (overrides verticalArrangement)
    ) : Node

    @Serializable
    @SerialName("Row")
    data class Row(
        override val id: String,
        val children: List<Node> = emptyList(),
        val horizontalArrangement: HArrangement = HArrangement.Start,
        val verticalAlignment: VAlignment = VAlignment.Top,
        override val modifier: List<ModifierSpec> = emptyList(),
        val spacing: Int = 0, // dp; > 0 → Arrangement.spacedBy (overrides horizontalArrangement)
    ) : Node

    @Serializable
    @SerialName("Box")
    data class Box(
        override val id: String,
        val children: List<Node> = emptyList(),
        val contentAlignment: BoxAlignment = BoxAlignment.TopStart,
        override val modifier: List<ModifierSpec> = emptyList(),
    ) : Node

    @Serializable
    @SerialName("Image")
    data class Image(
        override val id: String,
        val contentDescription: String = "",
        val placeholderColor: Long = 0xFFCFD4DC,
        override val modifier: List<ModifierSpec> = emptyList(),
        val url: String = "", // http(s) URL → Coil AsyncImage; data: URL → a picked local image (preview only)
    ) : Node

    @Serializable
    @SerialName("Divider")
    data class Divider(
        override val id: String,
        override val modifier: List<ModifierSpec> = emptyList(),
    ) : Node

    @Serializable
    @SerialName("Icon")
    data class Icon(
        override val id: String,
        val icon: IconKind = IconKind.Favorite,
        val contentDescription: String = "",
        override val modifier: List<ModifierSpec> = emptyList(),
        // Free-form Material Symbols name (e.g. "shopping_cart"). Non-empty WINS
        // over [icon]: the editor renders the bundled symbol; codegen emits
        // painterResource(Res.drawable.ic_<name>) + a sourcing comment. Empty =
        // legacy curated [IconKind] (Icons.Default.X). Placed after [modifier] so
        // positional calls and old JSON keep working.
        val symbol: String = "",
    ) : Node

    @Serializable
    @SerialName("IconButton")
    data class IconButton(
        override val id: String,
        val icon: IconKind = IconKind.Menu,
        override val modifier: List<ModifierSpec> = emptyList(),
        val symbol: String = "", // same contract as [Icon.symbol]
    ) : Node

    /** Material3 TabRow: children are [Tab] leaves; [selectedIndex] drives the indicator and the generated initial state. */
    @Serializable
    @SerialName("TabRow")
    data class TabRow(
        override val id: String,
        val children: List<Node> = emptyList(),
        val selectedIndex: Int = 0,
        override val modifier: List<ModifierSpec> = emptyList(),
    ) : Node

    /** One tab in a [TabRow] — selection/click are index-derived at emit time. */
    @Serializable
    @SerialName("Tab")
    data class Tab(
        override val id: String,
        val label: String = "Tab",
        override val modifier: List<ModifierSpec> = emptyList(),
    ) : Node

    /** Material3 NavigationBar: children are [NavItem] leaves. */
    @Serializable
    @SerialName("NavigationBar")
    data class NavigationBar(
        override val id: String,
        val children: List<Node> = emptyList(),
        val selectedIndex: Int = 0,
        override val modifier: List<ModifierSpec> = emptyList(),
    ) : Node

    /** One NavigationBar destination: label + Material Symbols icon name. */
    @Serializable
    @SerialName("NavItem")
    data class NavItem(
        override val id: String,
        val label: String = "Home",
        val symbol: String = "home",
        override val modifier: List<ModifierSpec> = emptyList(),
    ) : Node

    /** Material3 chip. [selected] applies to Filter/Input variants; [symbol] = optional leading icon. */
    @Serializable
    @SerialName("Chip")
    data class Chip(
        override val id: String,
        val label: String = "Chip",
        val variant: ChipVariant = ChipVariant.Assist,
        val selected: Boolean = false,
        val symbol: String = "",
        override val modifier: List<ModifierSpec> = emptyList(),
    ) : Node

    /** Material3 BadgedBox: [badge] text over the content children (empty = dot badge). */
    @Serializable
    @SerialName("BadgedBox")
    data class BadgedBox(
        override val id: String,
        val badge: String = "3",
        val children: List<Node> = emptyList(),
        override val modifier: List<ModifierSpec> = emptyList(),
    ) : Node

    /**
     * Free-drawing surface: children are SHAPE leaves ([Line]/[RectShape]/[CircleShape]/
     * [EllipseShape]/[ArcShape]) drawn in order inside ONE `Canvas { }` block — they are
     * draw calls, not composables, so they have no layout bounds (select them in the
     * Layers tree). Size the canvas itself with Size/fill modifiers; coordinates are dp.
     */
    @Serializable
    @SerialName("Canvas")
    data class Canvas(
        override val id: String,
        val children: List<Node> = emptyList(),
        override val modifier: List<ModifierSpec> = emptyList(),
    ) : Node

    @Serializable
    @SerialName("Line")
    data class Line(
        override val id: String,
        val x1: Int = 0,
        val y1: Int = 0,
        val x2: Int = 120,
        val y2: Int = 0,
        val color: Long = 0xFF1F2937,
        val strokeWidth: Int = 2,
        override val modifier: List<ModifierSpec> = emptyList(), // unused — shapes aren't layout nodes
    ) : Node

    @Serializable
    @SerialName("RectShape")
    data class RectShape(
        override val id: String,
        val x: Int = 0,
        val y: Int = 0,
        val width: Int = 120,
        val height: Int = 80,
        val color: Long = 0xFF6366F1,
        val filled: Boolean = true,
        val strokeWidth: Int = 2,
        val corner: Int = 0,
        override val modifier: List<ModifierSpec> = emptyList(),
    ) : Node

    @Serializable
    @SerialName("CircleShape")
    data class CircleShape(
        override val id: String,
        val cx: Int = 60,
        val cy: Int = 60,
        val radius: Int = 50,
        val color: Long = 0xFF6366F1,
        val filled: Boolean = true,
        val strokeWidth: Int = 2,
        override val modifier: List<ModifierSpec> = emptyList(),
    ) : Node

    @Serializable
    @SerialName("EllipseShape")
    data class EllipseShape(
        override val id: String,
        val x: Int = 0,
        val y: Int = 0,
        val width: Int = 140,
        val height: Int = 80,
        val color: Long = 0xFF6366F1,
        val filled: Boolean = true,
        val strokeWidth: Int = 2,
        override val modifier: List<ModifierSpec> = emptyList(),
    ) : Node

    /** [filled] draws a pie wedge (useCenter); unfilled draws the arc stroke only. */
    @Serializable
    @SerialName("ArcShape")
    data class ArcShape(
        override val id: String,
        val x: Int = 0,
        val y: Int = 0,
        val width: Int = 120,
        val height: Int = 120,
        val startAngle: Int = 0,
        val sweepAngle: Int = 120,
        val color: Long = 0xFF6366F1,
        val filled: Boolean = false,
        val strokeWidth: Int = 2,
        override val modifier: List<ModifierSpec> = emptyList(),
    ) : Node

    @Serializable
    @SerialName("TextField")
    data class TextField(
        override val id: String,
        val value: String = "",
        val placeholder: String = "Label",
        override val modifier: List<ModifierSpec> = emptyList(),
    ) : Node

    @Serializable
    @SerialName("Switch")
    data class Switch(
        override val id: String,
        val checked: Boolean = false,
        override val modifier: List<ModifierSpec> = emptyList(),
    ) : Node

    @Serializable
    @SerialName("Checkbox")
    data class Checkbox(
        override val id: String,
        val checked: Boolean = false,
        override val modifier: List<ModifierSpec> = emptyList(),
    ) : Node

    @Serializable
    @SerialName("RadioButton")
    data class RadioButton(
        override val id: String,
        val selected: Boolean = false,
        override val modifier: List<ModifierSpec> = emptyList(),
    ) : Node

    @Serializable
    @SerialName("Slider")
    data class Slider(
        override val id: String,
        val value: Float = 0.5f,
        override val modifier: List<ModifierSpec> = emptyList(),
    ) : Node

    @Serializable
    @SerialName("CircularProgress")
    data class CircularProgress(
        override val id: String,
        override val modifier: List<ModifierSpec> = emptyList(),
    ) : Node

    @Serializable
    @SerialName("LinearProgress")
    data class LinearProgress(
        override val id: String,
        override val modifier: List<ModifierSpec> = emptyList(),
    ) : Node

    @Serializable
    @SerialName("Card")
    data class Card(
        override val id: String,
        val children: List<Node> = emptyList(),
        override val modifier: List<ModifierSpec> = emptyList(),
    ) : Node

    /** Material3 FloatingActionButton — a container so its content can be anything (icon, text, …). */
    @Serializable
    @SerialName("Fab")
    data class Fab(
        override val id: String,
        val children: List<Node> = emptyList(),
        override val modifier: List<ModifierSpec> = emptyList(),
    ) : Node

    /** A modal `Dialog` (Surface content). Previewed inline as a card; codegen wraps in `Dialog { Surface { … } }`. */
    @Serializable
    @SerialName("Dialog")
    data class Dialog(
        override val id: String,
        val children: List<Node> = emptyList(),
        override val modifier: List<ModifierSpec> = emptyList(),
    ) : Node

    /** A Material3 `ModalBottomSheet`. Previewed inline as a bottom surface; codegen wraps in `ModalBottomSheet { … }`. */
    @Serializable
    @SerialName("BottomSheet")
    data class BottomSheet(
        override val id: String,
        val children: List<Node> = emptyList(),
        override val modifier: List<ModifierSpec> = emptyList(),
    ) : Node

    @Serializable
    @SerialName("Scaffold")
    data class Scaffold(
        override val id: String,
        val children: List<Node> = emptyList(),
        val topBar: Node? = null,
        val bottomBar: Node? = null,
        val fab: Node? = null,
        override val modifier: List<ModifierSpec> = emptyList(),
    ) : Node

    /** Material3 CenterAlignedTopAppBar: navigation icon (left), title (center), actions (right). */
    @Serializable
    @SerialName("TopAppBar")
    data class TopAppBar(
        override val id: String,
        val title: Node? = null,
        val navigationIcon: Node? = null,
        val actions: List<Node> = emptyList(),
        override val modifier: List<ModifierSpec> = emptyList(),
        val variant: TopAppBarVariant = TopAppBarVariant.CenterAligned,
    ) : Node

    /**
     * A Compose **slot argument** made visible in the tree — e.g. a Scaffold's
     * `topBar` / `bottomBar` / `floatingActionButton`. Permanent: created with its
     * parent, can't be deleted, moved, or copied on its own — only its children
     * change. [name] is the Compose argument name and the layer label. An empty
     * slot emits nothing (the argument is omitted from the generated call).
     */
    @Serializable
    @SerialName("Slot")
    data class Slot(
        override val id: String,
        val name: String,
        val children: List<Node> = emptyList(),
        override val modifier: List<ModifierSpec> = emptyList(),
    ) : Node

    /**
     * An **instance of a reusable component**: references another node in the tree
     * (the *main*, registered in [Artboard.componentIds]) by [refId]. Renders the
     * main's subtree as one unit; codegen emits a call to the extracted
     * `@Composable fun` instead of inlining. [modifier] applies OUTSIDE the
     * main's own chain (wrapper semantics). Deleting the main leaves instances
     * dangling — they render/emit a placeholder.
     */
    @Serializable
    @SerialName("Instance")
    data class Instance(
        override val id: String,
        val refId: String,
        override val modifier: List<ModifierSpec> = emptyList(),
    ) : Node

    /**
     * Opaque preserved Kotlin source (IDE plugin code-import): a statement the
     * designer can't represent (if/when, loops, state logic, custom composables).
     * [code] is verbatim dedented source, re-emitted as-is by codegen — the one
     * deliberate esc()-exempt emission. Locked in the editor (selectable, never
     * content-editable), never in the palette; created only by the code parser.
     */
    @Serializable
    @SerialName("RawCode")
    data class RawCode(
        override val id: String,
        val code: String = "",
        override val modifier: List<ModifierSpec> = emptyList(),
    ) : Node

    /**
     * Not a real UI component — one composable **function scope** (the body of a
     * generated `@Composable fun`). Its children are emitted directly into the
     * function (no wrapper). Lives directly under the [Artboard]; its layer name
     * becomes the generated function name. [x]/[y] are the frame's position on the
     * artboard canvas and [width]/[height] its screen size — editor geometry only,
     * never emitted into code.
     *
     * Serialized as "Frame" for backward compatibility with old saves (where a
     * single Frame was the design root). The legacy [theme]/[layerNames] fields are
     * kept so old JSON round-trips; [migrateToArtboard] lifts them onto the Artboard.
     */
    @Serializable
    @SerialName("Frame")
    data class Composable(
        override val id: String,
        val children: List<Node> = emptyList(),
        override val modifier: List<ModifierSpec> = emptyList(),
        val theme: DesignTheme = DesignTheme(), // legacy (pre-Artboard roots); see migrateToArtboard
        val layerNames: Map<String, String> = emptyMap(), // legacy (pre-Artboard roots)
        val x: Int = 0, // canvas position (dp) on the artboard
        val y: Int = 0,
        val width: Int = 390, // screen size (dp)
        val height: Int = 844,
    ) : Node

    /**
     * The design root — the canvas that holds any number of [Composable] screens.
     * Owns the design's Material [themes] (a user-editable named list — e.g.
     * "Light", "Dark", "Brand" — with [activeTheme] selecting the one the preview
     * and the generated default use) and the custom [layerNames] map.
     * Not a UI component; it can't be deleted, moved, or given modifiers.
     *
     * [theme] is the legacy single-theme field (pre-multi-theme saves); the
     * migration lifts it into [themes]. Post-migration [themes] is never empty.
     */
    @Serializable
    @SerialName("Artboard")
    data class Artboard(
        override val id: String,
        val composables: List<Node> = emptyList(), // each is a [Composable]
        override val modifier: List<ModifierSpec> = emptyList(),
        val theme: DesignTheme = DesignTheme(), // legacy (single-theme saves); see migration
        val layerNames: Map<String, String> = emptyMap(), // custom layer names, keyed by node id
        val themes: List<NamedTheme> = emptyList(),
        val activeTheme: Int = 0,
        // Reusable-component registry: ids of MAIN nodes (their layer name is the
        // component/function name). Ids whose node no longer exists are ignored.
        val componentIds: List<String> = emptyList(),
    ) : Node {
        /** The theme the preview renders and codegen defaults to. */
        fun currentTheme(): DesignTheme =
            themes.getOrNull(activeTheme.coerceIn(0, (themes.size - 1).coerceAtLeast(0)))?.theme ?: theme
    }
}

/** A user-named Material theme (e.g. "Light", "Dark", "Brand"). */
@Serializable
data class NamedTheme(val name: String, val theme: DesignTheme = DesignTheme())

/**
 * Migrate a decoded design root to the Artboard model. Old saves (and imports)
 * have a single "Frame" (now [Node.Composable]) as the root carrying the theme
 * and layer names — lift those onto a new [Node.Artboard] wrapping it. An
 * already-migrated root passes through unchanged. [frameWidth]/[frameHeight]
 * seed the migrated screen's size (old designs kept it outside the model).
 */
fun Node.migrateToArtboard(frameWidth: Int = 1024, frameHeight: Int = 680): Node.Artboard = when (this) {
    is Node.Artboard -> this
    is Node.Composable -> Node.Artboard(
        id = "artboard-$id",
        composables = listOf(copy(theme = DesignTheme(), layerNames = emptyMap(), width = frameWidth, height = frameHeight)),
        theme = theme,
        layerNames = layerNames,
    )
    else -> Node.Artboard(
        id = "artboard-root",
        composables = listOf(Node.Composable(id = "screen-root", children = listOf(this), width = frameWidth, height = frameHeight)),
    )
}.migrateThemes().normalizeSlots().normalizeButtons() as Node.Artboard

/**
 * Convert legacy Button labels (Button used to be a leaf with a `label`) into a
 * Text child — Buttons are containers now with no label of their own. The Text
 * child id derives from the button id (deterministic; `dedupeIds` resolves any
 * collision). Recursive over the tree, idempotent.
 */
fun Node.normalizeButtons(): Node {
    val mapped = mapChildren { it.normalizeButtons() }
    return if (mapped is Node.Button && mapped.label.isNotEmpty()) {
        mapped.copy(label = "", children = mapped.children + Node.Text("${mapped.id}-label", mapped.label))
    } else mapped
}

/**
 * Ensure the artboard has a non-empty named [Node.Artboard.themes] list: legacy
 * single-theme saves get their [Node.Artboard.theme] lifted as the first entry
 * (named after its base scheme). Idempotent.
 */
fun Node.Artboard.migrateThemes(): Node.Artboard =
    if (themes.isNotEmpty()) this
    else copy(themes = listOf(NamedTheme(if (theme.dark) "Dark" else "Light", theme)), activeTheme = 0)

/**
 * Ensure every Scaffold's slots are permanent [Node.Slot] containers: legacy
 * direct slot content (a bare TopAppBar/Box/Fab) is wrapped in a Slot, and an
 * absent slot becomes an empty Slot. Slot ids are derived from the Scaffold id
 * (deterministic; `dedupeIds` resolves any collision). Recursive over the tree.
 */
fun Node.normalizeSlots(): Node {
    val mapped = mapChildren { it.normalizeSlots() }
    return if (mapped is Node.Scaffold) mapped.copy(
        topBar = mapped.topBar.asSlot("${mapped.id}-topBar", "topBar"),
        bottomBar = mapped.bottomBar.asSlot("${mapped.id}-bottomBar", "bottomBar"),
        fab = mapped.fab.asSlot("${mapped.id}-fab", "fab"),
    ) else mapped
}

private fun Node?.asSlot(id: String, name: String): Node.Slot = when (this) {
    null -> Node.Slot(id, name)
    is Node.Slot -> this
    else -> Node.Slot(id, name, children = listOf(this))
}
