package composer

import kotlinx.browser.document
import org.w3c.dom.HTMLElement

/**
 * Set the mouse cursor on the app's canvas element via CSS. Compose-web's
 * [androidx.compose.ui.input.pointer.PointerIcon] set has no resize cursors,
 * so the resize edge/corner zones drive the browser cursor directly
 * ("ew-resize", "ns-resize", "nwse-resize", "nesw-resize", "default").
 */
fun setCanvasCursor(cursor: String) {
    (document.getElementById("composer") as? HTMLElement)?.style?.cursor = cursor
}
