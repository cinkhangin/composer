package composer.idea.web

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * Loopback static server for the bundled Composer web app (JCEF pages need a real
 * HTTP origin: the app's `<base href="/">` + absolute `/composer.js` resolve
 * against the origin root, and `WebAssembly.instantiateStreaming` requires the
 * `application/wasm` MIME type). Serves ONLY static assets — design data rides
 * the JS bridge, never HTTP — so a random ephemeral port on 127.0.0.1 is the
 * whole exposure surface.
 *
 * Asset resolution order:
 *  1. the `composer.web.dist.dir` system property (dev loop: `runIde` points it
 *     at :app's dist dir so web rebuilds don't need a plugin rebuild), then
 *  2. classpath resources under `composer-web/` (the bundled production build).
 */
@Service(Service.Level.APP)
class ComposerWebServer : Disposable {
    private val log = thisLogger()

    @Volatile
    private var started: HttpServer? = null

    private val server: HttpServer
        @Synchronized get() = started ?: HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            createContext("/") { exchange -> exchange.use(::handle) }
            start()
            log.info("Composer web server on http://127.0.0.1:${address.port}/")
            started = this
        }

    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}/"

    private fun handle(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET" && exchange.requestMethod != "HEAD") {
            exchange.sendResponseHeaders(405, -1)
            return
        }
        val path = exchange.requestURI.path.orEmpty()
        if (path.contains("..")) {
            exchange.sendResponseHeaders(404, -1)
            return
        }
        // SPA-style fallback: the root (and any extensionless path) is the app page.
        // NB: substringAfterLast('/') must keep its default missing-delimiter value
        // (the whole string) — passing "" here made every ROOT-LEVEL asset
        // (composer.js, *.wasm) read as extensionless and serve index.html.
        val rel = path.trimStart('/').ifEmpty { "index.html" }.let {
            if ('.' in it.substringAfterLast('/')) it else "index.html"
        }
        val bytes = readAsset(rel)
        if (bytes == null) {
            exchange.sendResponseHeaders(404, -1)
            return
        }
        exchange.responseHeaders.add("Content-Type", mimeOf(rel))
        // Content-hashed .wasm bundles are immutable; entry assets must revalidate.
        exchange.responseHeaders.add(
            "Cache-Control",
            if (rel.endsWith(".wasm")) "max-age=31536000, immutable" else "no-cache",
        )
        if (exchange.requestMethod == "HEAD") {
            exchange.sendResponseHeaders(200, -1)
        } else {
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.write(bytes)
        }
    }

    private fun readAsset(rel: String): ByteArray? {
        System.getProperty(DEV_DIST_DIR_PROPERTY)?.let { dir ->
            val f = File(dir, rel)
            if (f.isFile) return f.readBytes()
        }
        return javaClass.classLoader.getResourceAsStream("composer-web/$rel")?.use { it.readBytes() }
    }

    private fun mimeOf(rel: String): String = when (rel.substringAfterLast('.', "")) {
        "html" -> "text/html; charset=utf-8"
        "js", "mjs" -> "text/javascript; charset=utf-8"
        "wasm" -> "application/wasm"
        "json" -> "application/json"
        "svg" -> "image/svg+xml"
        "ttf" -> "font/ttf"
        "txt" -> "text/plain; charset=utf-8"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "ico" -> "image/x-icon"
        else -> "application/octet-stream"
    }

    override fun dispose() {
        // Only stop what actually ran — touching `server` here would lazily
        // START it just to stop it.
        started?.stop(0)
    }

    companion object {
        const val DEV_DIST_DIR_PROPERTY = "composer.web.dist.dir"
    }
}

private inline fun HttpExchange.use(handler: (HttpExchange) -> Unit) {
    try {
        handler(this)
    } finally {
        close()
    }
}
