package composer

import kotlinx.browser.localStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
 * Browser-backed multi-file storage (localStorage). An index of [FileMeta] lives
 * under [INDEX_KEY]; each design's JSON lives under `composer.file.<id>`.
 */
object FileStore {
    private const val INDEX_KEY = "composer.files"
    private val json = Json { ignoreUnknownKeys = true }

    fun list(): List<FileMeta> {
        val raw = localStorage.getItem(INDEX_KEY) ?: return emptyList()
        return runCatching { json.decodeFromString<List<FileMeta>>(raw) }.getOrDefault(emptyList())
            .sortedByDescending { it.updatedAt }
    }

    fun loadDesign(id: String): String? = localStorage.getItem("composer.file.$id")

    fun save(id: String, name: String, designJson: String): SaveResult {
        // Write the (large) design blob first: if quota is hit it fails here, before the
        // index is touched, so the index stays consistent with the last good state.
        setItemSafe("composer.file.$id", designJson).let { if (it.isNotEmpty()) return toFailure(it) }
        val meta = FileMeta(id, name, nowMs())
        val updated = (list().filter { it.id != id } + meta)
        setItemSafe(INDEX_KEY, json.encodeToString(updated)).let { if (it.isNotEmpty()) return toFailure(it) }
        return SaveResult.Ok(meta)
    }

    fun delete(id: String) {
        localStorage.removeItem("composer.file.$id")
        // Removing frees space, so this write shouldn't hit quota; ignore any failure.
        setItemSafe(INDEX_KEY, json.encodeToString(list().filter { it.id != id }))
    }

    fun newId(): String = "f" + nowMs().toLong().toString()

    private fun toFailure(errorName: String): SaveResult =
        if (errorName.contains("quota", ignoreCase = true)) SaveResult.QuotaExceeded
        else SaveResult.Error(errorName)
}

private fun nowMs(): Double = js("Date.now()")

private fun localeDate(ms: Double): String = js("new Date(ms).toLocaleDateString()")

/** "Edited 5m ago"-style label for file cards, from a [FileMeta.updatedAt] epoch-ms stamp. */
fun editedLabel(updatedAt: Double): String {
    val mins = ((nowMs() - updatedAt) / 60_000).toInt()
    return when {
        mins < 1 -> "Edited just now"
        mins < 60 -> "Edited ${mins}m ago"
        mins < 24 * 60 -> "Edited ${mins / 60}h ago"
        mins < 7 * 24 * 60 -> "Edited ${mins / (24 * 60)}d ago"
        else -> "Edited " + localeDate(updatedAt)
    }
}

/**
 * `localStorage.setItem` guarded in JS — returns "" on success or the browser error
 * name (e.g. "QuotaExceededError", Firefox's "NS_ERROR_DOM_QUOTA_REACHED") on failure,
 * so a full store surfaces as a value instead of an uncatchable thrown DOMException.
 */
private fun setItemSafe(key: String, value: String): String =
    js("(function(){ try { localStorage.setItem(key, value); return ''; } catch (e) { return (e && e.name) ? e.name : 'Error'; } })()")
