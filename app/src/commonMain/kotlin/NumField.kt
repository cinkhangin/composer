package composer

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import composer.ui.Field

/**
 * Integer field that accepts free typing. A valid number commits; an invalid one
 * (including empty) shows red and leaves the stored value untouched. While focused the
 * user owns the text — it is never overwritten, even if [onChange] clamps the value
 * (e.g. frame size). On blur (or an external change) it re-syncs to the stored value.
 *
 * [autoLabel] handles the model's "0 = inherit/auto" convention like Figma: a zero
 * value shows as an empty field with the label (e.g. "Auto") as placeholder, and
 * clearing the field commits 0 (back to auto) instead of erroring.
 */
@Composable
internal fun NumField(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    allowNegative: Boolean = false,
    autoLabel: String? = null,
    onChange: (Int) -> Unit,
) {
    fun show(v: Int) = if (autoLabel != null && v == 0) "" else v.toString()
    var text by remember { mutableStateOf(show(value)) }
    var focused by remember { mutableStateOf(false) }
    if (!focused && text != show(value)) text = show(value)
    fun parse(t: String): Int? = when {
        t.isEmpty() && autoLabel != null -> 0
        else -> t.toIntOrNull()?.let { if (allowNegative || it >= 0) it else null }
    }
    Field(
        value = text,
        isError = parse(text) == null,
        onValueChange = { v -> text = v; parse(v)?.let(onChange) },
        onFocusChange = { f -> focused = f; if (!f) text = show(value) },
        label = label,
        placeholder = autoLabel,
        modifier = modifier,
    )
}
