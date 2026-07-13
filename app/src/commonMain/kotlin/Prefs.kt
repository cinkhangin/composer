package composer

/**
 * Editor preferences (NOT design data) over the [prefGet]/[prefSet] seam:
 * legacy frame size and side-panel collapse.
 */

private const val FRAME_KEY = "composer.frame"

/** Persist the editor's frame/preview size globally (it's a preference, not per-design). */
fun saveFrameSize(width: Int, height: Int) = prefSet(FRAME_KEY, "$width,$height")

fun loadFrameSize(): Pair<Int, Int>? {
    val parts = prefGet(FRAME_KEY)?.split(",") ?: return null
    if (parts.size != 2) return null
    val w = parts[0].toIntOrNull() ?: return null
    val h = parts[1].toIntOrNull() ?: return null
    return w to h
}

// Side-panel visibility (IntelliJ-style collapse) — persisted per panel.
private const val PANEL_KEY_PREFIX = "composer.panel."
const val PANEL_LEFT = "layers"
const val PANEL_RIGHT = "inspector"

fun loadPanelOpen(panel: String): Boolean = prefGet(PANEL_KEY_PREFIX + panel) != "closed"

fun savePanelOpen(panel: String, open: Boolean) {
    prefSet(PANEL_KEY_PREFIX + panel, if (open) "open" else "closed")
}
