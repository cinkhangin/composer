package composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import kotlin.math.roundToInt
import composer.ui.Tk

@Composable
internal fun SliderField(value: Float, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BasicText("value: ${(value * 100).roundToInt()}%", style = TextStyle(color = Tk.textSecondary, fontSize = 13.sp))
        Slider(value = value, onValueChange = onChange)
    }
}
