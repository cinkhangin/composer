package composer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import composer.model.DesignJson
import composer.model.Node
import composer.ui.AppTheme
import kotlinx.browser.window
import org.w3c.dom.events.Event

enum class Route { Home, Edit }

/**
 * App-wide state above the editor: the current route (`/` home, `/{id}/edit`
 * editor) and the file currently open. The open file's id lives in the URL path,
 * so a reload restores it. Routing is synced with the browser URL via the History
 * API (see [Root]).
 */
enum class SaveStatus { Saved, Saving, Error }

class Workspace {
    var route by mutableStateOf(Route.Home)
        private set
    var currentId by mutableStateOf<String?>(null)
        private set
    var currentName by mutableStateOf("Untitled")
    var openToken by mutableStateOf(0)
        private set

    var initialDesign: Node = emptyDesign
        private set

    /** Non-null when the last save failed — shown in the toolbar so data loss isn't silent. */
    var saveError by mutableStateOf<String?>(null)
        private set

    /** Live persistence state, surfaced as a status chip in the toolbar (like Figma/Docs). */
    var saveStatus by mutableStateOf(SaveStatus.Saved)

    fun markDirty() { if (saveStatus != SaveStatus.Error) saveStatus = SaveStatus.Saving }

    init {
        // Restore the open file from the URL on first load / hard refresh.
        if (pathIsEdit()) {
            loadFromUrl()
            route = Route.Edit
        }
    }

    fun newDesign() {
        // Allocate the id up front so the URL carries it immediately (survives refresh,
        // even before the first auto-save). The file is created on the first edit.
        currentId = FileStore.newId()
        currentName = "Untitled"
        initialDesign = emptyDesign
        openToken++
        go(Route.Edit)
    }

    /** Start a NEW design seeded from a template — the template itself is never mutated. */
    fun newDesignFrom(name: String, design: Node) {
        currentId = FileStore.newId()
        currentName = name
        initialDesign = design
        openToken++
        go(Route.Edit)
    }

    fun open(meta: FileMeta) {
        currentId = meta.id
        currentName = meta.name
        initialDesign = loadDesignOrEmpty(meta.id)
        openToken++
        go(Route.Edit)
    }

    fun home() = go(Route.Home)

    fun dismissSaveError() { saveError = null }

    /** Persist the current editor tree to its file (creating one if needed). Records [saveError] on failure. */
    fun save(root: Node): FileMeta? {
        val id = currentId ?: FileStore.newId().also { currentId = it }
        return when (val r = FileStore.save(id, currentName.ifBlank { "Untitled" }, DesignJson.encode(root))) {
            is SaveResult.Ok -> { saveError = null; saveStatus = SaveStatus.Saved; r.meta }
            SaveResult.QuotaExceeded -> {
                saveError = "Storage full — latest changes not saved. Local storage is ~5 MB; embedded images are the usual cause. Export JSON (File ▸ Export JSON) to keep a copy."
                saveStatus = SaveStatus.Error
                null
            }
            is SaveResult.Error -> {
                saveError = "Couldn't save (${r.name}) — export your design to avoid losing work."
                saveStatus = SaveStatus.Error
                null
            }
        }
    }

    /** Re-sync route + open file from the URL (browser back/forward via popstate). */
    fun syncFromUrl() {
        if (pathIsEdit()) {
            if (pathId() != currentId) {
                loadFromUrl()
                openToken++ // navigated to a different design — rebuild the editor
            }
            route = Route.Edit
        } else {
            route = Route.Home
        }
    }

    /** Populate currentId/name/design from the id in the URL path. */
    private fun loadFromUrl() {
        val id = pathId()
        currentId = id
        if (id != null) {
            currentName = FileStore.list().firstOrNull { it.id == id }?.name ?: "Untitled"
            initialDesign = loadDesignOrEmpty(id)
        } else {
            currentName = "Untitled"
            initialDesign = emptyDesign
        }
    }

    private fun loadDesignOrEmpty(id: String): Node =
        FileStore.loadDesign(id)
            ?.let { runCatching { DesignJson.decode(it) }.getOrNull() }
            ?: emptyDesign

    private fun go(r: Route) {
        route = r
        val path = when {
            r == Route.Edit && currentId != null -> "/$currentId/edit"
            r == Route.Edit -> "/edit"
            else -> "/"
        }
        window.history.pushState(null, "", path)
    }
}

private fun pathIsEdit(): Boolean =
    window.location.pathname.removeSuffix("/").lowercase().endsWith("/edit")

/** The id segment from a `/{id}/edit` path, or null for a bare `/edit`. */
private fun pathId(): String? {
    val parts = window.location.pathname.trim('/').split('/').filter { it.isNotEmpty() }
    return if (parts.size >= 2 && parts.last().lowercase() == "edit") parts[parts.size - 2] else null
}

/** App entry: routes between the home page and the editor, under one theme. */
@Composable
fun Root() {
    val ws = remember { Workspace() }
    // First composition = the app is alive; fade out the index.html boot loader.
    LaunchedEffect(Unit) { dismissBootLoader() }
    DisposableEffect(Unit) {
        val listener: (Event) -> Unit = { ws.syncFromUrl() }
        window.addEventListener("popstate", listener)
        onDispose { window.removeEventListener("popstate", listener) }
    }
    AppTheme {
        when (ws.route) {
            Route.Home -> HomePage(ws)
            Route.Edit -> key(ws.openToken) { EditorScreen(ws) }
        }
    }
}
