package composer

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // ComposeViewport creates its own canvas inside the container element
    // (CanvasBasedWindow + <canvas id> was removed in CMP 1.11).
    ComposeViewport(document.getElementById("composer")!!) {
        Root()
    }
}
