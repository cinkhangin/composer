package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import composer.model.ModifierSpec
import composer.ui.AppIconKind
import composer.ui.SquareIconButton
import composer.ui.Tk

@Composable
internal fun ModifierCard(
    index: Int,
    spec: ModifierSpec,
    dnd: ModifierDndState,
    onRemove: () -> Unit,
    onChange: (ModifierSpec) -> Unit,
    onReorder: (Int, Int) -> Unit,
) {
    val isDragging = dnd.draggingIndex == index
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { c -> dnd.bounds[index] = RowSpan(c.positionInWindow().y, c.size.height.toFloat()) }
            .alpha(if (isDragging) 0.4f else 1f)
            .clip(RoundedCornerShape(Tk.rSm))
            .background(Tk.panelAlt)
            .border(1.dp, Tk.border, RoundedCornerShape(Tk.rSm))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            DragHandle(index, dnd, onReorder)
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(Tk.accent))
            BasicText(
                specName(spec),
                style = TextStyle(color = Tk.textPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            SquareIconButton(AppIconKind.Trash, danger = true, onClick = onRemove)
        }
        ModifierParams(spec, onChange)
    }
}
