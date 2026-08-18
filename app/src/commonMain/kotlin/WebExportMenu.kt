package composer

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import composer.codegen.CodeGen
import composer.model.DesignJson
import composer.ui.AppIconKind
import composer.ui.TkMenu
import composer.ui.TkMenuItem
import composer.ui.ToolButton

@Composable
internal fun WebExportMenu(state: EditorState) {
    var open by remember { mutableStateOf(false) }
    Box {
        ToolButton("Export", primary = true, icon = AppIconKind.Share) { open = true }
        TkMenu(expanded = open, onDismissRequest = { open = false }) {
            TkMenuItem("Export Kotlin files") {
                open = false
                CodeGen.generateFiles(state.root).forEach { file ->
                    downloadText(file.path, file.text, "text/plain")
                }
            }
            TkMenuItem("Export JSON") {
                open = false
                downloadText("composer-design.json", DesignJson.encode(state.root), "application/json")
            }
        }
    }
}
