package composer.idea.bridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Envelope shared by the plugin host and an in-process designer session.
 * The design payload is an OPAQUE DesignJson string — envelope parsing never
 * touches the Node schema, so the two sides can't drift on it.
 *
 * designer → IDE: `ready` (editor booted), `designChanged` (rev + design),
 *            `selectionChanged` (rev + nodeId, null = deselected),
 *            `newComposable` (show the IDE-owned creation dialog).
 * IDE → designer: `loadDesign` (rev + design), `setTheme` (dark),
 *            `selectNode` (rev + nodeId).
 *
 * Each side stamps a monotonically increasing `rev` on its own outgoing
 * messages; the receiver drops anything at or below the highest rev it has
 * seen, and resets that tracker on `ready` (a new panel starts a new session).
 */
@Serializable
data class BridgeMsg(
    val type: String,
    val rev: Int = 0,
    val design: String? = null,
    val nodeId: String? = null,
    val dark: Boolean? = null,
    /** Sent by the whole-app tool window to select screen-oriented editor copy. */
    val appMode: Boolean? = null,
)

/** Forward-compatible envelope codec (unknown fields/types are ignored). */
val bridgeJson: Json = Json { ignoreUnknownKeys = true }
