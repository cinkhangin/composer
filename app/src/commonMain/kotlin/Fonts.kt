package composer

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.text.font.FontFamily

/**
 * Device-installed fonts for the canvas preview. Browser and JVM hosts provide
 * their own discovery/loading mechanisms. Loaded fonts are registered as Compose
 * [FontFamily]s keyed by family name.
 */
expect object LocalFonts {
    /** Installed font family names, populated by [query]. Snapshot-backed for UI. */
    val available: SnapshotStateList<String>

    /** family name → loaded FontFamily, populated by [load]. */
    val loaded: SnapshotStateMap<String, FontFamily>

    val supported: Boolean

    /** Enumerate installed font families (permission-gated where required). */
    suspend fun query()

    /** Load [family] and register a FontFamily for the canvas preview. */
    suspend fun load(family: String)
}
