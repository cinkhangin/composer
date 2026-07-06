package composer.idea.bridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Envelope of the JS bridge protocol shared with the web app's EmbeddedBridge.
 * The design payload is an OPAQUE DesignJson string — envelope parsing never
 * touches the Node schema, so the two sides can't drift on it.
 *
 * web → IDE: `ready` (editor booted), `designChanged` (rev + design).
 * IDE → web: `loadDesign` (rev + design).
 */
@Serializable
data class BridgeMsg(
    val type: String,
    val rev: Int = 0,
    val design: String? = null,
    val nodeId: String? = null,
    val dark: Boolean? = null,
)

/** Forward-compatible envelope codec (unknown fields/types are ignored). */
val bridgeJson: Json = Json { ignoreUnknownKeys = true }
