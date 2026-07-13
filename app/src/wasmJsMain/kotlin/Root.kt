package composer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import composer.ui.AppTheme
import kotlinx.browser.window
import org.w3c.dom.events.Event

/** App entry: routes between the home page and the editor, under one theme. */
@Composable
fun Root() {
    val ws = remember { Workspace() }
    // First composition = the app is alive; fade out the index.html boot loader.
    LaunchedEffect(Unit) { dismissBootLoader() }
    DisposableEffect(Unit) {
        val listener: (Event) -> Unit = { ws.syncFromUrl() }
        window.addEventListener("popstate", listener)
        onDispose { window.removeEventListener("popstate", listener) }
    }
    AppTheme {
        when (ws.route) {
            Route.Home -> HomePage(ws)
            Route.Edit -> key(ws.openToken) { WebEditorScreen(ws) }
        }
    }
}
