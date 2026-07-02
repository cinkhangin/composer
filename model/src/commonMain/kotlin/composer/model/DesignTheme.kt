package composer.model

import kotlinx.serialization.Serializable

/**
 * A design's Material 3 theme — a small, editable subset of `ColorScheme` plus a
 * light/dark base. Pure data (no Compose): the renderer maps it to a real
 * `ColorScheme` for the preview, and [composer.codegen.CodeGen] emits it as a
 * `MaterialTheme(colorScheme = …) { }` wrapper.
 *
 * Defaults are the Material 3 **baseline light** scheme. [dark] only selects which
 * builder (`lightColorScheme`/`darkColorScheme`) fills the ~20 roles we don't expose;
 * a token left at its light default is not emitted, so a dark theme keeps the dark
 * builder's value for it (see [changedTokens]).
 *
 * Token names match both the `ColorScheme` property and the codegen argument, so
 * [TOKENS] drives the editor, the renderer, and codegen uniformly.
 */
@Serializable
data class DesignTheme(
    val dark: Boolean = false,
    val primary: Long = 0xFF6750A4,
    val onPrimary: Long = 0xFFFFFFFF,
    val secondary: Long = 0xFF625B71,
    val tertiary: Long = 0xFF7D5260,
    val error: Long = 0xFFB3261E,
    val background: Long = 0xFFFFFBFE,
    val onBackground: Long = 0xFF1C1B1F,
    val surface: Long = 0xFFFFFBFE,
    val onSurface: Long = 0xFF1C1B1F,
) {
    /** ARGB value of [token] (a name from [TOKENS]); falls back to [primary] for an unknown name. */
    fun get(token: String): Long = when (token) {
        "primary" -> primary
        "onPrimary" -> onPrimary
        "secondary" -> secondary
        "tertiary" -> tertiary
        "error" -> error
        "background" -> background
        "onBackground" -> onBackground
        "surface" -> surface
        "onSurface" -> onSurface
        else -> primary
    }

    /** A copy with [token] set to [value]; unknown names are ignored. */
    fun set(token: String, value: Long): DesignTheme = when (token) {
        "primary" -> copy(primary = value)
        "onPrimary" -> copy(onPrimary = value)
        "secondary" -> copy(secondary = value)
        "tertiary" -> copy(tertiary = value)
        "error" -> copy(error = value)
        "background" -> copy(background = value)
        "onBackground" -> copy(onBackground = value)
        "surface" -> copy(surface = value)
        "onSurface" -> copy(onSurface = value)
        else -> this
    }

    /** Tokens whose value differs from the baseline-light default — the ones worth emitting. */
    fun changedTokens(): List<Pair<String, Long>> {
        val base = DEFAULT
        return TOKENS.filter { get(it) != base.get(it) }.map { it to get(it) }
    }

    /** True once the theme has been touched (dark base picked, or any token changed). */
    fun isCustomized(): Boolean = dark || changedTokens().isNotEmpty()

    /**
     * The EFFECTIVE color of [token] — what actually renders. Untouched tokens
     * (still at the light-baseline stored value) resolve to the base scheme's
     * builder default: dark baseline for a dark theme, light for light. Mirrors
     * the renderer's `darkColorScheme()/lightColorScheme() + changed overrides`,
     * so inspector swatches show the same colors as the canvas.
     */
    fun effective(token: String): Long {
        val stored = get(token)
        if (stored != DEFAULT.get(token)) return stored // user-set → wins
        return if (dark) DARK_DEFAULT.get(token) else stored
    }

    companion object {
        private val DEFAULT = DesignTheme()

        // Material 3 baseline DARK scheme values for the exposed tokens — what
        // `darkColorScheme()` yields, so `effective` matches the render.
        private val DARK_DEFAULT = DesignTheme(
            dark = true,
            primary = 0xFFD0BCFF,
            onPrimary = 0xFF381E72,
            secondary = 0xFFCCC2DC,
            tertiary = 0xFFEFB8C8,
            error = 0xFFF2B8B5,
            background = 0xFF1C1B1F,
            onBackground = 0xFFE6E1E5,
            surface = 0xFF1C1B1F,
            onSurface = 0xFFE6E1E5,
        )

        /** Editable color tokens in display order. Names match `ColorScheme` + codegen args. */
        val TOKENS: List<String> = listOf(
            "primary", "onPrimary", "secondary", "tertiary", "error",
            "background", "onBackground", "surface", "onSurface",
        )
    }
}
