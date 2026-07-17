package composer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import composer.model.DesignJson
import composer.model.Node
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.ln

/** JSON envelope shared with the Android Studio plugin host. */
@Serializable
internal data class DesignerMessage(
    val type: String,
    val rev: Int = 0,
    val design: String? = null,
    val nodeId: String? = null,
    val dark: Boolean? = null,
    val appMode: Boolean? = null,
)

/** Cumulative native trackpad magnification from the JVM ComposePanel host. */
internal data class CanvasMagnification(val sequence: Long, val logScale: Double)

/**
 * One in-process bridge session per ComposePanel.
 *
 * Keeping all transport and echo-guard state on the panel instance allows the
 * tool windows from multiple open projects to coexist in one Android Studio
 * process without stealing each other's messages.
 */
internal class DesignerSession(private val hostSink: (String) -> Unit) {
    var appMode by mutableStateOf(false)
        private set

    var canvasMagnification by mutableStateOf<CanvasMagnification?>(null)
        private set

    var onLoadDesign: ((Node) -> Unit)? = null
    var onSelectNode: ((String) -> Unit)? = null

    private val json = Json { ignoreUnknownKeys = true }
    private var started = false
    private var outRev = 0
    private var lastInRev = 0
    private var lastSelection: String? = null
    private var lastLoaded: String? = null
    private var canvasMagnificationLog = 0.0

    fun noteLoaded(designJson: String) {
        lastLoaded = designJson
    }

    fun start() {
        if (started) return
        started = true
        post(DesignerMessage(type = "ready"))
    }

    fun postDesign(designJson: String) {
        if (designJson == lastLoaded) return
        lastLoaded = null
        post(DesignerMessage(type = "designChanged", rev = ++outRev, design = designJson))
    }

    fun postSelection(nodeId: String?) {
        if (nodeId == lastSelection) return
        lastSelection = nodeId
        post(DesignerMessage(type = "selectionChanged", rev = ++outRev, nodeId = nodeId))
    }

    fun requestNewComposable() {
        post(DesignerMessage(type = "newComposable", rev = ++outRev))
    }

    /** Called by the JVM host's native macOS magnification listener. */
    fun magnifyCanvas(delta: Float) {
        val next = (canvasMagnification?.sequence ?: 0L) + 1L
        canvasMagnificationLog += ln((1.0 + delta).coerceIn(0.1, 10.0))
        canvasMagnification = CanvasMagnification(next, canvasMagnificationLog)
    }

    fun deliver(raw: String) {
        val msg = runCatching { json.decodeFromString(DesignerMessage.serializer(), raw) }.getOrNull() ?: return
        if (msg.rev in 1..lastInRev) return
        if (msg.rev > 0) lastInRev = msg.rev
        when (msg.type) {
            "loadDesign" -> {
                msg.appMode?.let { appMode = it }
                val tree = msg.design?.let { runCatching { DesignJson.decode(it) }.getOrNull() } ?: return
                onLoadDesign?.invoke(tree)
            }
            "selectNode" -> msg.nodeId?.let { id ->
                lastSelection = id
                onSelectNode?.invoke(id)
            }
            "setTheme" -> msg.dark?.let { composer.ui.Theme.set(it) }
        }
    }

    fun dispose() {
        onLoadDesign = null
        onSelectNode = null
    }

    private fun post(msg: DesignerMessage) {
        hostSink(json.encodeToString(DesignerMessage.serializer(), msg))
    }
}
