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
 * The Composer concentric-hexagon mark from `resources/logo.svg`, inlined as
 * an [ImageVector] so it renders in the Skia canvas. Multicolor, so it draws
 * via [Image] (an Icon would tint it flat).
 * Keep in sync with logo.svg and favicon.svg.
 */
private val composerMark: ImageVector by lazy {
    ImageVector.Builder(
        name = "composer-logo",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 40f, viewportHeight = 40f,
    )
        .addPath(
            pathData = addPathNodes("M20 0L37.3205 10V30L20 40L2.67949 30V10L20 0Z"),
            fill = SolidColor(Color(0xFF474747)),
        )
        .addPath(
            pathData = addPathNodes("M18.5 5.86603C19.4282 5.33013 20.5718 5.33013 21.5 5.86603L31.4904 11.634C32.4186 12.1699 32.9904 13.1603 32.9904 14.2321V25.7679C32.9904 26.8397 32.4186 27.8301 31.4904 28.366L21.5 34.134C20.5718 34.6699 19.4282 34.6699 18.5 34.134L8.50962 28.366C7.58142 27.8301 7.00962 26.8397 7.00962 25.7679V14.2321C7.00962 13.1603 7.58142 12.1699 8.50962 11.634L18.5 5.86603Z"),
            fill = SolidColor(Color(0xFF898989)),
        )
        .addPath(
            pathData = addPathNodes("M19 10.5774C19.6188 10.2201 20.3812 10.2201 21 10.5774L27.6603 14.4226C28.2791 14.7799 28.6603 15.4402 28.6603 16.1547V23.8453C28.6603 24.5598 28.2791 25.2201 27.6603 25.5774L21 29.4226C20.3812 29.7799 19.6188 29.7799 19 29.4226L12.3397 25.5774C11.7209 25.2201 11.3397 24.5598 11.3397 23.8453V16.1547C11.3397 15.4402 11.7209 14.7799 12.3397 14.4226L19 10.5774Z"),
            fill = SolidColor(Color(0xFFBEBEBE)),
        )
        .addPath(
            pathData = addPathNodes("M19 15.5774C19.6188 15.2201 20.3812 15.2201 21 15.5774L23.3301 16.9226C23.9489 17.2799 24.3301 17.9402 24.3301 18.6547V21.3453C24.3301 22.0598 23.9489 22.7201 23.3301 23.0774L21 24.4227C20.3812 24.7799 19.6188 24.7799 19 24.4226L16.6699 23.0774C16.0511 22.7201 15.6699 22.0598 15.6699 21.3453V18.6547C15.6699 17.9402 16.0511 17.2799 16.6699 16.9226L19 15.5774Z"),
            fill = SolidColor(Color.White),
        )
        .build()
}

@Composable
fun BrandLogo(modifier: Modifier = Modifier.size(24.dp)) {
    Image(imageVector = composerMark, contentDescription = "Composer", modifier = modifier)
}
