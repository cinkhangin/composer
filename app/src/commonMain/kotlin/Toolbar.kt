package composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import composer.ui.AppIconKind
import composer.ui.Theme
import composer.ui.ToolButton

/**
 * Plugin toolbar: local design history and preview theme. Android Studio owns
 * files, source code, export, and project identity.
 */
@Composable
internal fun Toolbar(
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
