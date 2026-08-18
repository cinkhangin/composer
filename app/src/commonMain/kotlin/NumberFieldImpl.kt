package composer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import composer.ui.Field

/**
 * Numeric field that accepts any text. A valid parse commits; invalid input (including
 * empty) shows red and leaves the stored value untouched (so it reverts to the previous
 * value). While focused the user owns the text — it is never overwritten mid-edit; it
 * re-syncs to [valueText] on blur or an external change.
 */
@Composable
internal fun <T> NumberFieldImpl(
    label: String,
    valueText: String,
    modifier: Modifier,
    parse: (String) -> T?,
    onChange: (T) -> Unit,
) {
    var text by remember { mutableStateOf(valueText) }
    var focused by remember { mutableStateOf(false) }
    if (!focused && text != valueText) text = valueText
    Field(
        value = text,
        isError = parse(text) == null,
        onValueChange = { v -> text = v; parse(v)?.let(onChange) },
        onFocusChange = { f -> focused = f; if (!f) text = valueText },
        label = label,
        modifier = modifier,
    )
}
