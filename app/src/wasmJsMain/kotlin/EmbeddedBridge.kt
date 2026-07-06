package composer

import composer.model.DesignJson
import composer.model.Node
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Bridge to an embedding host (the IntelliJ plugin's JCEF panel). Activated by the
 * `?embedded=1` query param: [Root] then skips the home page / FileStore / URL
 * routing entirely and runs a bare editor whose design is supplied and reported
 * over this bridge instead of persisted.
 *
 * Protocol (JSON envelope; the design payload is an OPAQUE [DesignJson] string,
 * so envelope parsing never touches the Node schema):
 *  - web → host: `{"type":"ready"}` once the editor is up;
 *    `{"type":"designChanged","rev":N,"design":"…"}` after each (debounced) edit.
 *  - host → web: `{"type":"loadDesign","rev":N,"design":"…"}` via the
 *    `window.__composerEmbed.receive(json)` global this object registers.
 *
 * The host injects `window.__composerHost = { postMessage: fn }`. Injection can
 * lose the race against wasm boot, so outgoing messages queue in [outbox] until
 * the host object exists (the editor pumps [flush] until delivery).
 */
@Serializable
data class BridgeMsg(
    val type: String,
    val rev: Int = 0,
    val design: String? = null,
    val nodeId: String? = null,
    val dark: Boolean? = null,
)

object EmbeddedBridge {
    /** True when running inside a host (the `?embedded=1` query param is present). */
    val active: Boolean by lazy { embeddedFlag() }

    /** Registered by the embedded editor screen; receives host-pushed designs. */
    var onLoadDesign: ((Node) -> Unit)? = null

    private val json = Json { ignoreUnknownKeys = true }
    private val outbox = ArrayDeque<String>()
    private var started = false
    private var outRev = 0

    /**
     * Canonical encoding of the last host-loaded design AS APPLIED (post-migration,
     * recorded via [noteLoaded] by the editor after [onLoadDesign] runs) — used to
     * swallow the one echo the load itself produces (the designChanged flow fires
     * on any root change, including external ones; the host already has that design).
     */
    private var lastLoaded: String? = null

    /** Record the editor's canonical encoding of the design it just loaded. */
    fun noteLoaded(designJson: String) {
        lastLoaded = designJson
    }

    /** Register the receiver global and queue the ready handshake. Idempotent. */
    fun start() {
        if (started) return
        started = true
        registerEmbedReceiver { raw -> handle(raw) }
        post(BridgeMsg(type = "ready"))
    }

    /** Report an edited design to the host (skipping the post-load echo). */
    fun postDesign(designJson: String) {
        if (designJson == lastLoaded) return
        lastLoaded = null // a real edit ends the echo window
        post(BridgeMsg(type = "designChanged", rev = ++outRev, design = designJson))
    }

    /** Try to deliver queued messages; true once the queue is empty. */
    fun flush(): Boolean {
        if (outbox.isEmpty()) return true
        if (!hostReady()) return false
        while (outbox.isNotEmpty()) {
            hostPost(outbox.first())
            outbox.removeFirst()
        }
        return true
    }

    private fun post(msg: BridgeMsg) {
        outbox.addLast(json.encodeToString(BridgeMsg.serializer(), msg))
        flush()
    }

    private fun handle(raw: String) {
        val msg = runCatching { json.decodeFromString(BridgeMsg.serializer(), raw) }.getOrNull() ?: return
        when (msg.type) {
            "loadDesign" -> {
                val tree = msg.design?.let { runCatching { DesignJson.decode(it) }.getOrNull() } ?: return
                onLoadDesign?.invoke(tree)
            }
            // The host IDE's look-and-feel drives the editor chrome theme.
            "setTheme" -> msg.dark?.let { composer.ui.Theme.set(it) }
            // Unknown types are ignored — the envelope is forward-compatible.
        }
    }
}

private fun embeddedFlag(): Boolean =
    js("new URLSearchParams(window.location.search).has('embedded')")

private fun hostReady(): Boolean =
    js("typeof window.__composerHost !== 'undefined' && window.__composerHost !== null")

private fun hostPost(msg: String): Unit =
    js("window.__composerHost.postMessage(msg)")

private fun registerEmbedReceiver(onMessage: (String) -> Unit): Unit =
    js("window.__composerEmbed = { receive: function (s) { onMessage(String(s)); } }")
