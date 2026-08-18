package composer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A floating rounded surface for canvas overlays such as palettes and badges.
 * The main workspace uses flat, edge-to-edge panes instead.
 */
@Composable
fun Island(
    modifier: Modifier = Modifier,
    color: Color = Tk.panel,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Tk.rLg)
    Column(
        modifier = modifier
            .clip(shape)
            .background(color)
            .border(1.dp, Tk.border, shape),
        content = content,
    )
}
