package composer

import kotlinx.browser.document
import org.w3c.dom.HTMLElement

/**
 * Set the mouse cursor on the app's canvas via CSS. Compose-web's
 * [androidx.compose.ui.input.pointer.PointerIcon] set has no resize cursors,
 * so the resize edge/corner zones drive the browser cursor directly
 * ("ew-resize", "ns-resize", "nwse-resize", "nesw-resize", "default").
 *
 * Since the ComposeViewport migration, `#composer` is a container DIV and the
 * canvas is created inside it (and Compose writes the PointerIcon cursor onto
 * that canvas, which would override any style on the container) — so target
 * the inner canvas when present.
 */
fun setCanvasCursor(cursor: String) {
    val host = document.getElementById("composer") as? HTMLElement ?: return
    val canvas = host.querySelector("canvas") as? HTMLElement
    (canvas ?: host).style.cursor = cursor
}


/**
 * Give the app's canvas element BROWSER focus. On a fresh page load nothing has
 * DOM focus, so Compose-side FocusRequester calls can't take effect and the
 * first click only grabs focus (caret lands at the stale selection instead of
 * the click point). Called before requesting Compose focus programmatically.
 */
fun focusComposeCanvas() {
    val host = document.getElementById("composer") as? HTMLElement ?: return
    (host.querySelector("canvas") as? HTMLElement)?.focus()
}
