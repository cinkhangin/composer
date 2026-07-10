package composer

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.text.font.FontFamily

/**
 * Device-installed fonts for the canvas preview. Web: the Chromium Local Font
 * Access API (permission-gated; [supported] false elsewhere). JVM: the AWT
 * font list + skiko typeface lookup. Loaded fonts are registered as Compose
 * [FontFamily]s keyed by family name.
 */
expect object LocalFonts {
    /** Installed font family names, populated by [query]. Snapshot-backed for UI. */
    val available: SnapshotStateList<String>

    /** family name → loaded FontFamily, populated by [load]. */
    val loaded: SnapshotStateMap<String, FontFamily>

    val supported: Boolean

    /** Enumerate installed font families (may prompt for permission). */
    suspend fun query()

    /** Load [family] and register a FontFamily for the canvas preview. */
    suspend fun load(family: String)
}
