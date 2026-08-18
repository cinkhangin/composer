package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.exp
import kotlin.math.roundToInt
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.dp
import composer.model.Node
import composer.model.CornerUnit
import composer.model.backgroundCorner
import composer.model.findById
import composer.model.isShape

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun Canvas(
    state: EditorState,
    appMode: Boolean,
    magnification: CanvasMagnification?,
    modifier: Modifier = Modifier,
) {
    // User zoom (multiplier on the fitted view) + pan offset (px), like Figma.
    // The EFFECTIVE scale is fit * zoom — that's what the badge shows and what
    // the limits below apply to, so "500x" is the same true magnification
    // whether the design fits at 0.1 or 1.
    var zoom by remember { mutableStateOf(1f) }
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }
    // Fit-to-canvas scale, hoisted from the layout pass below so the zoom badge
    // and clamp (both outside BoxWithConstraints' scope) can read it.
    var fitScale by remember { mutableStateOf(1f) }
    // Cursor position over the island (px), for the Figma-style hover outline.
    // Null while a button is down (mid-drag/click) or the pointer is outside.
    var hoverPos by remember { mutableStateOf<Offset?>(null) }
    var viewportSize by remember { mutableStateOf(Size.Zero) }
    var appliedMagnificationLog by remember { mutableStateOf(magnification?.logScale ?: 0.0) }

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
        // Clamp the EFFECTIVE scale (fit * zoom) to [MIN_SCALE, MAX_SCALE] —
        // an absolute magnification ceiling, independent of how small fit is.
        val newZoom = z.coerceIn(MIN_SCALE / fitScale, MAX_SCALE / fitScale)
        if (newZoom == zoom) return
        val ratio = newZoom / zoom
        panX = anchorX - (anchorX - panX) * ratio
        panY = anchorY - (anchorY - panY) * ratio
        zoom = newZoom
    }

    fun resetView() {
        zoom = 1f; panX = 0f; panY = 0f
    }

    // ComposePanel's JVM host forwards the native macOS magnification gesture.
    // It is separate from wheel/trackpad scrolling, so Figma-style two-finger
    // pan and pinch-to-zoom can coexist without a wheel-delta heuristic.
    LaunchedEffect(magnification?.sequence) {
        val gesture = magnification ?: return@LaunchedEffect
        val anchor = hoverPos
        val ax = (anchor?.x ?: viewportSize.width / 2f) - viewportSize.width / 2f
        val ay = (anchor?.y ?: viewportSize.height / 2f) - viewportSize.height / 2f
        // The session accumulates in log space, so no magnification is lost when
        // several native events arrive before Compose can recompose this effect.
        val factor = exp(gesture.logScale - appliedMagnificationLog).toFloat().coerceIn(0.1f, 10f)
        appliedMagnificationLog = gesture.logScale
        applyZoom(zoom * factor, ax, ay)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned {
                        viewportSize = Size(it.size.width.toFloat(), it.size.height.toFloat())
                    }
                    .onPointerEvent(PointerEventType.Scroll) { event ->
                        val delta = event.changes.firstOrNull()?.scrollDelta ?: Offset.Zero
                        val mods = event.keyboardModifiers
                        if (mods.isCtrlPressed || mods.isMetaPressed) {
                            // Explicit Ctrl/Cmd + wheel remains a zoom fallback. Native
                            // plugin pinch is delivered through [magnification] instead.
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
                    .onPointerEvent(PointerEventType.Move) { event ->
                        hoverPos = if (event.buttons.isPrimaryPressed || event.buttons.isTertiaryPressed) null
                        else event.changes.firstOrNull()?.position
                    }
                    .onPointerEvent(PointerEventType.Exit) { hoverPos = null }
                    // Middle-button drag pans — plain-mouse users have no two-finger scroll.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (!currentEvent.buttons.isTertiaryPressed) return@awaitEachGesture
                            down.consume()
                            setCanvasCursor("grabbing")
                            var prev = down.position
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) { change.consume(); break }
                                    panX += change.position.x - prev.x
                                    panY += change.position.y - prev.y
                                    prev = change.position
                                    change.consume()
                                }
                            } finally {
                                setCanvasCursor("default")
                            }
                        }
                    }
                    // A tap on empty canvas (nothing consumed it) selects the artboard.
                    .pointerInput(Unit) { detectTapGestures { state.select(state.root.id) } },
            ) {
                val density = LocalDensity.current
                val screens = state.composables
                // The content bounding box is frozen while screens MOVE, so dragging
                // never re-fits the view. Shared screen-size changes do re-key it.
                val screenSizeKey = screens.map { Triple(it.id, it.width, it.height) }
                val content = remember(screenSizeKey) { contentBoxOf(screens) }
                // Auto-fit scale (never up past 1:1), then apply the user's zoom. The
                // margin keeps names, handles, and the floating zoom controls reachable.
                // The component bar is a real footer and no longer consumes canvas space.
                val marginH = 144f
                val marginV = 144f
                val fit = minOf(1f, (maxWidth.value - marginH) / content.w, (maxHeight.value - marginV) / content.h)
                    .coerceAtLeast(0.05f)
                SideEffect { fitScale = fit }
                val scale = (fit * zoom).coerceIn(MIN_SCALE, MAX_SCALE)

                // Shared measurement state: node bounds in the PRE-SCALE space + that
                // space's coordinates. Hoisted here so the screen-space overlay below
                // (outside the zoomed layer) can use them.
                val bounds = remember { mutableStateMapOf<String, Rect>() }
                var spaceCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
                // Prune bounds of deleted nodes so ghost rects can't win drill hit-tests.
                // One O(n) id sweep — a findById per key was O(n·m) on every edit keystroke.
                LaunchedEffect(state.root) {
                    val ids = HashSet<String>().also { collectIds(state.root, it) }
                    val stale = bounds.keys.filter { it !in ids }
                    stale.forEach { bounds.remove(it) }
                }

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

                // Overlays live in SCREEN space — siblings of the zoomed layer, so all
                // chrome (outline, handles, edge zones, dims pill) is plain dp and stays
                // crisp/hit-testable at ANY zoom. Inside the layer, /scale layout sizes
                // round to 0 px past ~150x — dead handles, no cursor. The pre-scale→screen
                // mapping mirrors the layer's transform (centered + pan + scale) and READS
                // panX/panY/scale, so it recomposes with them. The layer is island-sized
                // and scales around its center (= viewport center + pan); bounds are
                // island-space px with the content-centering already baked in by
                // ScreenFrame's placement.
                val d = density.density
                val cw = with(density) { maxWidth.toPx() }
                val ch = with(density) { maxHeight.toPx() }
                fun toScreen(x: Float, y: Float) =
                    Offset(cw / 2f + panX + (x - cw / 2f) * scale, ch / 2f + panY + (y - ch / 2f) * scale)

                // Screen names live outside the zoomed layer. Their anchors follow
                // each transformed frame while the text and gap stay constant in px.
                for (screen in screens) {
                    val frameLeft = (cw - content.w * d) / 2f + (screen.x - content.minX) * d
                    val frameTop = (ch - content.h * d) / 2f + (screen.y - content.minY) * d
                    ScreenLabel(
                        name = state.layerName(screen.id) ?: "Composable",
                        selected = state.selectedId == screen.id,
                        frameTopLeft = toScreen(frameLeft, frameTop),
                        onClick = { state.select(screen.id) },
                    )
                }

                // Corner rounding of a node's outline in screen px. Percent corners are
                // relative to the node's smaller side (like the rendered
                // RoundedCornerShape(percent)); dp corners scale with zoom.
                fun outlineCornerPx(node: Node?, r: Rect): Float =
                    when (val c = if (node is Node.Composable) null else node?.backgroundCorner()) {
                        null -> 0f
                        else -> when (c.second) {
                            CornerUnit.Percent -> minOf(r.width, r.height) * scale * (c.first.coerceAtMost(50) / 100f)
                            CornerUnit.Dp -> c.first * d * scale
                        }
                    }

                val sel = state.selectedId
                val b = sel?.let { bounds[it] }

                // Hover preview: outline the DEEPEST node under the cursor — exactly
                // what a click selects (see EditorState.selectAt). Hit-testing reuses
                // the bounds map (smallest rect containing the point), so it works over
                // interactive components too. Selection still happens only on click.
                val hoverTarget = hoverPos?.let { p ->
                    val px = (p.x - cw / 2f - panX) / scale + cw / 2f
                    val py = (p.y - ch / 2f - panY) / scale + ch / 2f
                    val deepest = bounds.entries
                        .filter { (id, r) -> id != state.root.id && px >= r.left && px <= r.right && py >= r.top && py <= r.bottom }
                        .minByOrNull { (_, r) -> r.width * r.height }?.key
                    deepest?.let {
                        val overSel = b != null && px >= b.left && px <= b.right && py >= b.top && py <= b.bottom
                        // No preview in the resize-handle band just outside the selection —
                        // an outline flashing under the handles reads as noise (Figma hides it).
                        val margin = 16f * d / scale
                        val nearSelEdge = b != null && !overSel &&
                            px >= b.left - margin && px <= b.right + margin &&
                            py >= b.top - margin && py <= b.bottom + margin
                        if (nearSelEdge) null else it
                    }
                }
                val hb = hoverTarget?.takeIf { it != sel && it != state.root.id }?.let { bounds[it] }
                if (hb != null) {
                    val hoverNode = state.root.findById(hoverTarget)
                    val hCorner = outlineCornerPx(hoverNode, hb)
                    val htl = toScreen(hb.left, hb.top)
                    val hbr = toScreen(hb.right, hb.bottom)
                    HoverOutline(Rect(htl.x, htl.y, hbr.x, hbr.y), hCorner, density)
                }

                if (sel != null && b != null && sel != state.root.id) {
                    val tl = toScreen(b.left, b.top)
                    val br = toScreen(b.right, b.bottom)
                    // key(sel) recreates the whole overlay per selection — its pointerInputs
                    // capture callbacks at start, so a reused overlay would keep dragging
                    // the PREVIOUSLY selected node.
                    key(sel) {
                        val isScreen = state.selected is Node.Composable
                        val isShape = state.selected?.isShape() == true
                        SelectionOverlay(
                            screen = Rect(tl.x, tl.y, br.x, br.y),
                            viewport = Size(cw, ch),
                            dimsLabel = "${with(density) { b.width.toDp().value }.roundToInt()} × ${with(density) { b.height.toDp().value }.roundToInt()}",
                            density = density,
                            cornerPx = outlineCornerPx(state.selected, b),
                            scale = scale,
                            frameCoords = spaceCoords,
                            // Screens float freely on the artboard: resizing from a top/left
                            // handle moves them so the opposite edge stays pinned. Components
                            // are LAYOUT children (their position is the parent's business,
                            // like Figma auto-layout items) — corners/edges only resize.
                            // Shapes float freely inside their Canvas (model coords), so
                            // top/left handles move-compensate like screens once did.
                            anchorMove = isShape,
                            resizable = !isScreen && state.selected !is Node.Line,
                            onMove = { dx, dy ->
                                when {
                                    isScreen -> state.moveComposable(sel, dx, dy)
                                    isShape -> state.moveShape(sel, dx, dy)
                                    else -> state.offsetNode(sel, dx, dy)
                                }
                            },
                            // ~8dp of SCREEN travel snaps at any zoom (threshold is in artboard dp).
                            onBodyMove = { dx, dy ->
                                when {
                                    isScreen ->
                                        state.moveComposableSnapped(sel, dx, dy, threshold = (8f / scale).roundToInt().coerceAtLeast(2))
                                    isShape -> state.moveShape(sel, dx, dy)
                                    else -> state.offsetNode(sel, dx, dy)
                                }
                            },
                            onBodyMoveEnd = { if (isScreen) state.endScreenDrag() },
                            onResize = { dw, dh ->
                                when {
                                    isShape -> state.resizeShape(sel, dw, dh)
                                    else -> {
                                        val baseW = with(density) { b.width.toDp().value }.roundToInt()
                                        val baseH = with(density) { b.height.toDp().value }.roundToInt()
                                        state.resizeNode(sel, baseW, baseH, dw, dh)
                                    }
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
            SizeBadge(state, appMode, Modifier.align(Alignment.TopStart).padding(12.dp))
            // The badge shows/steps the EFFECTIVE scale; applyZoom takes the relative zoom.
            ZoomBadge(fitScale * zoom, onZoom = { applyZoom(it / fitScale) }, onReset = ::resetView, Modifier.align(Alignment.TopEnd).padding(12.dp))
        }
        ComponentBar(state)
    }
}
