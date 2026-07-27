package composer.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.ContentScale
import composer.LocalFonts
import composer.LocalImages
import composer.ui.SymbolIcon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.runtime.key
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import composer.model.BoxAlignment
import composer.model.ButtonVariant
import composer.model.ChipVariant
import composer.model.childNodes
import composer.model.findById
import composer.model.CornerUnit
import composer.model.DesignTheme
import composer.model.GradientDirection
import composer.model.HAlignment
import composer.model.HArrangement
import composer.model.IconKind
import composer.model.ModifierSpec
import composer.model.PaddingMode
import composer.model.Node
import composer.model.SourcePreviewLayout
import composer.model.TextAlignment
import composer.model.TextFontFamily
import androidx.compose.ui.text.style.TextAlign
import composer.model.TextWeight
import composer.model.ThemeColorRef
import composer.model.TopAppBarVariant
import composer.model.effectiveLineHeight
import composer.model.VAlignment
import composer.model.VArrangement

/**
 * Renders a [Node] tree on the canvas with real Compose composables — the same
 * primitives [composer.codegen.CodeGen] targets, so the canvas is true WYSIWYG.
 *
 * Tapping a node selects it (innermost wins — the tap gesture is consumed at the
 * deepest node under the pointer). The selected node gets an outline. [onBounds]
 * reports each node's layout coordinates so the canvas can place resize/move
 * handles over the selection.
 */
/** The full design tree — [Node.Instance] resolves its main through this. */
val LocalDesignRoot = compositionLocalOf<Node?> { null }

/** Instance nesting depth — hard stop against pathological reference chains. */
val LocalInstanceDepth = compositionLocalOf { 0 }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenderNode(
    node: Node,
    selectedId: String?,
    onSelect: (id: String, deep: Boolean) -> Unit,
    onBounds: (String, LayoutCoordinates) -> Unit = { _, _ -> },
    scopeModifier: Modifier = Modifier,
    scaffoldPadding: PaddingValues? = null,
) {
    // Split the chain so the selection outline wraps the node's FULL box: the positional
    // Offset stays outermost (so a moved node's outline/hit-area track it), but the bounds
    // measurement + tap hit-area sit OUTSIDE the node's own box modifiers (padding, size,
    // background). Otherwise the innermost onGloballyPositioned measured the content area
    // *inside* the padding, so a padded container's outline hugged its content, not its box.
    val scheme = MaterialTheme.colorScheme
    val previewModifiers = node.modifier.previewSpecs()
    val offsetMod = previewModifiers.filterIsInstance<ModifierSpec.Offset>().toModifier(scheme, scaffoldPadding)
    val innerMod = previewModifiers.filterNot { it is ModifierSpec.Offset }.toModifier(scheme, scaffoldPadding)
    val modifier = scopeModifier
        .then(offsetMod)
        .then(selectionModifier(node, onSelect))
        .onGloballyPositioned { onBounds(node.id, it) }
        .then(innerMod)
    when (node) {
        // An instance renders its composable's CONTENT as ONE unit: taps anywhere
        // inside select the INSTANCE (redirected onSelect), and inner nodes don't
        // register bounds (their ids would collide across instances). The
        // composable's canvas geometry (x/y/w/h) is editor-only — not applied here.
        is Node.Instance -> {
            val designRoot = LocalDesignRoot.current
            val depth = LocalInstanceDepth.current
            val main = if (depth < 8) designRoot?.findById(node.refId) as? Node.Composable else null
            Box(modifier = modifier) {
                if (main == null) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .border(1.dp, MaterialTheme.colorScheme.error),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("?", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    CompositionLocalProvider(LocalInstanceDepth provides depth + 1) {
                        main.children.forEach { child ->
                            RenderNode(
                                child,
                                selectedId = null,
                                onSelect = { _, _ -> onSelect(node.id, false) },
                                onBounds = { _, _ -> },
                            )
                        }
                    }
                }
            }
        }
        is Node.Text -> {
            val custom = node.customFont.takeIf { it.isNotEmpty() }
            if (custom != null) LaunchedEffect(custom) { LocalFonts.load(custom) }
            Text(
                text = node.text,
                modifier = modifier,
                color = node.color?.let { themeColor(it, MaterialTheme.colorScheme) } ?: Color.Unspecified,
                fontSize = if (node.fontSize > 0) node.fontSize.sp else TextUnit.Unspecified,
                lineHeight = node.effectiveLineHeight().takeIf { it > 0 }?.sp ?: TextUnit.Unspecified,
                fontWeight = node.fontWeight.toCompose(),
                fontFamily = custom?.let { LocalFonts.loaded[it] } ?: node.fontFamily.toCompose(),
                textAlign = node.textAlign.toCompose(),
                letterSpacing = if (node.letterSpacing != 0) node.letterSpacing.sp else TextUnit.Unspecified,
            )
        }
        // A tap-capturing overlay drives selection (the Button's own clickable would swallow it).
        is Node.Button -> InteractiveNode(node, onSelect, onBounds, scopeModifier) { m ->
            // A container: children render into the content slot (a RowScope, like
            // the real M3 Button) — put Text/Icon/anything inside.
            val content: @Composable RowScope.() -> Unit = {
                node.children.forEach { child ->
                    var sm: Modifier = Modifier
                    child.weightValue()?.let { sm = sm.weight(it) }
                    child.alignVertical()?.let { sm = sm.align(it.toCompose()) }
                    RenderNode(child, selectedId, onSelect, onBounds, sm)
                }
            }
            when (node.variant) {
                ButtonVariant.Filled -> Button(onClick = {}, modifier = m, content = content)
                ButtonVariant.Elevated -> ElevatedButton(onClick = {}, modifier = m, content = content)
                ButtonVariant.FilledTonal -> FilledTonalButton(onClick = {}, modifier = m, content = content)
                ButtonVariant.Outlined -> OutlinedButton(onClick = {}, modifier = m, content = content)
                ButtonVariant.Text -> TextButton(onClick = {}, modifier = m, content = content)
            }
        }
        is Node.Spacer -> Spacer(modifier = modifier)
        // RawCode remains in the model for lossless write-back, but it is not a
        // visual component and must not contribute canvas size or selection UI.
        is Node.RawCode -> Unit
        // Runtime/data wrappers are locked source, but their parsed descendants
        // form a useful static template in the designer.
        is Node.SourceContainer -> when (node.previewLayout) {
            SourcePreviewLayout.Box -> Box(modifier = modifier) {
                node.children.forEach { child ->
                    val sm = child.alignBox()?.let { Modifier.align(it.toCompose()) } ?: Modifier
                    RenderNode(child, selectedId, onSelect, onBounds, sm)
                }
            }
            SourcePreviewLayout.Column -> Column(modifier = modifier) {
                node.children.forEach { child ->
                    var sm: Modifier = Modifier
                    child.weightValue()?.let { sm = sm.weight(it) }
                    child.alignHorizontal()?.let { sm = sm.align(it.toCompose()) }
                    RenderNode(child, selectedId, onSelect, onBounds, sm)
                }
            }
            SourcePreviewLayout.Row -> Row(modifier = modifier) {
                node.children.forEach { child ->
                    var sm: Modifier = Modifier
                    child.weightValue()?.let { sm = sm.weight(it) }
                    child.alignVertical()?.let { sm = sm.align(it.toCompose()) }
                    RenderNode(child, selectedId, onSelect, onBounds, sm)
                }
            }
        }
        is Node.Image -> {
            if (node.url.isNotEmpty()) LaunchedEffect(node.url) { LocalImages.load(node.url) }
            val bitmap = node.url.takeIf { it.isNotEmpty() }?.let { LocalImages.loaded[it] }
            when {
                bitmap != null -> Image(
                    bitmap = bitmap,
                    contentDescription = node.contentDescription.ifBlank { null },
                    modifier = modifier,
                    contentScale = ContentScale.Crop,
                )
                // Failed fetch/decode (CORS, 404) — a plain placeholder here reads
                // as "still loading" forever, so show an explicit broken-image state.
                node.url.isNotEmpty() && LocalImages.isFailed(node.url) -> Box(
                    modifier = modifier.background(Color(node.placeholderColor).copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    SymbolIcon(
                        "broken_image",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Resource painters belong to the source project and are not
                // available to the in-process designer. Draw a neutral surface
                // without instantiating ColorPainter: that class is not ABI
                // compatible across every Compose version bundled by Studio.
                else -> Box(modifier = modifier.background(Color(node.placeholderColor)))
            }
        }
        is Node.Divider -> HorizontalDivider(modifier = modifier)
        // A free-form Material Symbols name wins over the curated IconKind; the
        // preview draws from the bundled symbol set, sized like a Material icon.
        is Node.Icon -> if (node.sourceImageExpression.isNotEmpty() || node.symbol.isNotEmpty()) {
            SymbolIcon(
                node.symbol.ifEmpty { sourceIconName(node.sourceImageExpression) },
                modifier.size(24.dp),
                tint = LocalContentColor.current,
                contentDescription = node.contentDescription.ifBlank { null },
            )
        } else {
            SymbolIcon(
                node.icon.symbolName(),
                modifier.size(24.dp),
                tint = LocalContentColor.current,
                contentDescription = node.contentDescription.ifBlank { null },
            )
        }
        is Node.IconButton -> InteractiveNode(node, onSelect, onBounds, scopeModifier) { m ->
            IconButton(onClick = {}, modifier = m) {
                if (node.previewSymbol.isNotEmpty() || node.symbol.isNotEmpty()) {
                    SymbolIcon(node.previewSymbol.ifEmpty { node.symbol }, Modifier.size(24.dp), tint = LocalContentColor.current)
                } else {
                    SymbolIcon(node.icon.symbolName(), Modifier.size(24.dp), tint = LocalContentColor.current)
                }
            }
        }
        // Tab/nav-item clicks double as canvas selection of that item node; each
        // item reports bounds so the selection outline and hover work on it.
        is Node.TabRow -> if (node.children.isEmpty()) {
            Box(modifier = modifier)
        } else {
            TabRow(
                selectedTabIndex = node.selectedIndex.coerceIn(0, node.children.lastIndex),
                modifier = modifier,
            ) {
                node.children.forEachIndexed { i, child ->
                    val tab = child as? Node.Tab ?: return@forEachIndexed
                    Tab(
                        selected = i == node.selectedIndex,
                        onClick = { onSelect(tab.id, false) },
                        text = { Text(tab.label) },
                        modifier = tab.modifier.toModifier(scheme).onGloballyPositioned { onBounds(tab.id, it) },
                    )
                }
            }
        }
        is Node.NavigationBar -> NavigationBar(modifier = modifier) {
            node.children.forEachIndexed { i, child ->
                val item = child as? Node.NavItem ?: return@forEachIndexed
                NavigationBarItem(
                    selected = i == node.selectedIndex,
                    onClick = { onSelect(item.id, false) },
                    icon = {
                        if (item.symbol.isNotEmpty()) SymbolIcon(item.symbol, Modifier.size(24.dp), tint = LocalContentColor.current)
                    },
                    label = { Text(item.label) },
                    modifier = item.modifier.toModifier(scheme).onGloballyPositioned { onBounds(item.id, it) },
                )
            }
        }
        // A stray Tab outside a TabRow (defensive) renders static, like codegen emits it.
        is Node.Tab -> Tab(selected = false, onClick = {}, text = { Text(node.label) }, modifier = modifier)
        is Node.NavItem -> Unit // rendered by its NavigationBar parent
        is Node.Chip -> InteractiveNode(node, onSelect, onBounds, scopeModifier) { m ->
            val label: @Composable () -> Unit = { Text(node.label) }
            val leading: (@Composable () -> Unit)? = if (node.symbol.isNotEmpty()) {
                { SymbolIcon(node.symbol, Modifier.size(18.dp), tint = LocalContentColor.current) }
            } else null
            when (node.variant) {
                ChipVariant.Assist -> AssistChip(onClick = {}, label = label, leadingIcon = leading, modifier = m)
                ChipVariant.Filter -> FilterChip(selected = node.selected, onClick = {}, label = label, leadingIcon = leading, modifier = m)
                ChipVariant.Input -> InputChip(selected = node.selected, onClick = {}, label = label, leadingIcon = leading, modifier = m)
                ChipVariant.Suggestion -> SuggestionChip(onClick = {}, label = label, icon = leading, modifier = m)
            }
        }
        is Node.BadgedBox -> BadgedBox(
            badge = { if (node.badge.isEmpty()) Badge() else Badge { Text(node.badge) } },
            modifier = modifier,
        ) {
            node.children.forEach { child ->
                val sm = child.alignBox()?.let { Modifier.align(it.toCompose()) } ?: Modifier
                RenderNode(child, selectedId, onSelect, onBounds, sm)
            }
        }
        // Shapes are DRAW CALLS inside one Canvas — they have no layout bounds, so
        // they are selected via the Layers tree. Theme-ref colors resolve live here
        // (codegen can't reference the scheme inside a draw lambda and emits black).
        is Node.Canvas -> {
            val shapes = node.children
            val colors = shapes.map { s ->
                val raw = when (s) {
                    is Node.Line -> s.color
                    is Node.RectShape -> s.color
                    is Node.CircleShape -> s.color
                    is Node.EllipseShape -> s.color
                    is Node.ArcShape -> s.color
                    else -> 0xFF000000
                }
                themeColor(raw, scheme)
            }
            Box(modifier = modifier) {
            Canvas(modifier = Modifier.matchParentSize()) {
                shapes.forEachIndexed { i, shape ->
                    val c = colors[i]
                    when (shape) {
                        is Node.Line -> drawLine(
                            c,
                            start = Offset(shape.x1.dp.toPx(), shape.y1.dp.toPx()),
                            end = Offset(shape.x2.dp.toPx(), shape.y2.dp.toPx()),
                            strokeWidth = shape.strokeWidth.dp.toPx(),
                        )
                        is Node.RectShape -> {
                            val style = if (shape.filled) Fill else Stroke(shape.strokeWidth.dp.toPx())
                            if (shape.corner > 0) {
                                drawRoundRect(
                                    c,
                                    topLeft = Offset(shape.x.dp.toPx(), shape.y.dp.toPx()),
                                    size = Size(shape.width.dp.toPx(), shape.height.dp.toPx()),
                                    cornerRadius = CornerRadius(shape.corner.dp.toPx()),
                                    style = style,
                                )
                            } else {
                                drawRect(
                                    c,
                                    topLeft = Offset(shape.x.dp.toPx(), shape.y.dp.toPx()),
                                    size = Size(shape.width.dp.toPx(), shape.height.dp.toPx()),
                                    style = style,
                                )
                            }
                        }
                        is Node.CircleShape -> drawCircle(
                            c,
                            radius = shape.radius.dp.toPx(),
                            center = Offset(shape.cx.dp.toPx(), shape.cy.dp.toPx()),
                            style = if (shape.filled) Fill else Stroke(shape.strokeWidth.dp.toPx()),
                        )
                        is Node.EllipseShape -> drawOval(
                            c,
                            topLeft = Offset(shape.x.dp.toPx(), shape.y.dp.toPx()),
                            size = Size(shape.width.dp.toPx(), shape.height.dp.toPx()),
                            style = if (shape.filled) Fill else Stroke(shape.strokeWidth.dp.toPx()),
                        )
                        is Node.ArcShape -> drawArc(
                            c,
                            startAngle = shape.startAngle.toFloat(),
                            sweepAngle = shape.sweepAngle.toFloat(),
                            useCenter = shape.filled,
                            topLeft = Offset(shape.x.dp.toPx(), shape.y.dp.toPx()),
                            size = Size(shape.width.dp.toPx(), shape.height.dp.toPx()),
                            style = if (shape.filled) Fill else Stroke(shape.strokeWidth.dp.toPx()),
                        )
                        else -> Unit
                    }
                }
            }
            // Shapes are draw calls, not layout nodes — give each an invisible
            // hit box at its drawn extent so click-select, hover, and the
            // SelectionOverlay work like for any component. The box reports 0×0
            // (free placement, like ScreenFrame) so it never affects the
            // Canvas's own measured size.
            shapes.forEach { shape ->
                val r = shapeHitRect(shape) ?: return@forEach
                key(shape.id) {
                    Box(
                        Modifier
                            .placeShapeBox(r)
                            .then(selectionModifier(shape, onSelect))
                            .onGloballyPositioned { onBounds(shape.id, it) },
                    )
                }
            }
            }
        }
        is Node.Line, is Node.RectShape, is Node.CircleShape,
        is Node.EllipseShape, is Node.ArcShape -> Unit // drawn by the Canvas parent

        is Node.TextField -> InteractiveNode(node, onSelect, onBounds, scopeModifier) { m ->
            OutlinedTextField(
                value = node.value,
                onValueChange = {},
                label = if (node.placeholder.isNotEmpty()) ({ Text(node.placeholder) }) else null,
                modifier = m,
            )
        }
        is Node.Switch -> InteractiveNode(node, onSelect, onBounds, scopeModifier) { m ->
            Switch(checked = node.checked, onCheckedChange = {}, modifier = m)
        }
        is Node.Checkbox -> InteractiveNode(node, onSelect, onBounds, scopeModifier) { m ->
            Checkbox(checked = node.checked, onCheckedChange = {}, modifier = m)
        }
        is Node.RadioButton -> InteractiveNode(node, onSelect, onBounds, scopeModifier) { m ->
            RadioButton(selected = node.selected, onClick = {}, modifier = m)
        }
        is Node.Slider -> InteractiveNode(node, onSelect, onBounds, scopeModifier) { m ->
            Slider(value = node.value, onValueChange = {}, modifier = m)
        }
        is Node.CircularProgress -> CircularProgressIndicator(modifier = modifier)
        is Node.LinearProgress -> LinearProgressIndicator(modifier = modifier)
        // weight() is a Column/Row scope member, so it's applied here per child.
        is Node.Column -> Column(
            modifier = modifier,
            verticalArrangement = if (node.spacing > 0) Arrangement.spacedBy(node.spacing.dp) else node.verticalArrangement.toCompose(),
            horizontalAlignment = node.horizontalAlignment.toCompose(),
        ) {
            node.children.forEach { child ->
                var sm: Modifier = Modifier
                child.weightValue()?.let { sm = sm.weight(it) }
                child.alignHorizontal()?.let { sm = sm.align(it.toCompose()) }
                RenderNode(child, selectedId, onSelect, onBounds, sm)
            }
        }
        is Node.Row -> Row(
            modifier = modifier,
            horizontalArrangement = if (node.spacing > 0) Arrangement.spacedBy(node.spacing.dp) else node.horizontalArrangement.toCompose(),
            verticalAlignment = node.verticalAlignment.toCompose(),
        ) {
            node.children.forEach { child ->
                var sm: Modifier = Modifier
                child.weightValue()?.let { sm = sm.weight(it) }
                child.alignVertical()?.let { sm = sm.align(it.toCompose()) }
                RenderNode(child, selectedId, onSelect, onBounds, sm)
            }
        }
        is Node.Box -> Box(modifier = modifier, contentAlignment = node.contentAlignment.toCompose()) {
            node.children.forEach { child ->
                val sm = child.alignBox()?.let { Modifier.align(it.toCompose()) } ?: Modifier
                RenderNode(child, selectedId, onSelect, onBounds, sm)
            }
        }
        is Node.Card -> Card(modifier = modifier) { node.children.forEach { RenderNode(it, selectedId, onSelect, onBounds) } }
        is Node.Fab -> InteractiveNode(node, onSelect, onBounds, scopeModifier) { m ->
            FloatingActionButton(onClick = {}, modifier = m) {
                node.children.forEach { RenderNode(it, selectedId, onSelect, onBounds) }
            }
        }
        // Modal components are previewed inline (as a surface) so their content stays
        // editable in the canvas's coordinate space; codegen wraps them in the real Dialog/ModalBottomSheet.
        // A real Dialog is centered over the screen by the system; mirror that by
        // centering the preview Surface in the frame (the selection bounds stay on the Surface).
        is Node.Dialog -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(
                modifier = modifier,
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    node.children.forEach { child ->
                        var sm: Modifier = Modifier
                        child.weightValue()?.let { sm = sm.weight(it) }
                        child.alignHorizontal()?.let { sm = sm.align(it.toCompose()) }
                        RenderNode(child, selectedId, onSelect, onBounds, sm)
                    }
                }
            }
        }
        // A ModalBottomSheet docks to the bottom of the screen — align the preview to the bottom.
        is Node.BottomSheet -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                tonalElevation = 2.dp,
                shadowElevation = 8.dp,
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .size(width = 32.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0x33808080)),
                    )
                    node.children.forEach { child ->
                        var sm: Modifier = Modifier
                        child.weightValue()?.let { sm = sm.weight(it) }
                        child.alignHorizontal()?.let { sm = sm.align(it.toCompose()) }
                        RenderNode(child, selectedId, onSelect, onBounds, sm)
                    }
                }
            }
        }
        is Node.Scaffold -> Scaffold(
            modifier = modifier,
            // Slots are permanent Slot containers; render only non-empty ones so an
            // empty slot contributes nothing to the preview (matches codegen).
            topBar = { node.topBar?.takeIf { it.childNodes().isNotEmpty() }?.let { RenderNode(it, selectedId, onSelect, onBounds) } },
            bottomBar = { node.bottomBar?.takeIf { it.childNodes().isNotEmpty() }?.let { RenderNode(it, selectedId, onSelect, onBounds) } },
            floatingActionButton = { node.fab?.takeIf { it.childNodes().isNotEmpty() }?.let { RenderNode(it, selectedId, onSelect, onBounds) } },
        ) { innerPadding ->
            // Parsed content retains an ordered ScaffoldPadding marker. Content
            // created in the designer has no marker, so preserve the historical
            // automatic prefix that keeps it clear of Scaffold bars.
            node.children.forEach { child ->
                val hasAuthoredPadding = child.modifier.previewSpecs().any { it is ModifierSpec.ScaffoldPadding }
                RenderNode(
                    child,
                    selectedId,
                    onSelect,
                    onBounds,
                    scopeModifier = if (hasAuthoredPadding) Modifier else Modifier.padding(innerPadding),
                    scaffoldPadding = innerPadding,
                )
            }
        }
        is Node.TopAppBar -> {
            val titleSlot: @Composable () -> Unit = { node.title?.let { RenderNode(it, selectedId, onSelect, onBounds) } }
            val navSlot: @Composable () -> Unit = { node.navigationIcon?.let { RenderNode(it, selectedId, onSelect, onBounds) } }
            val actionsSlot: @Composable RowScope.() -> Unit = { node.actions.forEach { RenderNode(it, selectedId, onSelect, onBounds) } }
            when (node.variant) {
                TopAppBarVariant.Small -> TopAppBar(title = titleSlot, navigationIcon = navSlot, actions = actionsSlot, modifier = modifier)
                TopAppBarVariant.CenterAligned -> CenterAlignedTopAppBar(title = titleSlot, navigationIcon = navSlot, actions = actionsSlot, modifier = modifier)
                TopAppBarVariant.Medium -> MediumTopAppBar(title = titleSlot, navigationIcon = navSlot, actions = actionsSlot, modifier = modifier)
                TopAppBarVariant.Large -> LargeTopAppBar(title = titleSlot, navigationIcon = navSlot, actions = actionsSlot, modifier = modifier)
            }
        }
        // A Slot = a slot-argument scope; preview its children in a plain box (its
        // parent's slot lambda positions it). Selectable so users can target it.
        is Node.Slot -> Box(modifier = modifier) { node.children.forEach { RenderNode(it, selectedId, onSelect, onBounds) } }
        // A Composable = function scope; it hugs children unless one of them fills
        // the bounded preview surface. ScreenFrame normally renders its children
        // directly, but keep this defensive branch consistent with that behavior.
        is Node.Composable -> Box(modifier = modifier) { node.children.forEach { RenderNode(it, selectedId, onSelect, onBounds) } }
        // The artboard is never rendered as a node — the canvas lays out each
        // screen's frame itself (App.kt). Render nothing defensively.
        is Node.Artboard -> Unit
    }
}

private fun Node.weightValue(): Float? =
    modifier.previewSpecs().firstNotNullOfOrNull { (it as? ModifierSpec.Weight)?.value }?.takeIf { it > 0f }

// align() is a scope member like weight — read per scope kind at the container's
// call site (mirrors codegen's projectAlign: mismatched fields are ignored).
private fun Node.alignBox(): BoxAlignment? =
    modifier.previewSpecs().firstNotNullOfOrNull { (it as? ModifierSpec.Align)?.box }

private fun Node.alignVertical(): VAlignment? =
    modifier.previewSpecs().firstNotNullOfOrNull { (it as? ModifierSpec.Align)?.vertical }

private fun Node.alignHorizontal(): HAlignment? =
    modifier.previewSpecs().firstNotNullOfOrNull { (it as? ModifierSpec.Align)?.horizontal }

private fun IconKind.symbolName() = when (this) {
    IconKind.Menu -> "menu"
    IconKind.Search -> "search"
    IconKind.Home -> "home"
    IconKind.Settings -> "settings"
    IconKind.Favorite -> "favorite"
    IconKind.Star -> "star"
    IconKind.Add -> "add"
    IconKind.Close -> "close"
    IconKind.Check -> "check"
    IconKind.Delete -> "delete"
    IconKind.Edit -> "edit"
    IconKind.Share -> "share"
    IconKind.Notifications -> "notifications"
    IconKind.Person -> "person"
    IconKind.Info -> "info"
    IconKind.MoreVert -> "more_vert"
    IconKind.Email -> "mail"
    IconKind.Lock -> "lock"
}

private fun sourceIconName(expression: String): String =
    expression.substringAfterLast('.').substringBefore(')').substringBefore('(')
        .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
        .lowercase()

private fun VArrangement.toCompose(): Arrangement.Vertical = when (this) {
    VArrangement.Top -> Arrangement.Top
    VArrangement.Bottom -> Arrangement.Bottom
    VArrangement.Center -> Arrangement.Center
    VArrangement.SpaceBetween -> Arrangement.SpaceBetween
    VArrangement.SpaceAround -> Arrangement.SpaceAround
    VArrangement.SpaceEvenly -> Arrangement.SpaceEvenly
}

private fun HArrangement.toCompose(): Arrangement.Horizontal = when (this) {
    HArrangement.Start -> Arrangement.Start
    HArrangement.End -> Arrangement.End
    HArrangement.Center -> Arrangement.Center
    HArrangement.SpaceBetween -> Arrangement.SpaceBetween
    HArrangement.SpaceAround -> Arrangement.SpaceAround
    HArrangement.SpaceEvenly -> Arrangement.SpaceEvenly
}

private fun HAlignment.toCompose(): Alignment.Horizontal = when (this) {
    HAlignment.Start -> Alignment.Start
    HAlignment.Center -> Alignment.CenterHorizontally
    HAlignment.End -> Alignment.End
}

private fun VAlignment.toCompose(): Alignment.Vertical = when (this) {
    VAlignment.Top -> Alignment.Top
    VAlignment.Center -> Alignment.CenterVertically
    VAlignment.Bottom -> Alignment.Bottom
}

private fun TextWeight.toCompose(): FontWeight = when (this) {
    TextWeight.Normal -> FontWeight.Normal
    TextWeight.Light -> FontWeight.Light
    TextWeight.Medium -> FontWeight.Medium
    TextWeight.SemiBold -> FontWeight.SemiBold
    TextWeight.Bold -> FontWeight.Bold
    TextWeight.ExtraBold -> FontWeight.ExtraBold
    TextWeight.Black -> FontWeight.Black
}

private fun TextAlignment.toCompose(): TextAlign = when (this) {
    TextAlignment.Start -> TextAlign.Start
    TextAlignment.Center -> TextAlign.Center
    TextAlignment.End -> TextAlign.End
    TextAlignment.Justify -> TextAlign.Justify
}

private fun TextFontFamily.toCompose(): FontFamily? = when (this) {
    TextFontFamily.Default -> null
    TextFontFamily.SansSerif -> FontFamily.SansSerif
    TextFontFamily.Serif -> FontFamily.Serif
    TextFontFamily.Monospace -> FontFamily.Monospace
    TextFontFamily.Cursive -> FontFamily.Cursive
}

private fun BoxAlignment.toCompose(): Alignment = when (this) {
    BoxAlignment.TopStart -> Alignment.TopStart
    BoxAlignment.TopCenter -> Alignment.TopCenter
    BoxAlignment.TopEnd -> Alignment.TopEnd
    BoxAlignment.CenterStart -> Alignment.CenterStart
    BoxAlignment.Center -> Alignment.Center
    BoxAlignment.CenterEnd -> Alignment.CenterEnd
    BoxAlignment.BottomStart -> Alignment.BottomStart
    BoxAlignment.BottomCenter -> Alignment.BottomCenter
    BoxAlignment.BottomEnd -> Alignment.BottomEnd
}

/**
 * Outermost modifier: tap-to-select (non-Button). The selection *outline* is the
 * {SelectionOverlay} drawn in frame space (App.kt) — not a per-node border — so it
 * sits cleanly outside the component and isn't clipped at the artboard edge.
 */
private fun selectionModifier(node: Node, onSelect: (id: String, deep: Boolean) -> Unit): Modifier {
    var m: Modifier = Modifier
    if (!node.isInteractive()) {
        // Single tap → select the top-level component. Drilling into children is handled
        // by the SelectionOverlay (tapping a selected parent drills one level deeper), so
        // no onDoubleTap here — that kept onTap waiting ~300ms and split the double-tap
        // across two gesture detectors once the overlay appeared.
        m = m.pointerInput(node.id) {
            detectTapGestures(onTap = { onSelect(node.id, false) })
        }
    }
    return m
}

/**
 * Interactive Material components (Button, Switch, Slider, …) have their own
 * click/toggle/drag/focus gesture that consumes the pointer before our selection
 * gesture can — so we never wrap them with [selectionModifier]; [InteractiveNode]
 * overlays a tap catcher on top instead.
 */
private fun Node.isInteractive(): Boolean = this is Node.Button || this is Node.Fab || this is Node.Chip ||
    this is Node.IconButton || this is Node.Switch || this is Node.Checkbox ||
    this is Node.RadioButton || this is Node.Slider || this is Node.TextField

/**
 * Renders an interactive [node] with a transparent tap-capturing overlay on top, so
 * a single tap selects it and a double tap drills in — the component's own gesture
 * (which would otherwise swallow the pointer) is left visually intact but inert.
 * The node's real modifier chain (size, background, …) is applied to [content].
 */
@Composable
private fun InteractiveNode(
    node: Node,
    onSelect: (id: String, deep: Boolean) -> Unit,
    onBounds: (String, LayoutCoordinates) -> Unit,
    scopeModifier: Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    // Split the modifier: positional Offset goes on the wrapper Box (so the tap overlay
    // and measured bounds move with the component), everything else (size, background, …)
    // styles the inner component. Otherwise a moved component's hit-area lagged its visual.
    val scheme = MaterialTheme.colorScheme
    val previewModifiers = node.modifier.previewSpecs()
    val offsetMod = previewModifiers.filterIsInstance<ModifierSpec.Offset>().toModifier(scheme)
    val innerMod = previewModifiers.filterNot { it is ModifierSpec.Offset }.toModifier(scheme)
    Box(modifier = scopeModifier.then(offsetMod).onGloballyPositioned { onBounds(node.id, it) }) {
        content(innerMod)
        Box(
            Modifier.matchParentSize().pointerInput(node.id) {
                awaitEachGesture {
                    // Consume the DOWN immediately. The overlay is the top sibling, so it
                    // processes the press first in the Main pass; consuming it here means the
                    // component's own clickable/toggle/drag (a lower sibling) sees it already
                    // consumed and never fires. detectTapGestures doesn't consume the down
                    // early enough — that's why Buttons in particular swallowed the tap.
                    awaitFirstDown().consume()
                    val up = waitForUpOrCancellation()
                    if (up != null) {
                        up.consume()
                        onSelect(node.id, false)
                    }
                }
            },
        )
    }
}

/**
 * Map a [DesignTheme] to a real Material3 [ColorScheme] for the live preview.
 * Mirrors codegen: start from the light/dark builder, then override only the tokens
 * the user changed from the baseline-light default — so the preview matches the
 * generated `MaterialTheme(colorScheme = …)` exactly.
 */
fun DesignTheme.toColorScheme(): ColorScheme {
    var s = if (dark) darkColorScheme() else lightColorScheme()
    for ((name, value) in changedTokens()) {
        val c = Color(value)
        s = when (name) {
            "primary" -> s.copy(primary = c)
            "onPrimary" -> s.copy(onPrimary = c)
            "secondary" -> s.copy(secondary = c)
            "tertiary" -> s.copy(tertiary = c)
            "error" -> s.copy(error = c)
            "background" -> s.copy(background = c)
            "onBackground" -> s.copy(onBackground = c)
            "surface" -> s.copy(surface = c)
            "onSurface" -> s.copy(onSurface = c)
            else -> s
        }
    }
    return s
}

/** [ModifierSpec] corner → Shape: dp or percent-of-smaller-side (50% = circle). */
private fun cornerShape(corner: Int, unit: CornerUnit): Shape = when {
    corner <= 0 -> RectangleShape
    unit == CornerUnit.Percent -> RoundedCornerShape(corner.coerceAtMost(50))
    else -> RoundedCornerShape(corner.dp)
}

/** Resolve a model color — a [ThemeColorRef] token follows [scheme]; else a literal. */
fun themeColor(value: Long, scheme: ColorScheme): Color = when (ThemeColorRef.tokenName(value)) {
    null -> Color(value)
    "primary" -> scheme.primary
    "onPrimary" -> scheme.onPrimary
    "secondary" -> scheme.secondary
    "tertiary" -> scheme.tertiary
    "error" -> scheme.error
    "background" -> scheme.background
    "onBackground" -> scheme.onBackground
    "surface" -> scheme.surface
    "onSurface" -> scheme.onSurface
    else -> scheme.primary
}

/**
 * Fold a modifier-spec chain into a real [Modifier], preserving order. [scheme]
 * resolves theme-token color references, so token-colored fills/borders/shadows
 * re-color live when the design theme changes.
 */
fun List<ModifierSpec>.toModifier(scheme: ColorScheme, scaffoldPadding: PaddingValues? = null): Modifier =
    fold(Modifier as Modifier) { acc, spec ->
        when (spec) {
            is ModifierSpec.External -> if (spec.opaque) {
                acc.then(spec.preview.toModifier(scheme, scaffoldPadding))
            } else {
                acc // source parameter; preview uses its default Modifier value
            }
            is ModifierSpec.ScaffoldPadding -> if (scaffoldPadding != null) acc.padding(scaffoldPadding) else acc
            is ModifierSpec.Padding -> when (spec.mode) {
                PaddingMode.All -> acc.padding(spec.all.dp)
                PaddingMode.Symmetric -> acc.padding(horizontal = spec.horizontal.dp, vertical = spec.vertical.dp)
                PaddingMode.Sides -> acc.padding(start = spec.start.dp, top = spec.top.dp, end = spec.end.dp, bottom = spec.bottom.dp)
            }
            is ModifierSpec.Size -> acc.size(spec.width.dp, spec.height.dp)
            is ModifierSpec.Width -> acc.width(spec.width.dp)
            is ModifierSpec.Height -> acc.height(spec.height.dp)
            is ModifierSpec.Offset -> acc.offset(spec.x.dp, spec.y.dp)
            is ModifierSpec.Background -> {
                val shape = cornerShape(spec.corner, spec.cornerUnit)
                val stops = spec.gradientStops()
                if (stops.isNotEmpty()) {
                    val colors = stops.map { themeColor(it, scheme) }
                    val brush = when (spec.direction) {
                        GradientDirection.Vertical -> Brush.verticalGradient(colors)
                        GradientDirection.Horizontal -> Brush.horizontalGradient(colors)
                        GradientDirection.Diagonal -> Brush.linearGradient(colors)
                        GradientDirection.Radial -> Brush.radialGradient(colors)
                    }
                    acc.background(brush, shape)
                } else {
                    acc.background(themeColor(spec.color, scheme), shape)
                }
            }
            is ModifierSpec.Weight -> acc // applied at the Row/Column call site, not here
            is ModifierSpec.AspectRatio -> acc.aspectRatio(spec.width.toFloat() / spec.height.coerceAtLeast(1).toFloat())
            is ModifierSpec.Clip -> acc.clip(cornerShape(spec.corner, spec.cornerUnit))
            is ModifierSpec.Alpha -> acc.alpha(spec.value)
            is ModifierSpec.Border -> acc.border(
                spec.width.dp,
                themeColor(spec.color, scheme),
                cornerShape(spec.corner, spec.cornerUnit),
            )
            // The real Compose 1.9+ shadow APIs — the preview now runs the exact
            // modifier the generated code emits (no more Skia emulation).
            is ModifierSpec.DropShadow -> acc.dropShadow(
                cornerShape(spec.corner, spec.cornerUnit),
                Shadow(
                    radius = spec.radius.dp,
                    color = themeColor(spec.color, scheme),
                    spread = spec.spread.dp,
                    offset = DpOffset(spec.offsetX.dp, spec.offsetY.dp),
                ),
            )
            is ModifierSpec.InnerShadow -> acc.innerShadow(
                cornerShape(spec.corner, spec.cornerUnit),
                Shadow(
                    radius = spec.radius.dp,
                    color = themeColor(spec.color, scheme),
                    spread = spec.spread.dp,
                    offset = DpOffset(spec.offsetX.dp, spec.offsetY.dp),
                ),
            )
            is ModifierSpec.Rotate -> acc.rotate(spec.degrees)
            is ModifierSpec.Scale -> acc.scale(spec.x, spec.y)
            is ModifierSpec.ZIndex -> acc.zIndex(spec.value)
            is ModifierSpec.Blur -> acc.blur(spec.radius.dp)
            is ModifierSpec.Align -> acc // scope member — applied at the container's call site
            is ModifierSpec.FillMaxWidth -> acc.fillMaxWidth(spec.fraction)
            is ModifierSpec.FillMaxHeight -> acc.fillMaxHeight(spec.fraction)
            is ModifierSpec.FillMaxSize -> acc.fillMaxSize(spec.fraction)
        }
    }

/** Flatten safe static projections from opaque source chains for scope handling. */
private fun List<ModifierSpec>.previewSpecs(): List<ModifierSpec> = flatMap { spec ->
    if (spec is ModifierSpec.External && spec.opaque) spec.preview.previewSpecs() else listOf(spec)
}


/** A canvas shape's drawn extent in dp: (x, y, w, h); null for unknown nodes. */
private fun shapeHitRect(shape: Node): ShapeRect? = when (shape) {
    is Node.Line -> {
        // Pad thin lines so they stay clickable (at least an ~8dp band).
        val pad = maxOf(shape.strokeWidth / 2, 4)
        ShapeRect(
            minOf(shape.x1, shape.x2) - pad,
            minOf(shape.y1, shape.y2) - pad,
            kotlin.math.abs(shape.x2 - shape.x1) + pad * 2,
            kotlin.math.abs(shape.y2 - shape.y1) + pad * 2,
        )
    }
    is Node.RectShape -> ShapeRect(shape.x, shape.y, shape.width, shape.height)
    is Node.CircleShape -> ShapeRect(shape.cx - shape.radius, shape.cy - shape.radius, shape.radius * 2, shape.radius * 2)
    is Node.EllipseShape -> ShapeRect(shape.x, shape.y, shape.width, shape.height)
    is Node.ArcShape -> ShapeRect(shape.x, shape.y, shape.width, shape.height)
    else -> null
}

private class ShapeRect(val x: Int, val y: Int, val w: Int, val h: Int)

/**
 * Measure at the shape's fixed extent, report 0×0, place at the shape's offset —
 * free placement inside the Canvas box that can't grow the canvas or be clamped.
 */
private fun Modifier.placeShapeBox(r: ShapeRect): Modifier = layout { measurable, _ ->
    val placeable = measurable.measure(
        Constraints.fixed(r.w.dp.roundToPx().coerceAtLeast(1), r.h.dp.roundToPx().coerceAtLeast(1)),
    )
    layout(0, 0) { placeable.place(r.x.dp.roundToPx(), r.y.dp.roundToPx()) }
}
