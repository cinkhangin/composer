package composer.idea.bridge

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import composer.idea.web.ComposerWebServer
import composer.idea.web.ComposerWebServerStartException
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Where the designer actually runs. Two implementations of the SAME
 * [BridgeMsg] protocol:
 *
 * - [DirectDesignerHost] (default): the editor as Compose for Desktop inside a
 *   ComposePanel — no JCEF, no web server, no wasm boot. Messages cross an
 *   in-process function call.
 * - [JcefDesignerHost] (fallback, `-Dcomposer.designer.jcef=true`): the bundled
 *   wasm web app in a JCEF browser, exactly the pre-J3 path.
 *
 * Consumers drive both identically: set [onMessage], call [load], [send]
 * envelopes, dispose. `ready` still arrives first either way.
 */
interface DesignerHost : Disposable {
    val component: JComponent

    /** Designer→IDE envelopes, delivered on the EDT. */
    var onMessage: ((BridgeMsg) -> Unit)?

    /** A designer message that failed to decode (already logged), on the EDT. */
    var onUndecodable: (() -> Unit)?

    /** True when boot is slow enough to need a watchdog timer (JCEF wasm boot). */
    val needsBootTimeout: Boolean

    /** Start the designer (compose the panel / load the URL). Idempotent. */
    fun load()

    fun send(msg: BridgeMsg)
}

/** Selects and constructs the designer host implementation. */
object DesignerHosts {
    sealed interface Result {
        class Ok(val host: DesignerHost) : Result
        /** No host could start; [retryable] = a Retry button makes sense (vs. a hard runtime gap). */
        class Failed(val message: String, val retryable: Boolean) : Result
    }

    private val forceJcef: Boolean get() = java.lang.Boolean.getBoolean("composer.designer.jcef")

    fun create(parent: Disposable): Result {
        if (!forceJcef) return Result.Ok(DirectDesignerHost().also { Disposer.register(parent, it) })
        if (!JBCefApp.isSupported()) {
            return Result.Failed(
                "<html>JCEF designer mode is forced (composer.designer.jcef) but this IDE runtime has no JCEF.<br>" +
                    "Fix: Search Everywhere (Shift Shift) → \"Choose Boot Java Runtime for the IDE\" → " +
                    "pick a runtime with JCEF → restart. Or drop the flag to use the in-process designer.</html>",
                retryable = false,
            )
        }
        val url = try {
            service<ComposerWebServer>().baseUrl
        } catch (e: ComposerWebServerStartException) {
            return Result.Failed(e.message ?: "Composer's local web server failed to start.", retryable = true)
        }
        return Result.Ok(JcefDesignerHost(url).also { Disposer.register(parent, it) })
    }
}

/** The in-process Compose for Desktop designer ([composer.createDesignerPanel]). */
class DirectDesignerHost : DesignerHost {
    private val container = JPanel(BorderLayout())
    private var connection: composer.DesignerConnection? = null

    override val component: JComponent get() = container
    override var onMessage: ((BridgeMsg) -> Unit)? = null
    override var onUndecodable: (() -> Unit)? = null
    override val needsBootTimeout: Boolean get() = false

    override fun load() {
        if (connection != null) return
        val c = composer.createDesignerPanel { raw ->
            val msg = runCatching { bridgeJson.decodeFromString(BridgeMsg.serializer(), raw) }.getOrNull()
            ApplicationManager.getApplication().invokeLater {
                if (msg == null) onUndecodable?.invoke() else onMessage?.invoke(msg)
            }
        }
        connection = c
        container.add(c.component, BorderLayout.CENTER)
        container.revalidate()
    }

    override fun send(msg: BridgeMsg) {
        connection?.deliver(bridgeJson.encodeToString(BridgeMsg.serializer(), msg))
    }

    override fun dispose() {
        connection?.dispose()
        connection = null
    }
}

/** The pre-J3 path: the bundled wasm web app in a JCEF browser. */
class JcefDesignerHost(private val url: String) : DesignerHost {
    private val browser: JBCefBrowser = JBCefBrowser.createBuilder().setOffScreenRendering(false).build().apply {
        setErrorPage(JBCefBrowserBase.ErrorPage.DEFAULT)
    }
    private val bridge = DesignerBridge(browser)
    private var loaded = false

    override val component: JComponent get() = browser.component
    override var onMessage: ((BridgeMsg) -> Unit)?
        get() = bridge.onMessage
        set(value) {
            bridge.onMessage = value
        }
    override var onUndecodable: (() -> Unit)?
        get() = bridge.onUndecodable
        set(value) {
            bridge.onUndecodable = value
        }
    override val needsBootTimeout: Boolean get() = true

    override fun load() {
        if (loaded) return
        loaded = true
        browser.loadURL("$url?embedded=1")
    }

    override fun send(msg: BridgeMsg) = bridge.send(msg)

    override fun dispose() {
        Disposer.dispose(bridge)
        Disposer.dispose(browser)
    }
}
