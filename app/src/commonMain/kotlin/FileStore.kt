package composer

import kotlinx.serialization.Serializable

/** Metadata for one saved design ("file"). */
@Serializable
data class FileMeta(val id: String, val name: String, val updatedAt: Double)

/** Outcome of a save attempt. Failures are surfaced (not thrown) so the UI can warn instead of losing data silently. */
sealed interface SaveResult {
    data class Ok(val meta: FileMeta) : SaveResult
    /** localStorage is full (~5 MB) — usually inline base64 images bloating the design JSON. */
    data object QuotaExceeded : SaveResult
    data class Error(val name: String) : SaveResult
}

/**
 * Multi-file design storage. Web: localStorage (an index of [FileMeta] plus
 * one entry per design). JVM (IDE designer): in-memory only — the bridge owns
 * real persistence, this just keeps [Workspace] compiling and consistent.
 */
expect object FileStore {
    fun list(): List<FileMeta>

    fun loadDesign(id: String): String?

    fun save(id: String, name: String, designJson: String): SaveResult

    fun delete(id: String)

    fun newId(): String
}
