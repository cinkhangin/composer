package composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import composer.model.CornerUnit

/**
 * Corner radius field with a dp / % unit toggle. Percent is Compose's
 * `RoundedCornerShape(percent)` — relative to the smaller side, clamped to 50
 * (= pill/circle), so it survives any resize.
 */
@Composable
internal fun CornerField(corner: Int, unit: CornerUnit, onChange: (Int, CornerUnit) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        val label = if (unit == CornerUnit.Percent) "corner radius (%)" else "corner radius (dp)"
        IntField(label, corner, Modifier.weight(1f)) { c ->
            onChange(if (unit == CornerUnit.Percent) c.coerceAtMost(50) else c, unit)
        }
        SegRow(listOf(CornerUnit.Dp to "dp", CornerUnit.Percent to "%"), unit) { u ->
            onChange(if (u == CornerUnit.Percent) corner.coerceAtMost(50) else corner, u)
        }
    }
}
