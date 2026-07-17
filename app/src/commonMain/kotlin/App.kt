package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.onEach
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
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
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
import kotlin.math.exp
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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import composer.model.DesignJson
import composer.model.DesignTheme
import composer.model.ThemeColorRef
import composer.model.Node
import composer.model.CornerUnit
import composer.model.backgroundCorner
import composer.model.childNodes
import composer.model.findById
import composer.model.isShape
import composer.render.LocalDesignRoot
import composer.render.RenderNode
import composer.render.toColorScheme
import composer.ui.AppIcon
import composer.ui.AppIconKind
import composer.ui.SquareIconButton
import composer.ui.HDivider
import composer.ui.Island
import composer.ui.LocalThemeSwatches
import composer.ui.ThemeSwatch
import composer.ui.Theme
import composer.ui.Tip
import composer.ui.Tk
import composer.ui.ToolButton
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer

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
            .background(Tk.appBg)
            .padding(Tk.gap)
            .focusRequester(focusRequester)
            .onKeyEvent {
                handleShortcut(
                    event = it,
                    state = state,
                    lockComposableStructure = session.appMode,
                )
            }
            .focusable(),
        verticalArrangement = Arrangement.spacedBy(Tk.gap),
    ) {
        Toolbar(
            state,
            showNewComposable = session.appMode,
            onNewComposable = session::requestNewComposable,
        )
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Tk.gap),
        ) {
            if (state.leftPanelOpen) {
                Island(Modifier.width(240.dp).fillMaxHeight()) { TreeView(state, onCollapse = state::toggleLeftPanel) }
            } else {
                CollapsedPanelStrip(AppIconKind.ExpandLeft, tip = "Show layers", onExpand = state::toggleLeftPanel)
            }
            Island(
                Modifier.weight(1f).fillMaxHeight().then(
                    // On any canvas press, reclaim editor focus (Initial pass, no consume) so
                    // keyboard shortcuts work even after clicking the same already-selected node.
                    Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            runCatching { focusRequester.requestFocus() }
                        }
                    }
                ),
                color = Tk.canvasBg,
            ) {
                Canvas(
                    state = state,
                    appMode = session.appMode,
                    magnification = session.canvasMagnification,
                )
            }
            if (state.rightPanelOpen) {
                Island(Modifier.width(240.dp).fillMaxHeight()) {
                    Inspector(
                        state,
                        onCollapse = state::toggleRightPanel,
                        lockComposableStructure = session.appMode,
                    )
                }
            } else {
                CollapsedPanelStrip(AppIconKind.ExpandRight, tip = "Show inspector", onExpand = state::toggleRightPanel)
            }
        }
    }
    }
}

/** Standalone website editor. Browser routing, files, and code view live here. */
@OptIn(FlowPreview::class)
@Composable
internal fun WebEditorScreen(ws: Workspace) {
    val state = remember(ws.openToken) { EditorState(ws.initialDesign) }
    val codeSync = remember(ws.openToken) { CodeSyncState() }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    LaunchedEffect(state.selectedId) {
        if (state.selectedId != null && !state.showCode) runCatching { focusRequester.requestFocus() }
    }
    LaunchedEffect(state, ws) {
        snapshotFlow { state.root to ws.currentName }
            .drop(1)
            .onEach { ws.markDirty() }
            .debounce(700)
            .collect { ws.save(state.root) }
    }
    DisposableEffect(state, ws) {
        val unregister = registerUnloadFlush {
            if (state.root != ws.initialDesign) ws.save(state.root)
        }
        onDispose { unregister() }
    }

    val themeSwatches = DesignTheme.TOKENS.map { token ->
        ThemeSwatch(token, ThemeColorRef.token(token)!!, state.theme.effective(token))
    }
    CompositionLocalProvider(LocalThemeSwatches provides themeSwatches) {
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
            WebToolbar(state, ws)
            ws.saveError?.let { message ->
                WebErrorBanner(message, onAction = ws::dismissSaveError)
            }
            if (ws.loadFailed) {
                WebErrorBanner(
                    "Couldn't read this saved design. Auto-save is paused so its data stays intact.",
                    actionLabel = "Save anyway",
                    onAction = { ws.saveOverwriting(state.root) },
                )
            }
            ws.importError?.let { message ->
                WebErrorBanner(message, onAction = { ws.importError = null })
            }
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Tk.gap),
            ) {
                if (state.leftPanelOpen) {
                    Island(Modifier.width(240.dp).fillMaxHeight()) {
                        TreeView(state, onCollapse = state::toggleLeftPanel)
                    }
                } else {
                    CollapsedPanelStrip(AppIconKind.ExpandLeft, "Show layers", state::toggleLeftPanel)
                }
                Island(
                    modifier = Modifier.weight(1f).fillMaxHeight().then(
                        if (state.showCode) Modifier else Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                runCatching { focusRequester.requestFocus() }
                            }
                        },
                    ),
                    color = if (state.showCode) Tk.codeBg else Tk.canvasBg,
                ) {
                    if (state.showCode) {
                        CodePanel(state, codeSync)
                    } else {
                        Canvas(state = state, appMode = false, magnification = null)
                    }
                }
                if (state.rightPanelOpen) {
                    Island(Modifier.width(240.dp).fillMaxHeight()) {
                        Inspector(state, onCollapse = state::toggleRightPanel)
                    }
                } else {
                    CollapsedPanelStrip(AppIconKind.ExpandRight, "Show inspector", state::toggleRightPanel)
                }
            }
        }
    }
}

/**
 * A collapsed side panel: a slim island with just the expand affordance, aligned
 * with the neighbors' headers (IntelliJ tool-window style).
 */
@Composable
private fun CollapsedPanelStrip(icon: AppIconKind, tip: String, onExpand: () -> Unit) {
    Island(Modifier.width(46.dp).fillMaxHeight()) {
        Box(Modifier.fillMaxWidth().height(46.dp), contentAlignment = Alignment.Center) {
            SquareIconButton(icon, tip = tip, onClick = onExpand)
        }
    }
}

/**
 * Plugin toolbar: local design history and preview theme. Android Studio owns
 * files, source code, export, and project identity.
 */
@Composable
private fun Toolbar(
    state: EditorState,
    showNewComposable: Boolean,
    onNewComposable: () -> Unit,
) {
    // 40dp: the tallest controls are 32dp, so this leaves 4dp of air above/below —
    // a slim, Figma-like bar instead of the airy 52dp it started with.
    Box(modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp)) {
        if (showNewComposable) {
            Box(Modifier.align(Alignment.CenterStart)) {
                ToolButton(
                    label = "New Composable",
                    icon = AppIconKind.Plus,
                    onClick = onNewComposable,
                )
            }
        }
        ScreenSizeControl(state, Modifier.align(Alignment.Center))
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TopIconButton(AppIconKind.Undo, tip = "Undo (⌘Z)", enabled = state.canUndo, onClick = state::undo)
            TopIconButton(AppIconKind.Redo, tip = "Redo (⇧⌘Z)", enabled = state.canRedo, onClick = state::redo)
            TopDivider()
            TopIconButton(
                if (Theme.isDark) AppIconKind.Sun else AppIconKind.Moon,
                tip = if (Theme.isDark) "Light mode" else "Dark mode",
                onClick = Theme::toggle,
            )
        }
    }
}

/** Borderless hover-highlight icon button for toolbar history/theme actions. */
@Composable
private fun TopIconButton(icon: AppIconKind, tip: String, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Tip(tip) {
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
}


@Composable
private fun TopDivider() {
    Box(Modifier.padding(horizontal = 2.dp)) {
        Box(Modifier.width(1.dp).height(20.dp).background(Tk.border))
    }
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
private fun Canvas(
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

    Box(modifier = modifier.fillMaxSize()) {
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
            // vertical margin clears the FLOATING CHROME: the preset badge (top) and
            // the palette (bottom) overlay the canvas and eat clicks — a frame fitted
            // under them has unreachable name label, top handles, and bottom handles.
            val marginH = 144f
            val marginV = 320f
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
        FloatingPalette(state, Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp))
    }
}

// Effective-scale limits: absolute magnification, independent of the fitted scale.
private const val MIN_SCALE = 0.02f
private const val MAX_SCALE = 500f

/** True magnification: "1x" = 1 design dp per screen dp; deep zooms read as a multiplier ("500x"), not "50000%". */
private fun zoomLabel(zoom: Float): String {
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
private fun collectIds(node: Node, out: MutableSet<String>) {
    out.add(node.id)
    for (child in node.childNodes()) collectIds(child, out)
}

@Composable
private fun ZoomBadge(zoom: Float, onZoom: (Float) -> Unit, onReset: () -> Unit, modifier: Modifier = Modifier) {
    Island(modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Tip("Zoom out") { ToolButton("−") { onZoom(zoom / 1.2f) } }
            // The readout zooms to TRUE size (1 design dp = 1 screen dp); Fit re-fits the view.
            Tip("Actual size (1x)") { ToolButton(zoomLabel(zoom), onClick = { onZoom(1f) }) }
            Tip("Zoom in") { ToolButton("+") { onZoom(zoom * 1.2f) } }
            Tip("Fit to screen") { ToolButton("", icon = AppIconKind.Fit, onClick = onReset) }
        }
    }
}

/**
 * Standalone mode can create composables here. Module discovery instead shows
 * the discovered count; function creation waits for stable annotation identity.
 */
@Composable
private fun SizeBadge(state: EditorState, appMode: Boolean, modifier: Modifier = Modifier) {
    Island(modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (appMode) {
                BasicText(
                    "${state.composables.size} composables",
                    style = TextStyle(color = Tk.textSecondary, fontSize = 12.sp),
                )
            } else {
                ToolButton("Composable", icon = AppIconKind.Plus) { state.addComposable() }
            }
            val comps = state.componentDefs()
            if (comps.isNotEmpty()) {
                Box(Modifier.width(1.dp).height(20.dp).padding(horizontal = 2.dp).background(Tk.border))
                for ((refId, name) in comps) {
                    ToolButton(name) { state.insertInstanceOf(refId) }
                }
            }
        }
    }
}

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
            // Snap guides (screen drags): full-length lines at the matched artboard
            // coordinate, same centering math as ScreenFrame; stroke /scale so it
            // stays 1dp on screen at any zoom. Draw-only — no pointer handlers.
            val gv = state.snapGuideV
            val gh = state.snapGuideH
            val bars = state.spacingBars
            if (gv != null || gh != null || bars.isNotEmpty()) {
                val textMeasurer = rememberTextMeasurer()
                Box(
                    Modifier.matchParentSize().drawBehind {
                        val stroke = 1.dp.toPx() / scale
                        val guide = Tk.snapGuide
                        fun ax(v: Int) = (size.width - content.w.dp.toPx()) / 2f + (v - content.minX).dp.toPx()
                        fun ay(v: Int) = (size.height - content.h.dp.toPx()) / 2f + (v - content.minY).dp.toPx()
                        if (gv != null) drawLine(guide, Offset(ax(gv), 0f), Offset(ax(gv), size.height), stroke)
                        if (gh != null) drawLine(guide, Offset(0f, ay(gh)), Offset(size.width, ay(gh)), stroke)
                        // Spacing bars: gap segment + end ticks + the gap value —
                        // all /scale so the chrome stays constant-size on screen.
                        val tick = 4.dp.toPx() / scale
                        for (b in bars) {
                            val label = textMeasurer.measure(
                                b.value.toString(),
                                TextStyle(color = guide, fontSize = (10f / scale).sp, fontWeight = FontWeight.Medium),
                            )
                            if (b.horizontal) {
                                val y = ay(b.cross).let { it }
                                val x1 = ax(b.start)
                                val x2 = ax(b.end)
                                drawLine(guide, Offset(x1, y), Offset(x2, y), stroke)
                                drawLine(guide, Offset(x1, y - tick), Offset(x1, y + tick), stroke)
                                drawLine(guide, Offset(x2, y - tick), Offset(x2, y + tick), stroke)
                                drawText(label, topLeft = Offset((x1 + x2) / 2f - label.size.width / 2f, y - tick - label.size.height))
                            } else {
                                val x = ax(b.cross)
                                val y1 = ay(b.start)
                                val y2 = ay(b.end)
                                drawLine(guide, Offset(x, y1), Offset(x, y2), stroke)
                                drawLine(guide, Offset(x - tick, y1), Offset(x + tick, y1), stroke)
                                drawLine(guide, Offset(x - tick, y2), Offset(x + tick, y2), stroke)
                                drawText(label, topLeft = Offset(x + tick + stroke, (y1 + y2) / 2f - label.size.height / 2f))
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * One composable's frame at its artboard position, with a clickable name label.
 * The frame is TRANSPARENT and HUGS its content: width/height are MAX constraints
 * (the device preset) — a fillMaxSize child grows to them, smaller content wraps.
 * No fixed size, no background paint, no canvas resize (the shared maximum is
 * picked in the toolbar); the label shows the generated function name.
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
                // MAX constraints (not fixed): the frame hugs its content; the
                // preset size only caps it — fillMaxSize children expand to it.
                val w = screen.width.dp.roundToPx()
                val h = screen.height.dp.roundToPx()
                val placeable = measurable.measure(Constraints(maxWidth = w, maxHeight = h))
                val ox = ((constraints.maxWidth - content.w.dp.toPx()) / 2f + (screen.x - content.minX).dp.toPx()).roundToInt()
                val oy = ((constraints.maxHeight - content.h.dp.toPx()) / 2f + (screen.y - content.minY).dp.toPx()).roundToInt()
                layout(0, 0) { placeable.place(ox, oy) }
            },
    ) {
        // The design's own theme wraps the preview (WYSIWYG with the generated
        // MaterialTheme). The frame paints NOTHING itself — a composable is
        // transparent until the user adds a background; the canvas grid shows
        // through, and the frame simply wraps whatever the content measures.
        MaterialTheme(colorScheme = state.theme.toColorScheme()) {
            // MaterialTheme alone does NOT set LocalContentColor (only Surface does) —
            // default-colored Text/Icon follows the theme like a themed app surface.
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onBackground,
                LocalDesignRoot provides state.root,
            ) {
                Box(
                    modifier = Modifier
                        // Register the measured surface, not the zero-sized
                        // positioning wrapper. This is the selection outline:
                        // full screen for fill/equal-size content, hug otherwise,
                        // and exactly 0×0 when there is no renderable content.
                        .onGloballyPositioned { register(screen.id, it) }
                        // Per-pixel grid overlay on the measured content area (the
                        // frame is transparent and hugs content, so this box IS the
                        // visible screen surface); fades in past 2x zoom.
                        .pixelGrid(scale)
                        // A tap on a gap (no child consumed it) selects the composable.
                        .pointerInput(screen.id) { detectTapGestures { state.select(screen.id) } },
                ) {
                    screen.children.forEach { child ->
                        RenderNode(child, state.selectedId, onSelect = state::selectAt, onBounds = ::register)
                    }
                }
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
 * Figma-style hover preview: a passive accent outline around the node a click
 * would select. Pure draw — NO pointer handlers, so it never eats a tap/drag.
 * Same screen-space float-px technique as the SelectionOverlay outline (layout
 * stays viewport-sized; huge zoomed rects are fine as draws).
 */
@Composable
private fun HoverOutline(screen: Rect, cornerPx: Float, density: Density) {
    val accent = Tk.accent
    val onePx = with(density) { 1.dp.toPx() }
    val gap = onePx // match the selection outline: 1dp OUTSIDE the component
    val outer = Rect(screen.left - gap, screen.top - gap, screen.right + gap, screen.bottom + gap)
    Box(
        Modifier.fillMaxSize().drawBehind {
            val sw = onePx * 1.5f // slightly heavier than the selection stroke, like Figma
            val inset = sw / 2f
            val tl = Offset(outer.left + inset, outer.top + inset)
            val sz = Size(outer.width - sw, outer.height - sw)
            if (sz.width <= 0f || sz.height <= 0f) return@drawBehind
            if (cornerPx > 0f) {
                val r = (cornerPx + gap - inset).coerceAtLeast(0f)
                drawRoundRect(accent, topLeft = tl, size = sz, cornerRadius = CornerRadius(r), style = Stroke(sw))
            } else {
                drawRect(accent, topLeft = tl, size = sz, style = Stroke(sw))
            }
        },
    )
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
    // Body-grip drag override + end hook: screens route the body drag through the
    // SNAPPED move (edge/corner anchor-compensation keeps plain onMove — mixing
    // snap into resize deltas would fight the handles).
    onBodyMove: ((Int, Int) -> Unit)? = null,
    onBodyMoveEnd: () -> Unit = {},
    resizable: Boolean = true,
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
                    .windowAnchoredDrag(scale, { bodyCoords }, thresholdDp = 5f, onEnd = onBodyMoveEnd) { dx, dy ->
                        (onBodyMove ?: onMove)(dx, dy)
                    },
            )
        }

        // Figma-style resize chrome: invisible EDGE zones (straddling the outline,
        // native resize cursor on hover) + four white corner squares — each placed
        // only along its edge's VISIBLE segment. Composables are NOT resizable
        // (they hug content; their preset is picked in the inspector) — [resizable]
        // skips all of this for them.
        val vy0 = outer.top.coerceAtLeast(0f)
        val vy1 = outer.bottom.coerceAtMost(viewport.height)
        val vx0 = outer.left.coerceAtLeast(0f)
        val vx1 = outer.right.coerceAtMost(viewport.width)
        if (resizable) {
        val thick = 10f * onePx // edge hit thickness (10dp), straddling the outline

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
        }

        // Figma-style dimensions pill under the selection's bottom edge (when that
        // edge is on screen); centered on the visible span of the selection. A
        // viewport narrower than the pill (IDE tool window) inverts the clamp
        // range — coerceIn would throw and kill the composition, so skip it.
        if (outer.bottom >= 0f && outer.bottom <= viewport.height - 12f * onePx &&
            viewport.width >= 120f * onePx
        ) {
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
 * native resize [cursor] on hover (locked while dragging), and drags via
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

/**
 * Global keyboard shortcuts. Runs as `onKeyEvent` (bubbling), so a focused text
 * field consumes its own keystrokes first — Delete/Backspace only deletes a node
 * when the canvas (not an inspector field) has focus. Returns true when handled.
 * The code editor owns the keyboard entirely while focused: text fields don't
 * consume non-editing keys (⌘D, ⌘Y, Escape) and those firing design actions
 * mid-typing would be destructive.
 */
private fun handleShortcut(
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
