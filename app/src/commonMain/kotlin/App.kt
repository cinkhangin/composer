package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
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
import androidx.compose.ui.unit.dp
import composer.model.DesignJson
import composer.model.DesignTheme
import composer.model.ThemeColorRef
import composer.model.Node
import composer.model.childNodes
import composer.model.findById
import composer.model.isShape
import composer.ui.AppIconKind
import composer.ui.LocalThemeSwatches
import composer.ui.ThemeSwatch
import composer.ui.Tk

/**
 * Android Studio designer shell. All panes are projections of one
 * [EditorState.root], while [session] owns synchronization with the IDE.
 */
@OptIn(FlowPreview::class)
@Composable
internal fun EditorScreen(session: DesignerSession) {
    // The IDE must never show a website/new-document sample as if it came from
    // the open project. The host replaces this blank tree during its ready
    // handshake (or after the first source parse completes).
    val state = remember(session) { EditorState(Node.Artboard(id = "root")) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    // Reclaim keyboard focus for the editor whenever the selection changes (e.g. after
    // clicking a component on the canvas or a layer in the tree), so keyboard shortcuts —
    // arrow-key nudge, Delete, copy/paste — keep working after touching an inspector field.
    LaunchedEffect(state.selectedId) {
        if (state.selectedId != null) runCatching { focusRequester.requestFocus() }
    }

    DisposableEffect(session, state) {
        session.onLoadDesign = { tree ->
            state.loadExternal(tree)
            session.noteLoaded(DesignJson.encode(state.root))
        }
        session.onSelectNode = { id ->
            val node = state.root.findById(id)
            if (node != null && node !is Node.RawCode) state.select(id)
        }
        onDispose {
            session.onLoadDesign = null
            session.onSelectNode = null
        }
    }
    LaunchedEffect(session) { session.start() }
    LaunchedEffect(session, state) {
        snapshotFlow { state.root }
            .drop(1)
            .debounce(300)
            .collect { session.postDesign(DesignJson.encode(state.root)) }
    }
    LaunchedEffect(session, state) {
        snapshotFlow { state.selectedId }
            .drop(1)
            .debounce(100)
            .collect { session.postSelection(it) }
    }

    // Every ColorPicker in the editor offers the ACTIVE theme's tokens as picks
    // (stored as ThemeColorRef references, so they follow theme switches).
    val themeSwatches = DesignTheme.TOKENS.map { t ->
        ThemeSwatch(t, ThemeColorRef.token(t)!!, state.theme.effective(t))
    }
    CompositionLocalProvider(LocalThemeSwatches provides themeSwatches) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tk.panel)
            .focusRequester(focusRequester)
            .onKeyEvent {
                handleShortcut(
                    event = it,
                    state = state,
                    lockComposableStructure = session.appMode,
                )
            }
            .focusable(),
    ) {
        Toolbar(
            state,
            showNewComposable = session.appMode,
            onNewComposable = session::requestNewComposable,
        )
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            if (state.leftPanelOpen) {
                Column(Modifier.width(232.dp).fillMaxHeight().workspaceSurface(divider = WorkspaceDivider.Right)) {
                    TreeView(state, onCollapse = state::toggleLeftPanel)
                }
            } else {
                CollapsedPanelStrip(
                    AppIconKind.ExpandLeft,
                    tip = "Show layers",
                    divider = WorkspaceDivider.Right,
                    onExpand = state::toggleLeftPanel,
                )
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().workspaceSurface(color = Tk.canvasBg).then(
                    // On any canvas press, reclaim editor focus (Initial pass, no consume) so
                    // keyboard shortcuts work even after clicking the same already-selected node.
                    Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            runCatching { focusRequester.requestFocus() }
                        }
                    }
                ),
            ) {
                Canvas(
                    state = state,
                    appMode = session.appMode,
                    magnification = session.canvasMagnification,
                )
            }
            if (state.rightPanelOpen) {
                Column(Modifier.width(232.dp).fillMaxHeight().workspaceSurface(divider = WorkspaceDivider.Left)) {
                    Inspector(
                        state,
                        onCollapse = state::toggleRightPanel,
                        lockComposableStructure = session.appMode,
                    )
                }
            } else {
                CollapsedPanelStrip(
                    AppIconKind.ExpandRight,
                    tip = "Show inspector",
                    divider = WorkspaceDivider.Left,
                    onExpand = state::toggleRightPanel,
                )
            }
        }
    }
    }
}

/** Bounding box (dp, artboard space) of a set of screens. */
internal class ContentBox(val minX: Int, val minY: Int, val w: Int, val h: Int)

internal fun contentBoxOf(screens: List<Node.Composable>): ContentBox {
    val minX = screens.minOfOrNull { it.x } ?: 0
    val minY = screens.minOfOrNull { it.y } ?: 0
    val maxX = screens.maxOfOrNull { it.x + it.width } ?: 390
    val maxY = screens.maxOfOrNull { it.y + it.height } ?: 844
    return ContentBox(minX, minY, (maxX - minX).coerceAtLeast(1), (maxY - minY).coerceAtLeast(1))
}

// Effective-scale limits: absolute magnification, independent of the fitted scale.
internal const val MIN_SCALE = 0.02f
internal const val MAX_SCALE = 500f

/** True magnification: "1x" = 1 design dp per screen dp; deep zooms read as a multiplier ("500x"), not "50000%". */
internal fun zoomLabel(zoom: Float): String {
    if (zoom >= 10f) return "${zoom.roundToInt()}x"
    if (zoom >= 0.95f) {
        val tenths = (zoom * 10).roundToInt()
        return if (tenths % 10 == 0) "${tenths / 10}x" else "${tenths / 10}.${tenths % 10}x"
    }
    // Below 1x a single tenth is too coarse (a fitted view is often 0.0x-something).
    val hundredths = (zoom * 100).roundToInt().coerceAtLeast(1)
    return if (hundredths % 10 == 0) "0.${hundredths / 10}x" else "0.${hundredths.toString().padStart(2, '0')}x"
}

/** All node ids in the subtree (slots included) — for pruning stale bounds. */
internal fun collectIds(node: Node, out: MutableSet<String>) {
    out.add(node.id)
    for (child in node.childNodes()) collectIds(child, out)
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
internal fun Modifier.windowAnchoredDrag(
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
internal fun Modifier.editorGrid(): Modifier = drawBehind {
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
internal fun Modifier.pixelGrid(scale: Float): Modifier = drawWithContent {
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

/**
 * Global keyboard shortcuts. Runs as `onKeyEvent` (bubbling), so a focused text
 * field consumes its own keystrokes first — Delete/Backspace only deletes a node
 * when the canvas (not an inspector field) has focus. Returns true when handled.
 * The code editor owns the keyboard entirely while focused: text fields don't
 * consume non-editing keys (⌘D, ⌘Y, Escape) and those firing design actions
 * mid-typing would be destructive.
 */
internal fun handleShortcut(
    event: KeyEvent,
    state: EditorState,
    lockComposableStructure: Boolean = false,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (state.codeEditorFocused) return false
    val cmd = event.isMetaPressed || event.isCtrlPressed
    val selectedFunctionIsLocked = lockComposableStructure && state.selected is Node.Composable
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
            if (!selectedFunctionIsLocked) state.duplicate()
            true
        }

        event.key == Key.Delete || event.key == Key.Backspace -> {
            if (selectedFunctionIsLocked) true
            else state.selectedId?.let { state.delete(it) } != null
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
                // Nudge a screen on the artboard canvas; a shape via its model
                // coords; a component via its Offset modifier.
                val node = state.root.findById(id)
                when {
                    node is Node.Composable -> state.moveComposable(id, dx, dy)
                    node?.isShape() == true -> state.moveShape(id, dx, dy)
                    else -> state.offsetNode(id, dx, dy)
                }
                true
            }
        }

        else -> false
    }
}
