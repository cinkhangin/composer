package composer.render

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import composer.model.BoxAlignment
import composer.model.ButtonVariant
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
) {
    // Split the chain so the selection outline wraps the node's FULL box: the positional
    // Offset stays outermost (so a moved node's outline/hit-area track it), but the bounds
    // measurement + tap hit-area sit OUTSIDE the node's own box modifiers (padding, size,
    // background). Otherwise the innermost onGloballyPositioned measured the content area
    // *inside* the padding, so a padded container's outline hugged its content, not its box.
    val scheme = MaterialTheme.colorScheme
    val offsetMod = node.modifier.filterIsInstance<ModifierSpec.Offset>().toModifier(scheme)
    val innerMod = node.modifier.filterNot { it is ModifierSpec.Offset }.toModifier(scheme)
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
        // Opaque preserved code (IDE plugin): a locked chip — selectable so it can be
        // seen/arranged, but its content is only editable in the code editor.
        is Node.RawCode -> {
            val label = node.code.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "Code"
            Row(
                modifier = modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SymbolIcon("code", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
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
                else -> Image(
                    painter = ColorPainter(Color(node.placeholderColor)),
                    contentDescription = node.contentDescription.ifBlank { null },
                    modifier = modifier,
                )
            }
        }
        is Node.Divider -> HorizontalDivider(modifier = modifier)
        is Node.Icon -> Icon(node.icon.toVector(), contentDescription = node.contentDescription.ifBlank { null }, modifier = modifier)
        is Node.IconButton -> InteractiveNode(node, onSelect, onBounds, scopeModifier) { m ->
            IconButton(onClick = {}, modifier = m) { Icon(node.icon.toVector(), contentDescription = null) }
        }
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
            // Content applies the Scaffold's innerPadding (clears the bars) — no implicit wrapper node.
            node.children.forEach { child ->
                RenderNode(child, selectedId, onSelect, onBounds, scopeModifier = Modifier.padding(innerPadding))
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
        // A Composable = function scope; preview its children in a fill-size box.
        is Node.Composable -> Box(modifier = modifier.fillMaxSize()) { node.children.forEach { RenderNode(it, selectedId, onSelect, onBounds) } }
        // The artboard is never rendered as a node — the canvas lays out each
        // screen's frame itself (App.kt). Render nothing defensively.
        is Node.Artboard -> Unit
    }
}

private fun Node.weightValue(): Float? =
    modifier.firstNotNullOfOrNull { (it as? ModifierSpec.Weight)?.value }?.takeIf { it > 0f }

// align() is a scope member like weight — read per scope kind at the container's
// call site (mirrors codegen's projectAlign: mismatched fields are ignored).
private fun Node.alignBox(): BoxAlignment? =
    modifier.firstNotNullOfOrNull { (it as? ModifierSpec.Align)?.box }

private fun Node.alignVertical(): VAlignment? =
    modifier.firstNotNullOfOrNull { (it as? ModifierSpec.Align)?.vertical }

private fun Node.alignHorizontal(): HAlignment? =
    modifier.firstNotNullOfOrNull { (it as? ModifierSpec.Align)?.horizontal }

private fun IconKind.toVector() = when (this) {
    IconKind.Menu -> Icons.Default.Menu
    IconKind.Search -> Icons.Default.Search
    IconKind.Home -> Icons.Default.Home
    IconKind.Settings -> Icons.Default.Settings
    IconKind.Favorite -> Icons.Default.Favorite
    IconKind.Star -> Icons.Default.Star
    IconKind.Add -> Icons.Default.Add
    IconKind.Close -> Icons.Default.Close
    IconKind.Check -> Icons.Default.Check
    IconKind.Delete -> Icons.Default.Delete
    IconKind.Edit -> Icons.Default.Edit
    IconKind.Share -> Icons.Default.Share
    IconKind.Notifications -> Icons.Default.Notifications
    IconKind.Person -> Icons.Default.Person
    IconKind.Info -> Icons.Default.Info
    IconKind.MoreVert -> Icons.Default.MoreVert
    IconKind.Email -> Icons.Default.Email
    IconKind.Lock -> Icons.Default.Lock
}

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
private fun Node.isInteractive(): Boolean = this is Node.Button || this is Node.Fab ||
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
    val offsetMod = node.modifier.filterIsInstance<ModifierSpec.Offset>().toModifier(scheme)
    val innerMod = node.modifier.filterNot { it is ModifierSpec.Offset }.toModifier(scheme)
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
fun List<ModifierSpec>.toModifier(scheme: ColorScheme): Modifier =
    fold(Modifier as Modifier) { acc, spec ->
        when (spec) {
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
