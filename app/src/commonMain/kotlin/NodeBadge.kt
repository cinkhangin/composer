package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import composer.model.Node
import composer.model.typeName
import composer.ui.ComponentGlyph
import composer.ui.Tk

@Composable
internal fun NodeBadge(state: EditorState, node: Node, isRoot: Boolean, lockComposableStructure: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(Tk.rSm)).background(Tk.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            ComponentGlyph(node.typeName(), Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            if (isRoot) {
                BasicText(
                    state.layerName(node.id) ?: "Artboard",
                    style = TextStyle(color = Tk.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                )
                BasicText(
                    if (state.layerName(node.id) != null) "Application" else "Design root",
                    style = TextStyle(color = Tk.textMuted, fontSize = 11.sp),
                )
            } else if (lockComposableStructure && node is Node.Composable) {
                BasicText(
                    state.layerName(node.id) ?: node.typeName(),
                    style = TextStyle(color = Tk.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                )
                BasicText("Source composable", style = TextStyle(color = Tk.textMuted, fontSize = 11.sp))
            } else {
                BasicTextField(
                    value = state.layerName(node.id) ?: node.typeName(),
                    onValueChange = { state.renameLayer(node.id, it) },
                    singleLine = true,
                    textStyle = TextStyle(color = Tk.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    cursorBrush = SolidColor(Tk.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
                BasicText("${node.typeName()} · rename", style = TextStyle(color = Tk.textMuted, fontSize = 11.sp))
            }
        }
    }
}
