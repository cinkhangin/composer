package composer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun FloatField(label: String, value: Float, modifier: Modifier = Modifier, onChange: (Float) -> Unit) {
    NumberFieldImpl(label, value.toString(), modifier, parse = { it.toFloatOrNull() }, onChange = onChange)
}
