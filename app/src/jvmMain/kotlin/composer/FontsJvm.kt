package composer

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Typeface
import java.awt.GraphicsEnvironment
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle

/**
 * Device-installed fonts on the JVM: enumerate via AWT, resolve typefaces via
 * Skiko's [FontMgr] as a Compose [FontFamily] per family name.
 */
actual object LocalFonts {
    actual val available: SnapshotStateList<String> = mutableStateListOf()

    actual val loaded: SnapshotStateMap<String, FontFamily> = mutableStateMapOf()

    actual val supported: Boolean get() = true

    actual suspend fun query() {
        if (available.isNotEmpty()) return
        val names = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .availableFontFamilyNames.toSortedSet().toList()
        available.clear()
        available.addAll(names)
    }

    actual suspend fun load(family: String) {
        if (family.isEmpty() || loaded.containsKey(family)) return
        val skia = FontMgr.default.matchFamilyStyle(family, FontStyle.NORMAL) ?: return
        loaded[family] = FontFamily(Typeface(skia))
    }
}
