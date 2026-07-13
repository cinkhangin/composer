package composer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import composer.model.DesignJson
import composer.model.Node
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

/**
 * One in-process bridge session per ComposePanel.
 *
 * Keeping all transport and echo-guard state on the panel instance allows the
 * whole-app tool window and any number of split-editor previews to coexist in
 * the same Android Studio process without stealing each other's messages.
 */
internal class DesignerSession(private val hostSink: (String) -> Unit) {
    var appMode by mutableStateOf(false)
        private set

    var onLoadDesign: ((Node) -> Unit)? = null
    var onSelectNode: ((String) -> Unit)? = null

    private val json = Json { ignoreUnknownKeys = true }
    private var started = false
    private var outRev = 0
    private var lastInRev = 0
    private var lastSelection: String? = null
    private var lastLoaded: String? = null

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
