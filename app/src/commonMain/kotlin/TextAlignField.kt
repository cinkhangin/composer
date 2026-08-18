package composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import composer.model.TextAlignment
import composer.ui.Tk

@Composable
internal fun TextAlignField(value: TextAlignment, onChange: (TextAlignment) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        BasicText("Align", style = TextStyle(color = Tk.textMuted, fontSize = 11.sp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AlignButton("format_align_left", value == TextAlignment.Start) { onChange(TextAlignment.Start) }
            AlignButton("format_align_center", value == TextAlignment.Center) { onChange(TextAlignment.Center) }
            AlignButton("format_align_right", value == TextAlignment.End) { onChange(TextAlignment.End) }
            AlignButton("format_align_justify", value == TextAlignment.Justify) { onChange(TextAlignment.Justify) }
        }
    }
}
