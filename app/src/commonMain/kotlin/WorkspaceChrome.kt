package composer

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import composer.ui.Tk

internal enum class WorkspaceDivider {
    Left,
    Right,
    Bottom,
}

/** Flat editor chrome: continuous surfaces separated by a single crisp rule. */
internal fun Modifier.workspaceSurface(
    color: Color = Tk.panel,
    divider: WorkspaceDivider? = null,
): Modifier = background(color).drawBehind {
    val stroke = 1.dp.toPx()
    val halfStroke = stroke / 2f
    when (divider) {
        WorkspaceDivider.Left -> drawLine(
            color = Tk.border,
            start = Offset(halfStroke, 0f),
            end = Offset(halfStroke, size.height),
            strokeWidth = stroke,
        )
        WorkspaceDivider.Right -> drawLine(
            color = Tk.border,
            start = Offset(size.width - halfStroke, 0f),
            end = Offset(size.width - halfStroke, size.height),
            strokeWidth = stroke,
        )
        WorkspaceDivider.Bottom -> drawLine(
            color = Tk.border,
            start = Offset(0f, size.height - halfStroke),
            end = Offset(size.width, size.height - halfStroke),
            strokeWidth = stroke,
        )
        null -> Unit
    }
}
