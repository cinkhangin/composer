package composer

import kotlinx.browser.document
import kotlinx.browser.localStorage
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader

/**
 * Thin browser-platform IO for persistence (M6): localStorage, file download,
 * and file import. Kept isolated so the rest of the app stays platform-neutral.
 */

private const val STORAGE_KEY = "composer.design"

fun saveToLocalStorage(json: String) = localStorage.setItem(STORAGE_KEY, json)

fun loadFromLocalStorage(): String? = localStorage.getItem(STORAGE_KEY)

private const val FRAME_KEY = "composer.frame"

/** Persist the editor's frame/preview size globally (it's a preference, not per-design). */
fun saveFrameSize(width: Int, height: Int) = localStorage.setItem(FRAME_KEY, "$width,$height")

fun loadFrameSize(): Pair<Int, Int>? {
    val parts = localStorage.getItem(FRAME_KEY)?.split(",") ?: return null
    if (parts.size != 2) return null
    val w = parts[0].toIntOrNull() ?: return null
    val h = parts[1].toIntOrNull() ?: return null
    return w to h
}

/** Trigger a browser download of [content] as [filename] via a data URL. */
fun downloadText(filename: String, content: String, mime: String) {
    val anchor = document.createElement("a") as HTMLAnchorElement
    anchor.href = "data:$mime;charset=utf-8," + encodeURIComponent(content)
    anchor.download = filename
    anchor.click()
}

/** Open a file picker and deliver the chosen file's text to [onText]. */
fun importTextFile(accept: String, onText: (String) -> Unit) {
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    input.accept = accept
    input.onchange = {
        val file = input.files?.item(0)
        if (file != null) {
            val reader = FileReader()
            reader.onload = {
                (reader.result as? JsString)?.let { onText(it.toString()) }
            }
            reader.readAsText(file)
        }
    }
    input.click()
}

/** Copy [text] to the system clipboard. */
fun copyToClipboard(text: String): Unit = js("{ navigator.clipboard.writeText(text); }")

private fun encodeURIComponent(value: String): String = js("encodeURIComponent(value)")
