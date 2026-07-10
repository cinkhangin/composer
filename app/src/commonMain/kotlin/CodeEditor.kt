package composer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import composer.codegen.CodeGen
import composer.codeparse.DesignParser
import composer.codeparse.ParsedDesign
import composer.model.Node
import composer.res.Res
import composer.res.jetbrainsmono_regular
import composer.ui.HDivider
import composer.ui.Theme
import composer.ui.Tk
import composer.ui.ToolButton
import composer.ui.highlightKotlin
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.Font

/**
 * The editable code view: type or paste Compose code and the design updates —
 * the inverse projection of CodeGen, powered by the same DesignParser as the
 * IDE plugin. Statements outside the supported grammar become locked RawCode
 * nodes (never dropped); code outside screen functions isn't representable and
 * is surfaced via a notice.
 *
 * Sync-loop design (the EmbeddedBridge noteLoaded/lastLoaded pattern, with
 * generated code as the canonical encoding): [CodeSyncState.lastSynced] is
 * always `CodeGen.generate(root)` of the tree the buffer currently REPRESENTS.
 *  - user types → debounced parse → [EditorState.applyCodeEdit] → lastSynced
 *    updated from the tree AS APPLIED → the root-change echo compares equal and
 *    the user's typed formatting is left alone;
 *  - inspector/layers/undo edit → generated != lastSynced → the buffer resets
 *    to canonical code (the tree wins);
 *  - programmatic buffer resets parse back to `text == lastSynced` → no-op, so
 *    external edits never cause id churn or spurious undo steps.
 */
class CodeSyncState {
    var field by mutableStateOf(TextFieldValue(""))

    /** Canonical generation of the tree the buffer reflects — the echo guard. */
    var lastSynced: String? = null

    var parseError by mutableStateOf<String?>(null)

    /** The buffer holds top-level code regeneration would drop (helpers, classes…). */
    var dropNotice by mutableStateOf(false)
}

@Composable
internal fun jetBrainsMono(): FontFamily = FontFamily(Font(Res.font.jetbrainsmono_regular))

@OptIn(FlowPreview::class)
@Composable
fun CodePanel(state: EditorState, sync: CodeSyncState, modifier: Modifier = Modifier) {
    // (Re)entry: seed the buffer from the tree unless it already represents it —
    // typed formatting survives a Design↔Code round trip with no tree changes.
    LaunchedEffect(Unit) {
        val gen = CodeGen.generate(state.root)
        if (sync.lastSynced != gen) {
            sync.field = TextFieldValue(gen)
            sync.lastSynced = gen
            sync.parseError = null
        }
    }

    // Tree → text: inspector/layers/undo edits while the code view is open.
    LaunchedEffect(state, sync) {
        snapshotFlow { state.root }
            .drop(1)
            .debounce(100) // coalesce drag storms (color picker, screen moves)
            .collect { root ->
                val gen = CodeGen.generate(root)
                if (gen != sync.lastSynced) { // echo of our own parse-apply keeps the buffer
                    sync.field = TextFieldValue(gen)
                    sync.lastSynced = gen
                    sync.parseError = null
                }
            }
    }

    // Text → tree: typing/pasting, debounced.
    LaunchedEffect(state, sync) {
        snapshotFlow { sync.field.text }
            .drop(1)
            .debounce(500)
            .collect { text -> parseAndApply(state, sync, text) }
    }

    // Entering the code view focuses the editor — type immediately, no click
    // needed. On a fresh page load the canvas has no DOM focus yet and the first
    // request can fizzle, so grab browser focus and retry briefly until it sticks.
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(10) {
            focusComposeCanvas()
            runCatching { fieldFocus.requestFocus() }
            if (state.codeEditorFocused) return@LaunchedEffect
            delay(50)
        }
    }

    // Leaving the code view within the debounce window still lands the edit.
    DisposableEffect(state, sync) {
        onDispose {
            if (sync.field.text != sync.lastSynced) parseAndApply(state, sync, sync.field.text)
            state.codeEditorFocused = false
        }
    }

    val codeFont = jetBrainsMono()
    val text = sync.field.text
    val lineCount = remember(text) { text.count { it == '\n' } + 1 }
    val gutter = remember(lineCount) {
        val w = lineCount.toString().length
        (1..lineCount).joinToString("\n") { it.toString().padStart(w) }
    }
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val codeStyle = TextStyle(color = Tk.codeText, fontFamily = codeFont, fontSize = 12.5.sp, lineHeight = 19.sp)

    // Highlighting rides a VisualTransformation (text unchanged → identity
    // mapping); memoized so re-layouts don't re-tokenize the whole buffer.
    val dark = Theme.isDark
    val highlight = remember(dark) {
        var cache: Pair<String, TransformedText>? = null
        VisualTransformation { annotated ->
            cache?.takeIf { it.first == annotated.text }?.second
                ?: TransformedText(highlightKotlin(annotated.text, dark), OffsetMapping.Identity)
                    .also { cache = annotated.text to it }
        }
    }

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
            var copied by remember { mutableStateOf(false) }
            LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }
            ToolButton(if (copied) "Copied ✓" else "Copy", onClick = { copyToClipboard(sync.field.text); copied = true })
        }
        HDivider(Modifier.background(Tk.border))
        sync.parseError?.let { CodeBanner(it, danger = true) }
        if (sync.dropNotice) {
            CodeBanner(
                "This file has top-level code besides screen functions (helpers, theme values). " +
                    "It isn't imported into the design and is dropped when the code regenerates.",
                danger = false,
            )
        }
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
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                val minW = maxWidth
                val minH = maxHeight
                // The focus system's automatic bring-into-view targets the WHOLE
                // field (which is content-sized, taller than the viewport), so any
                // click or keystroke deep in a long file yanked the scroll back to
                // the field's top edge. Disable it and follow the caret manually.
                val density = LocalDensity.current
                LaunchedEffect(sync, vScroll) {
                    snapshotFlow { sync.field.selection to sync.field.text.length }
                        .collect {
                            val f = sync.field
                            val line = f.text.take(f.selection.end.coerceIn(0, f.text.length)).count { c -> c == '\n' }
                            val lineHeightPx = with(density) { 19.sp.toPx() }
                            val padPx = with(density) { 16.dp.toPx() }
                            val top = padPx + line * lineHeightPx
                            val bottom = top + lineHeightPx + padPx
                            val viewport = vScroll.viewportSize
                            if (viewport <= 0) return@collect
                            when {
                                bottom > vScroll.value + viewport -> vScroll.scrollTo((bottom - viewport).toInt())
                                top < vScroll.value -> vScroll.scrollTo(top.toInt().coerceAtLeast(0))
                            }
                        }
                }
                @OptIn(ExperimentalFoundationApi::class)
                CompositionLocalProvider(LocalBringIntoViewSpec provides NoBringIntoView) {
                Box(Modifier.fillMaxSize().verticalScroll(vScroll).horizontalScroll(hScroll)) {
                    BasicTextField(
                        value = sync.field,
                        onValueChange = { sync.field = it },
                        textStyle = codeStyle,
                        cursorBrush = SolidColor(Tk.accent),
                        visualTransformation = highlight,
                        modifier = Modifier
                            // Content-sized inside the scroll pair (unbounded width →
                            // no soft wrap); min = viewport so empty-area clicks focus.
                            .defaultMinSize(minWidth = minW, minHeight = minH)
                            .padding(horizontal = 14.dp, vertical = 16.dp)
                            .focusRequester(fieldFocus)
                            .onFocusChanged { state.codeEditorFocused = it.isFocused }
                            .onPreviewKeyEvent { e ->
                                // Tab indents instead of moving focus.
                                if (e.type == KeyEventType.KeyDown && e.key == Key.Tab && !e.isShiftPressed) {
                                    val f = sync.field
                                    sync.field = f.copy(
                                        text = f.text.replaceRange(f.selection.min, f.selection.max, "    "),
                                        selection = TextRange(f.selection.min + 4),
                                    )
                                    true
                                } else false
                            },
                    )
                }
                }
            }
        }
    }
}

/** Disables focus-driven auto-scrolling; the caret follower owns the viewport. */
@OptIn(ExperimentalFoundationApi::class)
private object NoBringIntoView : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

/** Parse the buffer and apply it to the design; keeps the last good tree on failure. */
private fun parseAndApply(state: EditorState, sync: CodeSyncState, text: String) {
    if (text == sync.lastSynced) return // canonical text of the current root — no-op
    val parsed = DesignParser.parse(text)
    if (parsed == null) {
        sync.parseError =
            if (text.isBlank()) "Empty file — the design was kept. Add a @Composable function to define a screen."
            else "No screens found — the design was kept. A screen is a top-level @Composable fun with a { } body."
        return
    }
    val merged = mergeParsed(state.artboard, parsed)
    // Parsed ids always differ from the designer's, so tree equality can't catch
    // a formatting-only edit — canonical generation can: equal output means the
    // same design, and skipping the apply avoids an id-churn commit + auto-save.
    if (CodeGen.generate(merged) != CodeGen.generate(state.root)) {
        state.applyCodeEdit(merged)
    }
    // Record canonical AS APPLIED (post migrate/dedupe), so the root-change echo
    // compares equal and leaves the user's typed formatting alone.
    sync.lastSynced = CodeGen.generate(state.root)
    sync.parseError = null
    sync.dropNotice = parsed.hasNonScreenDeclarations
}

/**
 * Graft editor-only state — canvas geometry, themes, the component registry,
 * pretty layer names — from the [current] artboard onto [parsed]'s structure.
 * Code carries none of it, so a parsed tree arrives with defaults; without this
 * merge every code edit would scatter the screens and reset the theme. Screens
 * match by generated function name first, leftovers by order (handles renames).
 */
internal fun mergeParsed(current: Node.Artboard, parsed: ParsedDesign): Node.Artboard {
    val pArt = parsed.artboard
    val curScreens = current.composables.filterIsInstance<Node.Composable>()
    val curNames = CodeGen.screenFunctionNames(current) // parallel to curScreens
    val pScreens = pArt.composables.filterIsInstance<Node.Composable>()

    // Pass 1: match by function name (a parsed screen's layer name IS its fn name).
    val byName = curScreens.indices.associateBy { curNames[it] }
    val matched = arrayOfNulls<Int>(pScreens.size) // parsed index → current index
    val taken = mutableSetOf<Int>()
    pScreens.forEachIndexed { i, p ->
        byName[pArt.layerNames[p.id]]?.takeIf { taken.add(it) }?.let { matched[i] = it }
    }
    // Pass 2: leftovers pair up in order (a rename keeps its screen's geometry).
    val freeCur = curScreens.indices.filterNot { it in taken }.iterator()
    for (i in pScreens.indices) {
        if (matched[i] == null && freeCur.hasNext()) matched[i] = freeCur.next()
    }

    val idMap = mutableMapOf<String, String>() // current screen id → parsed id
    val merged = pScreens.mapIndexed { i, p ->
        val old = matched[i]?.let(curScreens::get) ?: return@mapIndexed p
        idMap[old.id] = p.id
        p.copy(x = old.x, y = old.y, width = old.width, height = old.height)
    }.toMutableList()
    // Truly-new screens: place right of everything kept (the parser's defaults
    // would overlap existing screens).
    var nextX = merged.filterIndexed { i, _ -> matched[i] != null }
        .maxOfOrNull { it.x + it.width + 60 } ?: 0
    for (i in merged.indices) {
        if (matched[i] == null) {
            merged[i] = merged[i].copy(x = nextX, y = 0)
            nextX += merged[i].width + 60
        }
    }

    // Pretty layer names survive when they still sanitize to the parsed fn name
    // ("Login Screen" stays "Login Screen" instead of becoming "LoginScreen").
    val names = pArt.layerNames.toMutableMap()
    idMap.forEach { (oldId, newId) ->
        val pretty = current.layerNames[oldId] ?: return@forEach
        val curIdx = curScreens.indexOfFirst { it.id == oldId }
        if (curIdx >= 0 && curNames[curIdx] == names[newId]) names[newId] = pretty
    }

    return current.copy( // keeps the artboard id, themes, and activeTheme
        composables = merged,
        layerNames = names,
        componentIds = (pArt.componentIds + current.componentIds.mapNotNull { idMap[it] }).distinct(),
    )
}

/** Slim in-panel banner (SaveErrorBanner is App-file-private; same visual recipe). */
@Composable
private fun CodeBanner(message: String, danger: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (danger) Tk.dangerSoft else Tk.panelAlt)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        BasicText(
            message,
            style = TextStyle(color = if (danger) Tk.danger else Tk.textSecondary, fontSize = 12.sp),
        )
    }
}
