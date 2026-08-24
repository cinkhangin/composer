package composer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import composer.codegen.CodeGen
import composer.ui.HDivider
import composer.ui.Theme
import composer.ui.Tk
import composer.ui.ToolButton
import composer.ui.highlightKotlin
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

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
                "Combined source",
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
                    snapshotFlow { Triple(sync.field.selection, sync.field.text.length, state.codeEditorFocused) }
                        .collect { (_, _, focused) ->
                            // Mounting Code view must not force a full-text caret
                            // layout and scroll before the user interacts with it.
                            if (!focused) return@collect
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
