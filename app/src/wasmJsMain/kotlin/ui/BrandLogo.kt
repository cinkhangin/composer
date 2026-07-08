package composer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The Composer brand mark — the C-shaped ribbon from `resources/logo.svg`,
 * inlined as an [ImageVector] (flat fills, three paths) so it renders in the
 * Skia canvas. Multicolor, so it draws via [Image] (an Icon would tint it flat).
 * Keep in sync with logo.svg and favicon.svg.
 */
private val composerMark: ImageVector by lazy {
    ImageVector.Builder(
        name = "composer-logo",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 1024f, viewportHeight = 1024f,
    )
        // Uniform-thickness hexagonal ribbon (see logo.svg for the geometry notes).
        // Top ribbon (light indigo — hover tone)
        .addPath(
            pathData = addPathNodes("M797.79 347L512 182L226.21 347L418.21 457.85L512 403.7L605.79 457.85Z"),
            fill = SolidColor(Color(0xFF8A95F6)),
        )
        // Left spine (deep indigo)
        .addPath(
            pathData = addPathNodes("M226.21 347L226.21 677L418.21 566.15L418.21 457.85Z"),
            fill = SolidColor(Color(0xFF2B3280)),
        )
        // Bottom ribbon (vivid indigo — brand accent)
        .addPath(
            pathData = addPathNodes("M226.21 677L512 842L797.79 677L605.79 566.15L512 620.3L418.21 566.15Z"),
            fill = SolidColor(Color(0xFF5563E8)),
        )
        .build()
}

@Composable
fun BrandLogo(modifier: Modifier = Modifier.size(24.dp)) {
    Image(imageVector = composerMark, contentDescription = "Composer", modifier = modifier)
}
