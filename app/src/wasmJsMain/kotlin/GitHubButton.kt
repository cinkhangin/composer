package composer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import composer.ui.Tk

@Composable
internal fun GitHubButton() {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(Tk.rSm))
            .background(if (hovered) Tk.elevated else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { openUrl("https://github.com/cinkhangin/composer") },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = githubMark,
            contentDescription = "GitHub repository",
            modifier = Modifier.size(18.dp),
            tint = if (hovered) Tk.textPrimary else Tk.textSecondary,
        )
    }
}
