package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.onEach
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import composer.model.DesignTheme
import composer.model.ThemeColorRef
import composer.ui.AppIconKind
import composer.ui.LocalThemeSwatches
import composer.ui.ThemeSwatch
import composer.ui.Tk

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
                .background(Tk.panel)
                .focusRequester(focusRequester)
                .onKeyEvent { handleShortcut(it, state) }
                .focusable(),
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
            ) {
                if (state.leftPanelOpen) {
                    Column(Modifier.width(232.dp).fillMaxHeight().workspaceSurface(divider = WorkspaceDivider.Right)) {
                        TreeView(state, onCollapse = state::toggleLeftPanel)
                    }
                } else {
                    CollapsedPanelStrip(
                        AppIconKind.ExpandLeft,
                        "Show layers",
                        WorkspaceDivider.Right,
                        state::toggleLeftPanel,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .workspaceSurface(color = if (state.showCode) Tk.codeBg else Tk.canvasBg)
                        .then(
                            if (state.showCode) Modifier else Modifier.pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    runCatching { focusRequester.requestFocus() }
                                }
                            },
                        ),
                ) {
                    if (state.showCode) {
                        CodePanel(state, codeSync)
                    } else {
                        Canvas(state = state, appMode = false, magnification = null)
                    }
                }
                if (state.rightPanelOpen) {
                    Column(Modifier.width(232.dp).fillMaxHeight().workspaceSurface(divider = WorkspaceDivider.Left)) {
                        Inspector(state, onCollapse = state::toggleRightPanel)
                    }
                } else {
                    CollapsedPanelStrip(
                        AppIconKind.ExpandRight,
                        "Show inspector",
                        WorkspaceDivider.Left,
                        state::toggleRightPanel,
                    )
                }
            }
        }
    }
}
