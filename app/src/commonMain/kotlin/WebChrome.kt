package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import composer.ui.AppIconKind
import composer.ui.SquareIconButton
import composer.ui.Theme
import composer.ui.Tk

/** Website-only document toolbar. Android Studio keeps its smaller host-owned toolbar. */
@Composable
internal fun WebToolbar(state: EditorState, ws: Workspace) {
    Box(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WebFileMenu(state, ws)
            WebProjectTitle(ws)
            WebSaveStatus(ws)
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScreenSizeControl(state)
            Box(Modifier.width(1.dp).height(20.dp).background(Tk.border))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                WebViewSegment("Design", AppIconKind.Design, !state.showCode) { state.setCodeView(false) }
                WebViewSegment("Code", AppIconKind.Code, state.showCode) { state.setCodeView(true) }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SquareIconButton(AppIconKind.Undo, enabled = state.canUndo, tip = "Undo") { state.undo() }
            SquareIconButton(AppIconKind.Redo, enabled = state.canRedo, tip = "Redo") { state.redo() }
            SquareIconButton(
                if (Theme.isDark) AppIconKind.Sun else AppIconKind.Moon,
                tip = if (Theme.isDark) "Light mode" else "Dark mode",
                onClick = Theme::toggle,
            )
            WebExportMenu(state)
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Tk.accentSoft)
                    .border(1.dp, Tk.border, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("G", style = TextStyle(Tk.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}
