package composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import composer.ui.AppIconKind
import composer.ui.SquareIconButton

/**
 * A collapsed side panel: a slim rail with just the expand affordance, aligned
 * with the neighbors' headers (IntelliJ tool-window style).
 */
@Composable
internal fun CollapsedPanelStrip(
    icon: AppIconKind,
    tip: String,
    divider: WorkspaceDivider,
    onExpand: () -> Unit,
) {
    Box(Modifier.width(42.dp).fillMaxHeight().workspaceSurface(divider = divider)) {
        Box(Modifier.fillMaxWidth().height(42.dp), contentAlignment = Alignment.Center) {
            SquareIconButton(icon, tip = tip, onClick = onExpand)
        }
    }
}
