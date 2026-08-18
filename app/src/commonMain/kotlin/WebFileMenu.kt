package composer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import composer.model.DesignJson
import composer.ui.BrandLogo
import composer.ui.HDivider
import composer.ui.Tk
import composer.ui.TkMenu
import composer.ui.TkMenuItem

@Composable
internal fun WebFileMenu(state: EditorState, ws: Workspace) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(Tk.rSm)).clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            BrandLogo(Modifier.size(28.dp))
        }
        TkMenu(expanded = open, onDismissRequest = { open = false }) {
            TkMenuItem("New design") { open = false; ws.newDesign() }
            TkMenuItem("Save") { open = false; ws.saveOverwriting(state.root) }
            TkMenuItem("Import JSON…") {
                open = false
                importTextFile(".json,application/json") { text ->
                    runCatching { DesignJson.decode(text) }
                        .onSuccess { ws.importError = null; state.load(it) }
                        .onFailure { ws.importError = "That file isn't a valid Composer design JSON." }
                }
            }
            HDivider()
            TkMenuItem("Back to home") { open = false; ws.home() }
            BasicText(
                "Composer v$APP_VERSION",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = TextStyle(Tk.textMuted, fontSize = 11.sp),
            )
        }
    }
}
