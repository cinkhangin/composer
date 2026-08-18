package composer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * Editor-themed dropdown menu: [Tk]-colored container (panel background, subtle
 * border, rounded corners) instead of the stock Material3 purple-tinted surface,
 * with compact hover-highlighted rows ([TkMenuItem]). Used by every menu in the
 * editor — the inspector dropdowns and the toolbar Logo/Export menus — so they
 * all read as one design system.
 */
@Composable
fun TkMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.padding(horizontal = 4.dp),
        offset = DpOffset(0.dp, 4.dp),
        shape = RoundedCornerShape(10.dp),
        containerColor = Tk.panelAlt,
        border = BorderStroke(1.dp, Tk.border),
        shadowElevation = 12.dp,
        content = content,
    )
}
