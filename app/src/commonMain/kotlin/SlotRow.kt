package composer

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import composer.ui.Tk
import composer.ui.ToolButton

@Composable
internal fun SlotRow(label: String, present: Boolean, onSet: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, style = TextStyle(color = Tk.textSecondary, fontSize = 13.sp), modifier = Modifier.weight(1f))
        if (present) ToolButton("Remove") { onSet(false) } else ToolButton("Add", primary = true) { onSet(true) }
    }
}
