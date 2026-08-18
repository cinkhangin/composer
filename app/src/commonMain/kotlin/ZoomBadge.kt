package composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import composer.ui.AppIconKind
import composer.ui.Island
import composer.ui.Tip
import composer.ui.ToolButton

@Composable
internal fun ZoomBadge(zoom: Float, onZoom: (Float) -> Unit, onReset: () -> Unit, modifier: Modifier = Modifier) {
    Island(modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Tip("Zoom out") { ToolButton("−") { onZoom(zoom / 1.2f) } }
            // The readout zooms to TRUE size (1 design dp = 1 screen dp); Fit re-fits the view.
            Tip("Actual size (1x)") { ToolButton(zoomLabel(zoom), onClick = { onZoom(1f) }) }
            Tip("Zoom in") { ToolButton("+") { onZoom(zoom * 1.2f) } }
            Tip("Fit to screen") { ToolButton("", icon = AppIconKind.Fit, onClick = onReset) }
        }
    }
}
