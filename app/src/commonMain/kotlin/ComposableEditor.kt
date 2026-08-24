package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import composer.model.Node
import composer.ui.Field
import composer.ui.Tk
import composer.ui.ToolButton

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BasicText(
                    "Parameters",
                    style = TextStyle(color = Tk.textSecondary, fontSize = 12.sp),
                    modifier = Modifier.weight(1f),
                )
                ToolButton("Add parameter", primary = true) { state.addComposableParameter(screen.id) }
            }
            if (preview.parameters.isEmpty()) {
                BasicText(
                    "Add parameters to the real function, then reference their names from component expressions.",
                    style = TextStyle(color = Tk.textMuted, fontSize = 11.sp),
                )
            }
            preview.parameters.forEachIndexed { index, parameter ->
                val validName = parameter.name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) &&
                    preview.parameters.count { it.name == parameter.name } == 1
                val validType = parameter.type.isNotBlank()
                val validValue = parameter.expression.isNotBlank()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Tk.panelAlt, RoundedCornerShape(Tk.rSm))
                        .border(1.dp, Tk.border, RoundedCornerShape(Tk.rSm))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Field(
                            value = parameter.name,
                            onValueChange = { state.setComposableParameter(screen.id, index, parameter.copy(name = it)) },
                            label = "Name",
                            isError = !validName,
                            placeholder = "title",
                            modifier = Modifier.weight(1f),
                        )
                        Field(
                            value = parameter.type,
                            onValueChange = { state.setComposableParameter(screen.id, index, parameter.copy(type = it)) },
                            label = "Type",
                            isError = !validType,
                            placeholder = "String",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Field(
                        value = parameter.expression,
                        onValueChange = { state.setComposableParameter(screen.id, index, parameter.copy(expression = it)) },
                        label = "Default / preview value",
                        isError = !validValue,
                        placeholder = "\"Value\"",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Checkbox(
                            checked = parameter.hasDefault,
                            onCheckedChange = {
                                state.setComposableParameter(screen.id, index, parameter.copy(hasDefault = it))
                            },
                        )
                        BasicText(
                            "Has default",
                            style = TextStyle(color = Tk.textSecondary, fontSize = 12.sp),
                            modifier = Modifier.weight(1f),
                        )
                        ToolButton("Remove") { state.removeComposableParameter(screen.id, index) }
                    }
                }
            }
        }
    }
}
