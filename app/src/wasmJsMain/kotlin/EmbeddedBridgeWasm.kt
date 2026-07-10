package composer

/**
 * Web transport for [EmbeddedBridge]: the JCEF host injects
 * `window.__composerHost = { postMessage: fn }`, and the editor registers
 * `window.__composerEmbed.receive`. Injection can lose the race against wasm
 * boot, which is why the bridge keeps an outbox until [hostReady].
 */

internal actual fun embeddedFlag(): Boolean =
    js("new URLSearchParams(window.location.search).has('embedded')")

internal actual fun hostReady(): Boolean =
    js("typeof window.__composerHost !== 'undefined' && window.__composerHost !== null")

internal actual fun hostPost(msg: String): Unit =
    js("window.__composerHost.postMessage(msg)")

internal actual fun registerEmbedReceiver(onMessage: (String) -> Unit): Unit =
    js("window.__composerEmbed = { receive: function (s) { onMessage(String(s)); } }")
