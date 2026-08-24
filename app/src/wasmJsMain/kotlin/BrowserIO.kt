package composer

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.files.FileReader

/**
 * Browser actuals for the [Platform.kt] seams: localStorage preferences, file
 * download/import, clipboard, URL routing, and boot-loader chrome.
 */

actual fun prefGet(key: String): String? = prefGetSafe(key)

actual fun prefSet(key: String, value: String) = prefSetSafe(key, value)

/**
 * Preferences share localStorage with design files. A large inline-image
 * design can fill that store, and some privacy modes deny storage entirely.
 * Preference failures must never abort a toolbar click or app composition.
 */
private fun prefGetSafe(key: String): String? =
    js("(function(k){ try { return localStorage.getItem(k); } catch (e) { return null; } })(key)")

private fun prefSetSafe(key: String, value: String): Unit =
    js("(function(k,v){ try { localStorage.setItem(k,v); } catch (e) {} })(key,value)")

/** Trigger a browser download of [content] as [filename] via a data URL. */
actual fun downloadText(filename: String, content: String, mime: String) {
    val anchor = document.createElement("a") as HTMLAnchorElement
    anchor.href = "data:$mime;charset=utf-8," + encodeURIComponent(content)
    anchor.download = filename
    anchor.click()
}

/** Open a file picker and deliver the chosen file's text to [onText]. */
actual fun importTextFile(accept: String, onText: (String) -> Unit) {
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
actual fun copyToClipboard(text: String): Unit = js("{ navigator.clipboard.writeText(text); }")

private fun encodeURIComponent(value: String): String = js("encodeURIComponent(value)")

/**
 * Fade out the index.html boot loader (shown while the wasm bundle loads).
 * Called on [Root]'s first composition — the CSS transition handles the fade,
 * and pointer-events: none keeps the invisible overlay from eating clicks.
 */
actual fun dismissBootLoader() {
    document.getElementById("loader")?.classList?.add("done")
}

/** Open [url] in a new browser tab. */
actual fun openUrl(url: String) {
    window.open(url, "_blank")
}

/** Flush on tab close — the auto-save debounce would otherwise drop the last edit. */
actual fun registerUnloadFlush(flush: () -> Unit): () -> Unit {
    val listener: (Event) -> Unit = { flush() }
    window.addEventListener("beforeunload", listener)
    return { window.removeEventListener("beforeunload", listener) }
}

// ---- URL routing (History API; see Root's popstate listener) ----

actual fun pathIsEdit(): Boolean =
    window.location.pathname.removeSuffix("/").lowercase().endsWith("/edit")

/** The id segment from a `/{id}/edit` path, or null for a bare `/edit`. */
actual fun pathId(): String? {
    val parts = window.location.pathname.trim('/').split('/').filter { it.isNotEmpty() }
    return if (parts.size >= 2 && parts.last().lowercase() == "edit") parts[parts.size - 2] else null
}

actual fun pushPath(path: String) {
    window.history.pushState(null, "", path)
}
