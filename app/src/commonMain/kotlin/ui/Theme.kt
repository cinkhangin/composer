package composer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import composer.prefGet
import composer.prefSet

/**
 * A full set of color tokens. Two instances exist — [DarkPalette] and
 * [LightPalette] — and [Theme] picks the active one reactively.
 */
data class Palette(
    val appBg: Color,
    val panel: Color,
    val panelAlt: Color,
    val elevated: Color,
    val border: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentHover: Color,
    val accentSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val snapGuide: Color,
    val canvasBg: Color,
    val canvasDot: Color,
    val canvasFrame: Color,
    val frameBg: Color,
    val codeBg: Color,
    val codeText: Color,
    val isDark: Boolean,
)

private val DarkPalette = Palette(
    appBg = Color(0xFF0E1014),
    panel = Color(0xFF161A20),
    panelAlt = Color(0xFF1C212A),
    elevated = Color(0xFF232A35),
    border = Color(0xFF272D38),
    borderStrong = Color(0xFF36404E),
    textPrimary = Color(0xFFE8EBF1),
    textSecondary = Color(0xFF98A2B3),
    textMuted = Color(0xFF626C7C),
    accent = Color(0xFF6E7BF2),
    accentHover = Color(0xFF8A95F6),
    accentSoft = Color(0x266E7BF2),
    danger = Color(0xFFE5605A),
    dangerSoft = Color(0x22E5605A),
    snapGuide = Color(0xFFFF4D8D),
    canvasBg = Color(0xFF0E1116),
    canvasDot = Color(0xFF1F2631),
    canvasFrame = Color(0xFF2A313C),
    frameBg = Color(0xFF191E27),
    codeBg = Color(0xFF0F1217),
    codeText = Color(0xFFC6CEDA),
    isDark = true,
)

private val LightPalette = Palette(
    appBg = Color(0xFFECEEF2),
    panel = Color(0xFFFFFFFF),
    panelAlt = Color(0xFFF4F6F8),
    elevated = Color(0xFFE9ECF1),
    border = Color(0xFFE0E4EA),
    borderStrong = Color(0xFFC7CDD7),
    textPrimary = Color(0xFF1B202A),
    textSecondary = Color(0xFF59626F),
    textMuted = Color(0xFF98A1AE),
    accent = Color(0xFF5563E8),
    accentHover = Color(0xFF4351DF),
    accentSoft = Color(0x1A5563E8),
    danger = Color(0xFFD9433D),
    snapGuide = Color(0xFFE0316E),
    dangerSoft = Color(0x16D9433D),
    canvasBg = Color(0xFFE7EAEF),
    canvasDot = Color(0xFFCFD5DE),
    canvasFrame = Color(0xFFE3E6EB),
    frameBg = Color(0xFFFFFFFF),
    codeBg = Color(0xFFF3F5F8),
    codeText = Color(0xFF2B313B),
    isDark = false,
)

private const val THEME_KEY = "composer.theme"

/** Active theme. Persisted as a preference; flipping [isDark] re-renders the whole UI. */
object Theme {
    // Default to dark unless the user explicitly chose light last time.
    var isDark by mutableStateOf(prefGet(THEME_KEY) != "light")
        private set

    val palette: Palette get() = if (isDark) DarkPalette else LightPalette

    fun toggle() {
        isDark = !isDark
        prefSet(THEME_KEY, if (isDark) "dark" else "light")
    }

    /** Host-driven theme (IDE plugin follows the IDE's LaF) — not persisted. */
    fun set(dark: Boolean) {
        isDark = dark
    }
}

/**
 * Design tokens. Colors delegate to the active [Theme.palette] (so reading any
 * `Tk.*` color in a composable or draw scope tracks the theme and updates on
 * toggle). Radii are constants.
 */
object Tk {
    val appBg get() = Theme.palette.appBg
    val panel get() = Theme.palette.panel
    val panelAlt get() = Theme.palette.panelAlt
    val elevated get() = Theme.palette.elevated
    val border get() = Theme.palette.border
    val borderStrong get() = Theme.palette.borderStrong
    val textPrimary get() = Theme.palette.textPrimary
    val textSecondary get() = Theme.palette.textSecondary
    val textMuted get() = Theme.palette.textMuted
    val accent get() = Theme.palette.accent
    val accentHover get() = Theme.palette.accentHover
    val accentSoft get() = Theme.palette.accentSoft
    val danger get() = Theme.palette.danger
    val snapGuide get() = Theme.palette.snapGuide
    val dangerSoft get() = Theme.palette.dangerSoft
    val canvasBg get() = Theme.palette.canvasBg
    val canvasDot get() = Theme.palette.canvasDot
    val canvasFrame get() = Theme.palette.canvasFrame
    val frameBg get() = Theme.palette.frameBg
    val codeBg get() = Theme.palette.codeBg
    val codeText get() = Theme.palette.codeText

    val rLg = 16.dp
    val r = 10.dp
    val rSm = 7.dp
    val rXs = 5.dp
    val gap = 10.dp
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val p = Theme.palette
    val scheme = if (p.isDark) {
        darkColorScheme(
            primary = p.accent, onPrimary = Color.White,
            background = p.appBg, onBackground = p.textPrimary,
            surface = p.panel, onSurface = p.textPrimary,
            surfaceVariant = p.elevated, onSurfaceVariant = p.textSecondary,
            outline = p.borderStrong, outlineVariant = p.border, error = p.danger,
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = Color.White,
            background = p.appBg, onBackground = p.textPrimary,
            surface = p.panel, onSurface = p.textPrimary,
            surfaceVariant = p.elevated, onSurfaceVariant = p.textSecondary,
            outline = p.borderStrong, outlineVariant = p.border, error = p.danger,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
