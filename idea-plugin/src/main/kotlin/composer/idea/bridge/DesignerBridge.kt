package composer.idea.bridge

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter

/**
 * Two-way JS bridge to the embedded Composer designer.
 *
 * - **web → IDE**: a [JBCefJSQuery] (created BEFORE the page loads — a JCEF
 *   requirement) is injected on every main-frame load as
 *   `window.__composerHost.postMessage`, which the web app's EmbeddedBridge
 *   polls for. Messages are decoded and dispatched to [onMessage] on the EDT.
 * - **IDE → web**: [send] executes `window.__composerEmbed.receive(json)` —
 *   the global the web app registers on boot.
 */
class DesignerBridge(private val browser: JBCefBrowser) : Disposable {
    private val log = thisLogger()
    private val query: JBCefJSQuery = JBCefJSQuery.create(browser as JBCefBrowserBase)

    /** Bridge messages from the designer, delivered on the EDT. */
    var onMessage: ((BridgeMsg) -> Unit)? = null

    private val loadHandler = object : CefLoadHandlerAdapter() {
        override fun onLoadEnd(cefBrowser: CefBrowser, frame: CefFrame?, httpStatusCode: Int) {
            if (frame == null || !frame.isMain) return
            cefBrowser.executeJavaScript(
                "window.__composerHost = { postMessage: function (m) { ${query.inject("m")} } };",
                cefBrowser.url,
                0,
            )
        }
    }

    init {
        query.addHandler { raw ->
            val msg = runCatching { bridgeJson.decodeFromString(BridgeMsg.serializer(), raw) }.getOrNull()
            if (msg == null) {
                log.warn("Undecodable bridge message (${raw.length} chars)")
            } else {
                ApplicationManager.getApplication().invokeLater { onMessage?.invoke(msg) }
            }
            null
        }
        browser.jbCefClient.addLoadHandler(loadHandler, browser.cefBrowser)
    }

    fun send(msg: BridgeMsg) {
        val payload = jsStringLiteral(bridgeJson.encodeToString(BridgeMsg.serializer(), msg))
        browser.cefBrowser.executeJavaScript(
            "window.__composerEmbed && window.__composerEmbed.receive($payload);",
            browser.cefBrowser.url,
            0,
        )
    }

    override fun dispose() {
        browser.jbCefClient.removeLoadHandler(loadHandler, browser.cefBrowser)
        query.dispose()
    }
}

/** A double-quoted JS string literal of [s], safe to splice into executeJavaScript. */
private fun jsStringLiteral(s: String): String = buildString(s.length + 2) {
    append('"')
    for (c in s) {
        when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\u2028' -> append("\\u2028") // JS line separators — illegal raw in literals
            '\u2029' -> append("\\u2029")
            '<' -> append("\\u003C") // defuses </script>
            else -> append(c)
        }
    }
    append('"')
}
