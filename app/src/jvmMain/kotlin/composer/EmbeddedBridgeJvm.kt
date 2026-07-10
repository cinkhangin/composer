package composer

/**
 * In-process transport for [EmbeddedBridge] (the IDE designer): instead of
 * JCEF-injected window globals, the hosting panel wires both directions
 * directly. Same envelope, same guards — only the wire is different.
 *
 * The panel must call [DesignerHostTransport.activate] BEFORE the first read
 * of [EmbeddedBridge.active] (it's a lazy), then set [DesignerHostTransport.hostSink]
 * to receive web→host messages and use [DesignerHostTransport.deliver] for
 * host→web ones.
 */
object DesignerHostTransport {
    @Volatile
    private var active = false

    /** Receives editor→host envelopes. Settable any time; the bridge outbox holds until then. */
    @Volatile
    var hostSink: ((String) -> Unit)? = null

    @Volatile
    private var receiver: ((String) -> Unit)? = null

    /** Mark this process as an embedded designer host. Call before composing the editor. */
    fun activate() {
        active = true
    }

    /** Deliver one host→editor envelope (loadDesign / selectNode / setTheme). */
    fun deliver(msg: String) {
        receiver?.invoke(msg)
    }

    internal fun isActive() = active

    internal fun register(onMessage: (String) -> Unit) {
        receiver = onMessage
    }
}

internal actual fun embeddedFlag(): Boolean = DesignerHostTransport.isActive()

internal actual fun hostReady(): Boolean = DesignerHostTransport.hostSink != null

internal actual fun hostPost(msg: String) {
    DesignerHostTransport.hostSink?.invoke(msg)
}

internal actual fun registerEmbedReceiver(onMessage: (String) -> Unit) {
    DesignerHostTransport.register(onMessage)
}
