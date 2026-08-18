package composer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
internal fun HexField(color: Long, modifier: Modifier, onChange: (Long) -> Unit) {
    var text by remember { mutableStateOf(hex8(color)) }
    var last by remember { mutableStateOf(color) }
    if (color != last) { text = hex8(color); last = color }
    Field(
        value = text,
        isError = parseHex(text) == null,
        onValueChange = { v ->
            val cleaned = v.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }.take(8)
            text = cleaned
            parseHex(cleaned)?.let { last = it; onChange(it) }
        },
        label = "Hex (RGB, RRGGBB or AARRGGBB)",
        modifier = modifier,
    )
}
