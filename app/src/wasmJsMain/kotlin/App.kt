package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.browser.window
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.onEach
import org.w3c.dom.events.Event
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import composer.codegen.CodeGen
import composer.model.DesignJson
import composer.model.Node
import composer.model.backgroundCorner
import composer.model.findById
import composer.render.RenderNode
import composer.render.toColorScheme
import composer.ui.AppIcon
import composer.ui.AppIconKind
import composer.ui.HDivider
import composer.ui.Island
import composer.ui.Theme
import composer.ui.Tk
import composer.ui.TkMenu
import composer.ui.TkMenuItem
import composer.ui.ToolButton
import composer.ui.highlightKotlin
import composer.res.Res
import composer.res.jetbrainsmono_regular
import org.jetbrains.compose.resources.Font
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle

/**
 * Composer editor shell: toolbar on top; palette · canvas · inspector · code
 * panel below. All panes are projections of one [EditorState.root].
 */
@OptIn(FlowPreview::class)
@Composable
fun EditorScreen(ws: Workspace) {
    val state = remember { EditorState(ws.initialDesign) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    // Reclaim keyboard focus for the editor whenever the selection changes (e.g. after
    // clicking a component on the canvas or a layer in the tree), so keyboard shortcuts —
    // arrow-key nudge, Delete, copy/paste — keep working after touching an inspector field.
    LaunchedEffect(state.selectedId) {
        if (state.selectedId != null) runCatching { focusRequester.requestFocus() }
    }

    // Auto-save: persist to the current file shortly after the design (or name) changes.
    LaunchedEffect(state, ws) {
        snapshotFlow { state.root to ws.currentName }
            .drop(1) // skip the initial state — don't create a file for an untouched design
            .onEach { ws.markDirty() } // show "Saving…" immediately; the save below settles it
            .debounce(700)
            .collect { ws.save(state.root) }
    }

    // Flush on tab close: the 700ms debounce would otherwise drop the last edit.
    // Only save if the design actually diverged, so closing an untouched new design creates no file.
    DisposableEffect(state, ws) {
        val flush: (Event) -> Unit = { if (state.root != ws.initialDesign) ws.save(state.root) }
        window.addEventListener("beforeunload", flush)
        onDispose { window.removeEventListener("beforeunload", flush) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tk.appBg)
            .padding(Tk.gap)
            .focusRequester(focusRequester)
            .onKeyEvent { handleShortcut(it, state) }
            .focusable(),
        verticalArrangement = Arrangement.spacedBy(Tk.gap),
    ) {
        Toolbar(state, ws)
        ws.saveError?.let { SaveErrorBanner(it, ws::dismissSaveError) }
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Tk.gap),
        ) {
            Island(Modifier.width(320.dp).fillMaxHeight()) { TreeView(state) }
            Island(
                Modifier.weight(1f).fillMaxHeight().then(
                    // On any canvas press, reclaim editor focus (Initial pass, no consume) so
                    // keyboard shortcuts work even after clicking the same already-selected node.
                    if (state.showCode) Modifier else Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            runCatching { focusRequester.requestFocus() }
                        }
                    }
                ),
                color = if (state.showCode) Tk.codeBg else Tk.canvasBg,
            ) {
                if (state.showCode) CodePanel(state) else Canvas(state)
            }
            Island(Modifier.width(320.dp).fillMaxHeight()) { Inspector(state) }
        }
    }
}

/**
 * Three-zone top bar (left: brand menu · editable title · save status; center: view
 * switch; right: history, theme, export, account) — the layout professional design tools
 * use. The center segmented control is absolutely centered regardless of side widths.
 */
@Composable
private fun Toolbar(state: EditorState, ws: Workspace) {
    Box(modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp)) {
        // LEFT — brand/main menu, editable project title, live save status
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LogoMenu(state, ws)
            ProjectTitle(ws)
            SaveStatusChip(ws)
        }

        // CENTER — primary view switch
        Box(Modifier.align(Alignment.Center)) { ViewSwitch(state) }

        // RIGHT — history, theme, export CTA, account
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TopIconButton(AppIconKind.Undo, enabled = state.canUndo, onClick = state::undo)
            TopIconButton(AppIconKind.Redo, enabled = state.canRedo, onClick = state::redo)
            TopDivider()
            TopIconButton(if (Theme.isDark) AppIconKind.Sun else AppIconKind.Moon, onClick = Theme::toggle)
            ExportMenu(state)
            AccountChip()
        }
    }
}

/** Accent brand mark that opens the main (document) menu — Figma-style. */
@Composable
private fun LogoMenu(state: EditorState, ws: Workspace) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Tk.accent)
                .clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            BasicText("C", style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold))
        }
        TkMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuItem("New design") { ws.newDesign(); open = false }
            MenuItem("Save") { ws.save(state.root); open = false }
            MenuItem("Import JSON…") {
                importTextFile(".json,application/json") { text ->
                    runCatching { DesignJson.decode(text) }.getOrNull()?.let(state::load)
                }
                open = false
            }
            HorizontalDivider()
            MenuItem("Back to home") { ws.home(); open = false }
        }
    }
}

/** Segmented Design | Code switch (one pill, active segment filled accent). */
@Composable
private fun ViewSwitch(state: EditorState) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Tk.rSm))
            .background(Tk.panelAlt)
            .border(1.dp, Tk.border, RoundedCornerShape(Tk.rSm))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ViewSegment("Design", AppIconKind.Design, active = !state.showCode) { state.setCodeView(false) }
        ViewSegment("Code", AppIconKind.Code, active = state.showCode) { state.setCodeView(true) }
    }
}

@Composable
private fun ViewSegment(label: String, icon: AppIconKind, active: Boolean, onClick: () -> Unit) {
    val fg = if (active) Color.White else Tk.textSecondary
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Tk.rXs))
            .background(if (active) Tk.accent else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AppIcon(icon, Modifier.size(13.dp), tint = fg)
        BasicText(label, style = TextStyle(color = fg, fontSize = 12.5.sp, fontWeight = FontWeight.Medium))
    }
}

/** Borderless hover-highlight icon button for toolbar history/theme actions. */
@Composable
private fun TopIconButton(icon: AppIconKind, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(Tk.rSm))
            .background(if (hovered && enabled) Tk.elevated else Color.Transparent)
            .hoverable(interaction, enabled)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(
            icon,
            Modifier.size(16.dp),
            tint = when {
                !enabled -> Tk.textMuted
                hovered -> Tk.textPrimary
                else -> Tk.textSecondary
            },
        )
    }
}

/** Primary Export CTA with a share icon; opens export/import options. */
@Composable
private fun ExportMenu(state: EditorState) {
    var open by remember { mutableStateOf(false) }
    Box {
        ToolButton("Export", primary = true, icon = AppIconKind.Share) { open = true }
        TkMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuItem("Export .kt") {
                downloadText("Screens.kt", CodeGen.generate(state.root), "text/plain"); open = false
            }
            MenuItem("Export JSON") {
                downloadText("composer-design.json", DesignJson.encode(state.root), "application/json"); open = false
            }
        }
    }
}

/** Mock account avatar (Guest / local workspace — matches the home page placeholder). */
@Composable
private fun AccountChip() {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Tk.accentSoft)
            .border(1.dp, Tk.border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        BasicText("G", style = TextStyle(color = Tk.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
    }
}

/** Live persistence status, like Figma's "Saved"/"Saving…". */
@Composable
private fun SaveStatusChip(ws: Workspace) {
    val (label, color) = when (ws.saveStatus) {
        SaveStatus.Saved -> "Saved" to Tk.textMuted
        SaveStatus.Saving -> "Saving…" to Tk.textSecondary
        SaveStatus.Error -> "Save failed" to Tk.danger
    }
    BasicText(label, style = TextStyle(color = color, fontSize = 11.5.sp))
}

@Composable
private fun TopDivider() {
    Box(Modifier.padding(horizontal = 2.dp)) {
        Box(Modifier.width(1.dp).height(20.dp).background(Tk.border))
    }
}

/** A full-width warning shown when a save fails, so data loss is never silent. */
@Composable
private fun SaveErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Tk.dangerSoft)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(
            message,
            modifier = Modifier.weight(1f),
            style = TextStyle(color = Tk.danger, fontSize = 13.sp),
        )
        ToolButton("Dismiss", onClick = onDismiss)
    }
}

/** Inline, borderless editable project title (hover reveals a subtle field). */
@Composable
private fun ProjectTitle(ws: Workspace) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val lit = hovered || focused
    Box(
        modifier = Modifier
            .widthIn(min = 60.dp, max = 240.dp)
            .clip(RoundedCornerShape(Tk.rSm))
            .background(if (lit) Tk.panelAlt else Color.Transparent)
            .border(1.dp, if (focused) Tk.accent else if (hovered) Tk.border else Color.Transparent, RoundedCornerShape(Tk.rSm))
            .hoverable(interaction)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = ws.currentName,
            onValueChange = { ws.currentName = it },
            singleLine = true,
            textStyle = TextStyle(color = Tk.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            cursorBrush = SolidColor(Tk.accent),
            interactionSource = interaction,
            decorationBox = { inner ->
                if (ws.currentName.isEmpty()) {
                    BasicText("Untitled", style = TextStyle(color = Tk.textMuted, fontSize = 14.sp))
                }
                inner()
            },
        )
    }
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    TkMenuItem(label, onClick = onClick)
}

/** Bounding box (dp, artboard space) of a set of screens. */
private class ContentBox(val minX: Int, val minY: Int, val w: Int, val h: Int)

private fun contentBoxOf(screens: List<Node.Composable>): ContentBox {
    val minX = screens.minOfOrNull { it.x } ?: 0
    val minY = screens.minOfOrNull { it.y } ?: 0
    val maxX = screens.maxOfOrNull { it.x + it.width } ?: 390
    val maxY = screens.maxOfOrNull { it.y + it.height } ?: 844
    return ContentBox(minX, minY, (maxX - minX).coerceAtLeast(1), (maxY - minY).coerceAtLeast(1))
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Canvas(state: EditorState, modifier: Modifier = Modifier) {
    // User zoom (1x = fit-to-canvas, up to 500x) + pan offset (px), like Figma.
    var zoom by remember { mutableStateOf(1f) }
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }

    /**
     * Anchored zoom: the content point under the anchor stays FIXED on screen, so
     * the area you're looking at never drifts away while zooming. [anchorX]/[anchorY]
     * are px offsets from the viewport center — (0,0) anchors the viewport center
     * (± buttons); the scroll handler passes the cursor position (Figma-style).
     *
     * Screen position of a content point u (from the content center):
     * `s = viewportCenter + pan + u·scale` — keeping `s` fixed across a zoom change
     * gives `pan' = anchor − (anchor − pan)·(zoom'/zoom)`.
     */
    fun applyZoom(z: Float, anchorX: Float = 0f, anchorY: Float = 0f) {
        val newZoom = z.coerceIn(0.2f, 500f)
        if (newZoom == zoom) return
        val ratio = newZoom / zoom
        panX = anchorX - (anchorX - panX) * ratio
        panY = anchorY - (anchorY - panY) * ratio
        zoom = newZoom
    }

    fun resetView() {
        zoom = 1f; panX = 0f; panY = 0f
    }

    Box(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    val delta = event.changes.firstOrNull()?.scrollDelta ?: Offset.Zero
                    val mods = event.keyboardModifiers
                    if (mods.isCtrlPressed || mods.isMetaPressed) {
                        // pinch / ctrl+scroll → zoom toward the cursor
                        if (delta.y != 0f) {
                            val pos = event.changes.firstOrNull()?.position
                            val ax = (pos?.x ?: size.width / 2f) - size.width / 2f
                            val ay = (pos?.y ?: size.height / 2f) - size.height / 2f
                            applyZoom(zoom * (1f - delta.y.coerceIn(-12f, 12f) * 0.05f), ax, ay)
                        }
                    } else {
                        // two-finger scroll → pan
                        panX -= delta.x.coerceIn(-15f, 15f) * 6f
                        panY -= delta.y.coerceIn(-15f, 15f) * 6f
                    }
                }
                // A tap on empty canvas (nothing consumed it) selects the artboard.
                .pointerInput(Unit) { detectTapGestures { state.select(state.root.id) } },
        ) {
            val density = LocalDensity.current
            val screens = state.composables
            // The content bounding box is FROZEN while screens move/resize (only the
            // screen count re-keys it) so dragging a screen never re-fits the view —
            // Figma keeps the viewport still; you pan, it doesn't jump.
            val content = remember(screens.size) { contentBoxOf(screens) }
            // Auto-fit scale (never up past 1:1), then apply the user's zoom. The
            // vertical margin clears the FLOATING CHROME: the preset badge (top) and
            // the palette (bottom) overlay the canvas and eat clicks — a frame fitted
            // under them has unreachable name label, top handles, and bottom handles.
            val marginH = 144f
            val marginV = 320f
            val fit = minOf(1f, (maxWidth.value - marginH) / content.w, (maxHeight.value - marginV) / content.h)
                .coerceAtLeast(0.05f)
            val scale = (fit * zoom).coerceIn(0.04f, 500f) // fit ≤ 1, so 500x user zoom passes uncapped

            // Shared measurement state: node bounds in the PRE-SCALE space + that
            // space's coordinates. Hoisted here so the screen-space overlay below
            // (outside the zoomed layer) can use them.
            val bounds = remember { mutableStateMapOf<String, Rect>() }
            var spaceCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
            // Prune bounds of deleted nodes so ghost rects can't win drill hit-tests.
            LaunchedEffect(state.root) {
                val stale = bounds.keys.filter { state.root.findById(it) == null }
                stale.forEach { bounds.remove(it) }
            }

            // Always-on background grid behind the frames; the per-pixel grid layers on top at high zoom.
            Box(Modifier.matchParentSize().editorGrid())
            ArtboardCanvas(
                state,
                scale,
                content,
                bounds,
                onSpaceCoords = { spaceCoords = it },
                Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(panX.roundToInt(), panY.roundToInt()) },
            )

            // Selection overlay in SCREEN space — a sibling of the zoomed layer, so
            // all its chrome (outline, handles, edge zones, dims pill) is plain dp
            // and stays crisp/hit-testable at ANY zoom. Inside the layer, /scale
            // layout sizes round to 0 px past ~150x — dead handles, no cursor.
            // The pre-scale→screen mapping mirrors the layer's transform (centered
            // + pan + scale) and READS panX/panY/scale, so it recomposes with them.
            val sel = state.selectedId
            val b = sel?.let { bounds[it] }
            if (sel != null && b != null && sel != state.root.id) {
                val isScreen = state.selected is Node.Composable
                val d = density.density
                val cw = with(density) { maxWidth.toPx() }
                val ch = with(density) { maxHeight.toPx() }
                // The layer is island-sized and scales around its center (= viewport
                // center + pan); bounds are island-space px with the content-centering
                // already baked in by ScreenFrame's placement.
                fun toScreen(x: Float, y: Float) =
                    Offset(cw / 2f + panX + (x - cw / 2f) * scale, ch / 2f + panY + (y - ch / 2f) * scale)
                val tl = toScreen(b.left, b.top)
                val br = toScreen(b.right, b.bottom)
                // key(sel) recreates the whole overlay per selection — its pointerInputs
                // capture callbacks at start, so a reused overlay would keep dragging
                // the PREVIOUSLY selected node.
                key(sel) {
                    SelectionOverlay(
                        screen = Rect(tl.x, tl.y, br.x, br.y),
                        viewport = Size(cw, ch),
                        dimsLabel = "${with(density) { b.width.toDp().value }.roundToInt()} × ${with(density) { b.height.toDp().value }.roundToInt()}",
                        density = density,
                        cornerPx = (if (isScreen) 0 else state.selected?.backgroundCorner() ?: 0) * d * scale,
                        scale = scale,
                        frameCoords = spaceCoords,
                        // Screens float freely on the artboard: resizing from a top/left
                        // handle moves them so the opposite edge stays pinned. Components
                        // are LAYOUT children (their position is the parent's business,
                        // like Figma auto-layout items) — corners/edges only resize.
                        anchorMove = isScreen,
                        onMove = { dx, dy ->
                            if (isScreen) state.moveComposable(sel, dx, dy) else state.offsetNode(sel, dx, dy)
                        },
                        onResize = { dw, dh ->
                            if (isScreen) {
                                state.resizeComposable(sel, dw, dh)
                            } else {
                                val baseW = with(density) { b.width.toDp().value }.roundToInt()
                                val baseH = with(density) { b.height.toDp().value }.roundToInt()
                                state.resizeNode(sel, baseW, baseH, dw, dh)
                            }
                        },
                        onDrill = { frame ->
                            // `frame` is in the pre-scale space (same space as the bounds
                            // map); drill into the smallest (deepest) node containing it.
                            val hit = bounds.entries
                                .filter { (id, r) -> id != state.root.id && frame.x >= r.left && frame.x <= r.right && frame.y >= r.top && frame.y <= r.bottom }
                                .minByOrNull { (_, r) -> r.width * r.height }
                            if (hit != null) state.selectAt(hit.key, deep = true)
                        },
                    )
                }
            }
        }
        SizeBadge(state, Modifier.align(Alignment.TopStart).padding(12.dp))
        ZoomBadge(zoom, onZoom = { applyZoom(it) }, onReset = ::resetView, Modifier.align(Alignment.TopEnd).padding(12.dp))
        FloatingPalette(state, Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp))
    }
}

/** "1x" = fit-to-canvas; deep zooms read as a multiplier ("500x"), not "50000%". */
private fun zoomLabel(zoom: Float): String {
    if (zoom >= 10f) return "${zoom.roundToInt()}x"
    val tenths = (zoom * 10).roundToInt()
    return if (tenths % 10 == 0) "${tenths / 10}x" else "${tenths / 10}.${tenths % 10}x"
}

@Composable
private fun ZoomBadge(zoom: Float, onZoom: (Float) -> Unit, onReset: () -> Unit, modifier: Modifier = Modifier) {
    Island(modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ToolButton("−") { onZoom(zoom / 1.2f) }
            ToolButton(zoomLabel(zoom), onClick = onReset)
            ToolButton("+") { onZoom(zoom * 1.2f) }
            // Fit to screen: back to 1x zoom, centered.
            ToolButton("", icon = AppIconKind.Fit, onClick = onReset)
        }
    }
}

private class FramePreset(val name: String, val w: Int, val h: Int)

private val framePresets = listOf(
    FramePreset("Mobile", 390, 844),
    FramePreset("Tablet", 820, 1180),
    FramePreset("Desktop", 1440, 900),
)

/**
 * Device presets + live size for the screen the selection lives in (falls back
 * to the first screen), plus an "add screen" action.
 */
@Composable
private fun SizeBadge(state: EditorState, modifier: Modifier = Modifier) {
    val screen = state.selectedId?.let { state.screenOf(it) } ?: state.composables.firstOrNull()
    Island(modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ToolButton("Composable", icon = AppIconKind.Plus) { state.addComposable() }
            if (screen != null) {
                Box(Modifier.width(1.dp).height(20.dp).padding(horizontal = 2.dp).background(Tk.border))
                for (p in framePresets) {
                    ToolButton(p.name, primary = screen.width == p.w && screen.height == p.h) {
                        state.setComposableSize(screen.id, p.w, p.h)
                    }
                }
                Box(Modifier.width(1.dp).height(20.dp).padding(horizontal = 2.dp).background(Tk.border))
                BasicText(
                    "${screen.width} × ${screen.height}",
                    style = TextStyle(color = Tk.textSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
        }
    }
}

private val windowShape = RectangleShape

/**
 * The artboard canvas: every [Node.Composable] screen rendered as its own frame
 * at its (x, y) position, inside ONE zoomed layer and ONE shared coordinate
 * space — so the bounds map and hit-testing work across screens. The selection
 * overlay lives OUTSIDE this layer (screen space, see [Canvas]) so its chrome
 * survives extreme zoom.
 */
@Composable
private fun ArtboardCanvas(
    state: EditorState,
    scale: Float,
    content: ContentBox,
    bounds: MutableMap<String, Rect>,
    onSpaceCoords: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    var spaceCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // The zoomed layer is ISLAND-SIZED (fillMaxSize), never sized to the content:
    // Modifier.size silently COERCES to the parent's max constraints, so a content
    // box wider/taller than the island (e.g. 3 mobile screens, or one desktop
    // screen) got clamped — shifting the scale origin and breaking every screen
    // -> overlay mapping. Content is centered arithmetically inside instead, and
    // each frame is placed by an unclampable custom layout (see ScreenFrame).
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .fillMaxSize(),
    ) {
        // Reference box: the shared coordinate space for the bounds measurement.
        // Not clipped — screens may extend past the island box.
        Box(
            modifier = Modifier.fillMaxSize().onGloballyPositioned {
                spaceCoords = it
                onSpaceCoords(it)
            },
        ) {
            for (screen in state.composables) {
                key(screen.id) {
                    ScreenFrame(state, screen, scale, content, spaceCoords, bounds)
                }
            }
        }
    }
}

/**
 * One screen's frame: themed surface + preview at the screen's artboard position,
 * with a Figma-style clickable name label above it. The label shows the layer
 * name — which is also the generated function name.
 */
@Composable
private fun ScreenFrame(
    state: EditorState,
    screen: Node.Composable,
    scale: Float,
    content: ContentBox,
    spaceCoords: LayoutCoordinates?,
    bounds: MutableMap<String, Rect>,
) {
    fun register(id: String, coords: LayoutCoordinates) {
        spaceCoords?.let { sc ->
            val tl = sc.localPositionOf(coords, Offset.Zero)
            bounds[id] = Rect(tl.x, tl.y, tl.x + coords.size.width, tl.y + coords.size.height)
        }
    }
    Box(
        modifier = Modifier
            // Unclampable placement: measure the frame at its EXACT size (a screen
            // can be larger than the island — Modifier.size would silently coerce)
            // and place it so the frozen content box is centered in the island.
            .layout { measurable, constraints ->
                val w = screen.width.dp.roundToPx()
                val h = screen.height.dp.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(w, h))
                val ox = ((constraints.maxWidth - content.w.dp.toPx()) / 2f + (screen.x - content.minX).dp.toPx()).roundToInt()
                val oy = ((constraints.maxHeight - content.h.dp.toPx()) / 2f + (screen.y - content.minY).dp.toPx()).roundToInt()
                layout(0, 0) { placeable.place(ox, oy) }
            }
            .onGloballyPositioned { register(screen.id, it) },
    ) {
        // The design's own theme wraps the preview (WYSIWYG with the generated
        // MaterialTheme). The frame ALWAYS paints the theme background — that's
        // what the generated app shows — so it reads as a real surface on the canvas
        // (Figma-style), never as transparent grid. pixelGrid is a drawWithContent
        // OVERLAY (children first, grid on top) so component fills can't cover it;
        // it sits after .clip() so the grid stays clipped to the frame.
        MaterialTheme(colorScheme = state.theme.toColorScheme()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(windowShape)
                    .background(MaterialTheme.colorScheme.background)
                    .pixelGrid(scale)
                    // A tap on the screen's empty area (no child consumed it) selects the screen.
                    .pointerInput(screen.id) { detectTapGestures { state.select(screen.id) } },
            ) {
                RenderNode(screen, state.selectedId, onSelect = state::selectAt, onBounds = ::register)
            }
        }

        // Screen name label (also the generated @Composable function name).
        // /scale-compensated to a constant on-screen size at any zoom.
        val selectedHere = state.selectedId == screen.id
        BasicText(
            text = state.layerName(screen.id) ?: "Composable",
            style = TextStyle(
                color = if (selectedHere) Tk.accent else Tk.textSecondary,
                fontSize = (11f / scale).sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(y = -(20f / scale).dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { state.select(screen.id) },
        )
    }
}

/**
 * Move grip + resize handles laid over the selected node — in SCREEN space
 * (a sibling of the zoomed layer). The overlay is a FULL-CANVAS box and every
 * piece of chrome is positioned at the [screen] rect but CLIPPED to the
 * [viewport]: at deep zoom the selection rect can be hundreds of thousands of
 * px, far beyond what Compose layout constraints can represent — a box sized
 * to the selection gets silently clamped to the parent (it rendered as a flat
 * line). Clipping keeps every layout node viewport-sized at ANY zoom, while
 * the outline itself is a float-px draw (no layout limits).
 * [scale] converts drag travel (window px) into design dp; [frameCoords]
 * (the pre-scale space) converts drill taps for hit-testing.
 */
@Composable
private fun SelectionOverlay(
    screen: Rect,
    viewport: Size,
    dimsLabel: String,
    density: Density,
    cornerPx: Float,
    scale: Float,
    frameCoords: LayoutCoordinates?,
    anchorMove: Boolean,
    onMove: (Int, Int) -> Unit,
    onResize: (Int, Int) -> Unit,
    onDrill: (Offset) -> Unit,
) {
    // The body's real layout coordinates — used to convert a tap to frame space exactly,
    // instead of deriving it from `screen` (which drifted from the body's actual placement).
    var bodyCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val accent = Tk.accent
    val onePx = with(density) { 1.dp.toPx() }
    val gap = onePx // 1dp — pushes the outline OUTSIDE the component
    val outer = Rect(screen.left - gap, screen.top - gap, screen.right + gap, screen.bottom + gap)
    fun pxToDp(v: Float) = with(density) { v.toDp() }

    Box(Modifier.fillMaxSize()) {
        // Outline: drawn over the full canvas in float px — huge rects are fine here.
        Box(
            Modifier.matchParentSize().drawBehind {
                val sw = onePx
                val inset = sw / 2f
                val tl = Offset(outer.left + inset, outer.top + inset)
                val sz = Size(outer.width - sw, outer.height - sw)
                if (cornerPx > 0f) {
                    val r = (cornerPx + gap - inset).coerceAtLeast(0f)
                    drawRoundRect(accent, topLeft = tl, size = sz, cornerRadius = CornerRadius(r), style = Stroke(sw))
                } else {
                    drawRect(accent, topLeft = tl, size = sz, style = Stroke(sw))
                }
            },
        )

        // Body (move/drill): the selection ∩ viewport, so its layout stays small.
        // A tap drills one level deeper into the child under the cursor; drag moves
        // the node (5dp dead-zone so jitter clicks don't write an Offset; below the
        // threshold nothing is consumed and the tap detector sees a clean tap).
        val bl = screen.left.coerceIn(0f, viewport.width)
        val bt = screen.top.coerceIn(0f, viewport.height)
        val br = screen.right.coerceIn(0f, viewport.width)
        val bb = screen.bottom.coerceIn(0f, viewport.height)
        if (br - bl >= 1f && bb - bt >= 1f) {
            Box(
                Modifier
                    .offset { IntOffset(bl.roundToInt(), bt.roundToInt()) }
                    .size(pxToDp(br - bl), pxToDp(bb - bt))
                    .onGloballyPositioned { bodyCoords = it }
                    .pointerInput(frameCoords) {
                        detectTapGestures(onTap = { local ->
                            val fc = frameCoords
                            val bc = bodyCoords
                            if (fc != null && bc != null) onDrill(fc.localPositionOf(bc, local))
                        })
                    }
                    .windowAnchoredDrag(scale, { bodyCoords }, thresholdDp = 5f) { dx, dy -> onMove(dx, dy) },
            )
        }

        // Figma-style resize chrome: invisible EDGE zones (straddling the outline,
        // browser resize cursor on hover) + four white corner squares — each placed
        // only along its edge's VISIBLE segment. When [anchorMove] (free-floating
        // screens), sides/corners anchored at the top/left invert the delta and move
        // the node the same amount so the opposite edge stays pinned; layout children
        // only resize.
        val thick = 10f * onePx // edge hit thickness (10dp), straddling the outline
        val vy0 = outer.top.coerceAtLeast(0f)
        val vy1 = outer.bottom.coerceAtMost(viewport.height)
        val vx0 = outer.left.coerceAtLeast(0f)
        val vx1 = outer.right.coerceAtMost(viewport.width)

        @Composable
        fun edge(x: Float, y: Float, w: Float, h: Float, cursor: String, onDelta: (Int, Int) -> Unit) {
            if (w < 1f || h < 1f) return
            ResizeEdge(
                Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .size(pxToDp(w), pxToDp(h)),
                cursor, scale, onDelta,
            )
        }
        if (outer.right >= -thick && outer.right <= viewport.width + thick) {
            edge(outer.right - thick / 2f, vy0, thick, vy1 - vy0, "ew-resize") { dx, _ -> onResize(dx, 0) }
        }
        if (outer.left >= -thick && outer.left <= viewport.width + thick) {
            edge(outer.left - thick / 2f, vy0, thick, vy1 - vy0, "ew-resize") { dx, _ ->
                onResize(-dx, 0); if (anchorMove) onMove(dx, 0)
            }
        }
        if (outer.bottom >= -thick && outer.bottom <= viewport.height + thick) {
            edge(vx0, outer.bottom - thick / 2f, vx1 - vx0, thick, "ns-resize") { _, dy -> onResize(0, dy) }
        }
        if (outer.top >= -thick && outer.top <= viewport.height + thick) {
            edge(vx0, outer.top - thick / 2f, vx1 - vx0, thick, "ns-resize") { _, dy ->
                onResize(0, -dy); if (anchorMove) onMove(0, dy)
            }
        }

        val cornerHalf = 8f * onePx // half of the 16dp corner hit box
        @Composable
        fun corner(cx: Float, cy: Float, cursor: String, onDelta: (Int, Int) -> Unit) {
            if (cx < -cornerHalf || cx > viewport.width + cornerHalf) return
            if (cy < -cornerHalf || cy > viewport.height + cornerHalf) return
            CornerHandle(
                Modifier.offset { IntOffset((cx - cornerHalf).roundToInt(), (cy - cornerHalf).roundToInt()) },
                cursor, scale, onDelta,
            )
        }
        corner(outer.left, outer.top, "nwse-resize") { dx, dy ->
            onResize(-dx, -dy); if (anchorMove) onMove(dx, dy)
        }
        corner(outer.right, outer.top, "nesw-resize") { dx, dy ->
            onResize(dx, -dy); if (anchorMove) onMove(0, dy)
        }
        corner(outer.left, outer.bottom, "nesw-resize") { dx, dy ->
            onResize(-dx, dy); if (anchorMove) onMove(dx, 0)
        }
        corner(outer.right, outer.bottom, "nwse-resize") { dx, dy ->
            onResize(dx, dy)
        }

        // Figma-style dimensions pill under the selection's bottom edge (when that
        // edge is on screen); centered on the visible span of the selection.
        if (outer.bottom >= 0f && outer.bottom <= viewport.height - 12f * onePx) {
            val cx = ((vx0 + vx1) / 2f).coerceIn(60f * onePx, viewport.width - 60f * onePx)
            Box(
                modifier = Modifier
                    .offset { IntOffset((cx - 60f * onePx).roundToInt(), (outer.bottom + 8f * onePx).roundToInt()) }
                    .size(pxToDp(120f * onePx), pxToDp(24f * onePx)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(accent)
                        .padding(horizontal = 6.dp, vertical = 2.5.dp),
                ) {
                    BasicText(
                        dimsLabel,
                        style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Window-anchored drag for chrome that MOVES while its own drag is applied (resize
 * handles ride the growing box; the move grip rides the moving node). Per-event
 * `positionChange()` deltas on a moving target lag layout by one frame — fast drags
 * rubber-band and stutter. Instead, the TOTAL pointer travel is measured in WINDOW
 * space (stable during the gesture, unaffected by the target moving underneath),
 * divided by [scale] into design dp, and emitted as integer diffs of the rounded
 * total — so the geometry stays locked to the cursor at any zoom with no drift.
 *
 * [thresholdDp] adds a screen-space dead-zone before the drag starts; below it no
 * change is consumed, so a sibling tap detector still sees a clean tap. On crossing,
 * the accumulated travel is applied at once so the target doesn't lag the cursor.
 */
private fun Modifier.windowAnchoredDrag(
    scale: Float,
    coords: () -> LayoutCoordinates?,
    thresholdDp: Float = 0f,
    onStart: () -> Unit = {},
    onEnd: () -> Unit = {},
    onDelta: (Int, Int) -> Unit,
): Modifier = pointerInput(scale) {
    val thresholdPx = thresholdDp.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // Someone above us (e.g. a resize handle overlapping the move grip)
        // already owns this gesture — leave it alone.
        if (down.isConsumed) return@awaitEachGesture
        val start = coords()?.localToWindow(down.position) ?: return@awaitEachGesture
        var dragging = thresholdPx <= 0f
        if (dragging) {
            down.consume()
            onStart()
        }
        var emittedX = 0
        var emittedY = 0
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (!dragging && change.isConsumed) break // claimed by another gesture mid-flight
                val cur = coords()?.localToWindow(change.position) ?: break
                val dxWin = cur.x - start.x
                val dyWin = cur.y - start.y
                if (!dragging && dxWin * dxWin + dyWin * dyWin > thresholdPx * thresholdPx) {
                    dragging = true
                    onStart()
                }
                if (dragging) {
                    change.consume()
                    val dx = (dxWin / scale).toDp().value.roundToInt() - emittedX
                    val dy = (dyWin / scale).toDp().value.roundToInt() - emittedY
                    if (dx != 0 || dy != 0) {
                        emittedX += dx
                        emittedY += dy
                        onDelta(dx, dy)
                    }
                }
            }
        } finally {
            if (dragging) onEnd()
        }
    }
}

/**
 * Invisible resize zone along one edge of the selection. Shows the matching
 * browser resize [cursor] on hover (locked while dragging), and drags via
 * [windowAnchoredDrag] so tracking stays cursor-exact.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ResizeEdge(modifier: Modifier, cursor: String, scale: Float, onDelta: (Int, Int) -> Unit) {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var dragging by remember { mutableStateOf(false) }
    // pointerInput captures its lambdas once; route through state so recompositions
    // with fresh callbacks (changed bounds/selection context) stay live mid-gesture.
    val currentOnDelta by rememberUpdatedState(onDelta)
    Box(
        modifier
            .onGloballyPositioned { coords = it }
            .onPointerEvent(PointerEventType.Enter) { setCanvasCursor(cursor) }
            .onPointerEvent(PointerEventType.Exit) { if (!dragging) setCanvasCursor("default") }
            .windowAnchoredDrag(
                scale, { coords },
                onStart = { dragging = true; setCanvasCursor(cursor) },
                onEnd = { dragging = false; setCanvasCursor("default") },
                onDelta = { dx, dy -> currentOnDelta(dx, dy) },
            ),
    )
}

/**
 * Figma-style corner handle: a small white square with an accent border, with a
 * generous invisible hit area and a diagonal resize cursor.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CornerHandle(modifier: Modifier, cursor: String, scale: Float, onDelta: (Int, Int) -> Unit) {
    val accent = Tk.accent
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var dragging by remember { mutableStateOf(false) }
    val currentOnDelta by rememberUpdatedState(onDelta)
    Box(
        modifier = modifier
            .size(16.dp) // hit area; the visible square is drawn centered
            .onGloballyPositioned { coords = it }
            .onPointerEvent(PointerEventType.Enter) { setCanvasCursor(cursor) }
            .onPointerEvent(PointerEventType.Exit) { if (!dragging) setCanvasCursor("default") }
            .windowAnchoredDrag(
                scale, { coords },
                onStart = { dragging = true; setCanvasCursor(cursor) },
                onEnd = { dragging = false; setCanvasCursor("default") },
                onDelta = { dx, dy -> currentOnDelta(dx, dy) },
            )
            .drawBehind {
                // 7dp white square + 1.25dp accent border (screen-space; plain dp at any zoom).
                val s = 7.dp.toPx()
                val bw = 1.25.dp.toPx()
                val tl = Offset((size.width - s) / 2f, (size.height - s) / 2f)
                drawRect(Color.White, topLeft = tl, size = Size(s, s))
                drawRect(accent, topLeft = tl, size = Size(s, s), style = Stroke(bw))
            },
    )
}

// Zoom thresholds for the per-pixel grid fade-in (scale = fit × user-zoom).
// Fade window tuned for real use: most zoom work happens at 200–300%, so the grid
// starts appearing right past 2x and is fully visible by 3x. (A 2.5→4x window left
// the grid at near-zero alpha across the whole working range — "invisible grid".)
private const val GRID_PIXEL_FADE_START = 2f  // per-pixel grid begins fading in
private const val GRID_PIXEL_FULL = 3f        // per-pixel grid fully visible

/**
 * Subtle 12dp line grid behind the canvas for a design-surface feel. Drawn on the
 * static container (not the zoomed frame), so its spacing never scales with zoom.
 */
private fun Modifier.editorGrid(): Modifier = drawBehind {
    val step = 12.dp.toPx()
    val sw = 1f // hairline, constant
    val color = Tk.canvasDot
    var x = step
    while (x < size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = sw)
        x += step
    }
    var y = step
    while (y < size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = sw)
        y += step
    }
}

/**
 * Per-pixel grid: one cell = one design unit (1.dp), drawn in the frame's local space so
 * the zoom [graphicsLayer] scales the cells with zoom (like Figma's pixel grid). Drawn as
 * an OVERLAY via `drawWithContent` — content first, grid on top — so component fills can't
 * cover it (a `drawBehind` grid vanished under any filled node). Only appears once zoomed
 * in past [GRID_PIXEL_FADE_START]. The stroke is `/scale`-compensated to a constant ~1px
 * hairline on screen, cells align to the frame's pixel origin, and the color is a neutral
 * translucent gray so it reads over both light and dark fills.
 */
private fun Modifier.pixelGrid(scale: Float): Modifier = drawWithContent {
    drawContent()
    if (scale < GRID_PIXEL_FADE_START) return@drawWithContent
    val alpha = ((scale - GRID_PIXEL_FADE_START) / (GRID_PIXEL_FULL - GRID_PIXEL_FADE_START)).coerceIn(0f, 1f)
    if (alpha <= 0f) return@drawWithContent
    val cell = 1.dp.toPx()          // 1 design unit; the layer scales it to `scale`-dp on screen
    val sw = 1.dp.toPx() / scale    // constant ~1dp hairline on screen at any zoom
    val color = Color(0xFF808080).copy(alpha = 0.45f * alpha)
    var x = cell
    while (x < size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = sw)
        x += cell
    }
    var y = cell
    while (y < size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = sw)
        y += cell
    }
}

@Composable
private fun jetBrainsMono(): FontFamily = FontFamily(Font(Res.font.jetbrainsmono_regular))

@Composable
private fun CodePanel(state: EditorState, modifier: Modifier = Modifier) {
    val code = remember(state.root) { CodeGen.generate(state.root) }
    val highlighted = remember(code, Theme.isDark) { highlightKotlin(code, Theme.isDark) }
    val codeFont = jetBrainsMono()
    val lineCount = remember(code) { code.count { it == '\n' } + 1 }
    val gutter = remember(lineCount) {
        val w = lineCount.toString().length
        (1..lineCount).joinToString("\n") { it.toString().padStart(w) }
    }
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val codeStyle = TextStyle(color = Tk.codeText, fontFamily = codeFont, fontSize = 12.5.sp, lineHeight = 19.sp)
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                "Screens.kt",
                style = TextStyle(color = Tk.textSecondary, fontSize = 12.sp, fontFamily = codeFont),
                modifier = Modifier.weight(1f),
            )
            ToolButton("Copy", onClick = { copyToClipboard(code) })
        }
        HDivider(Modifier.background(Tk.border))
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // Line-number gutter — shares [vScroll] with the code so they scroll together.
            BasicText(
                text = gutter,
                style = codeStyle.copy(color = Tk.textMuted),
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(vScroll)
                    .padding(start = 14.dp, end = 10.dp, top = 16.dp, bottom = 16.dp),
            )
            Box(Modifier.fillMaxHeight().width(1.dp).background(Tk.border))
            BasicText(
                text = highlighted,
                style = codeStyle,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(vScroll)
                    .horizontalScroll(hScroll)
                    .padding(horizontal = 14.dp, vertical = 16.dp),
            )
        }
    }
}

/**
 * Global keyboard shortcuts. Runs as `onKeyEvent` (bubbling), so a focused text
 * field consumes its own keystrokes first — Delete/Backspace only deletes a node
 * when the canvas (not an inspector field) has focus. Returns true when handled.
 */
private fun handleShortcut(event: KeyEvent, state: EditorState): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val cmd = event.isMetaPressed || event.isCtrlPressed
    return when {
        cmd && event.key == Key.Z && event.isShiftPressed -> {
            state.redo(); true
        }

        cmd && event.key == Key.Z -> {
            state.undo(); true
        }

        cmd && event.key == Key.Y -> {
            state.redo(); true
        }

        cmd && event.key == Key.C -> {
            state.copy(); true
        }

        cmd && event.key == Key.V -> {
            state.paste(); true
        }

        cmd && event.key == Key.D -> {
            state.duplicate(); true
        }

        event.key == Key.Delete || event.key == Key.Backspace -> {
            state.selectedId?.let { state.delete(it) } != null
        }

        event.key == Key.Escape -> {
            state.clearSelection(); true
        }

        // Arrow-key nudge of the selected component (Shift = ×10). A focused inspector
        // field consumes arrows first (cursor movement), so this only fires on the canvas.
        event.key == Key.DirectionLeft || event.key == Key.DirectionRight ||
            event.key == Key.DirectionUp || event.key == Key.DirectionDown -> {
            val id = state.selectedId
            if (id == null || id == state.root.id) false
            else {
                val step = if (event.isShiftPressed) 10 else 1
                val (dx, dy) = when (event.key) {
                    Key.DirectionLeft -> -step to 0
                    Key.DirectionRight -> step to 0
                    Key.DirectionUp -> 0 to -step
                    else -> 0 to step
                }
                // Nudge a screen on the artboard canvas; a component via its Offset modifier.
                if (state.root.findById(id) is Node.Composable) state.moveComposable(id, dx, dy)
                else state.offsetNode(id, dx, dy)
                true
            }
        }

        else -> false
    }
}
