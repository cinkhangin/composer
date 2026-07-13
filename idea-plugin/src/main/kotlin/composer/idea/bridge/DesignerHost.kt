package composer.idea.bridge

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.Magnificator
import com.intellij.ui.components.ZoomableViewport
import java.awt.BorderLayout
import java.awt.Point
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * In-process Compose designer host. Consumers set [onMessage], call [load] and
 * exchange [BridgeMsg] envelopes through direct in-process calls.
 */
interface DesignerHost : Disposable {
    val component: JComponent

    /** Designer→IDE envelopes, delivered on the EDT. */
    var onMessage: ((BridgeMsg) -> Unit)?

    /** A designer message that failed to decode (already logged), on the EDT. */
    var onUndecodable: (() -> Unit)?

    /** Start composing the panel. Idempotent. */
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

    /**
     * True when the IDE ships Compose for Desktop and exposes it to this plugin
     * (the plugin.xml `intellij.platform.compose` module dependency, present in
     * platform-compose builds). The plugin never bundles compose itself: on old
     * IDEs a bundled runtime dies on the platform-forced kotlin-stdlib split
     * (kotlin.time.Duration is platform-loaded under our newer skiko), and on
     * new IDEs the second skiko native can't coexist with the platform's.
     */
    private val platformComposeAvailable: Boolean by lazy {
        runCatching {
            Class.forName("androidx.compose.ui.awt.ComposePanel", false, DesignerHosts::class.java.classLoader)
        }.isSuccess
    }

    fun create(parent: Disposable): Result {
        if (platformComposeAvailable) {
            return Result.Ok(DirectDesignerHost().also { Disposer.register(parent, it) })
        }
        return Result.Failed(
            "<html>Composer requires an Android Studio runtime that exposes " +
                "<code>intellij.platform.compose</code>.</html>",
            retryable = false,
        )
    }
}

/** The in-process Compose for Desktop designer ([composer.createDesignerPanel]). */
class DirectDesignerHost : DesignerHost {
    private var connection: composer.DesignerConnection? = null
    private var lastMagnification = 0.0
    private val identityMagnificator = Magnificator { _, at -> at }
    private val container = object : JPanel(BorderLayout()), ZoomableViewport {
        override fun getMagnificator(): Magnificator = identityMagnificator

        override fun magnificationStarted(at: Point) {
            lastMagnification = 0.0
        }

        override fun magnify(magnification: Double) {
            val delta = magnification - lastMagnification
            lastMagnification = magnification
            connection?.magnifyCanvas(delta.toFloat())
        }

        override fun magnificationFinished(magnification: Double) {
            lastMagnification = 0.0
        }
    }

    override val component: JComponent get() = container
    override var onMessage: ((BridgeMsg) -> Unit)? = null
    override var onUndecodable: (() -> Unit)? = null
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
