package composer

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import composer.ui.Tk

@Composable
internal fun WebSaveStatus(ws: Workspace) {
    val (label, color) = when (ws.saveStatus) {
        SaveStatus.Saved -> "Saved" to Tk.textMuted
        SaveStatus.Saving -> "Saving…" to Tk.textSecondary
        SaveStatus.Error -> "Save failed" to Tk.danger
    }
    BasicText(label, style = TextStyle(color, fontSize = 11.5.sp))
}
