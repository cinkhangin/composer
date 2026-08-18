package composer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun IntField(label: String, value: Int, modifier: Modifier = Modifier, allowNegative: Boolean = false, onChange: (Int) -> Unit) {
    NumberFieldImpl(
        label, value.toString(), modifier,
        parse = { it.toIntOrNull()?.takeIf { n -> allowNegative || n >= 0 } },
        onChange = onChange,
    )
}
