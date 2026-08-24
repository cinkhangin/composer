package composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import composer.model.Node
import composer.ui.Tk
import composer.ui.Field

/** Artboard position editor for a screen (a [Node.Composable]) + its function-name note. */
@Composable
internal fun ComposableEditor(state: EditorState, screen: Node.Composable) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText(
            "Edits change the real @Composable function. The canvas renders its paired @Preview invocation.",
            style = TextStyle(color = Tk.textMuted, fontSize = 12.sp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            NumField("X", screen.x, Modifier.weight(1f), allowNegative = true) { state.setComposablePos(screen.id, it, screen.y) }
            NumField("Y", screen.y, Modifier.weight(1f), allowNegative = true) { state.setComposablePos(screen.id, screen.x, it) }
        }
        BasicText(
            "Screen size is shared by all composables and controlled from the designer toolbar.",
            style = TextStyle(color = Tk.textMuted, fontSize = 11.sp),
        )
        val preview = screen.preview
        if (preview != null) {
            BasicText(
                "Preview: ${preview.functionName.ifBlank { "generated preview" }}",
                style = TextStyle(color = Tk.textMuted, fontSize = 11.sp),
            )
            preview.parameters.forEach { parameter ->
                Field(
                    value = parameter.expression,
                    onValueChange = { state.setPreviewParameter(screen.id, parameter.name, it) },
                    label = if (parameter.type.isBlank()) parameter.name else "${parameter.name}: ${parameter.type}",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
