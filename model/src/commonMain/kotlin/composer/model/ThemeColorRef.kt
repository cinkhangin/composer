package composer.model

/**
 * Theme-token color references, packed into the existing `Long` color format.
 *
 * A plain color is `0xAARRGGBB` (fits in the low 32 bits). A value with [FLAG]
 * (bit 40) set instead references a [DesignTheme.TOKENS] entry by index — the
 * color follows the design's active theme (switch themes → everything using the
 * token re-colors, like Material component defaults), and codegen emits
 * `MaterialTheme.colorScheme.<token>` instead of a literal.
 *
 * Encoding tokens inside the `Long` means every existing color field
 * (`Node.Text.color`, `Background.color`/`colors`, `Border`, shadows) plus
 * serialization and clone logic work unchanged.
 */
object ThemeColorRef {
    const val FLAG: Long = 1L shl 40

    /** The reference value for [name], or null if it isn't a known token. */
    fun token(name: String): Long? {
        val i = DesignTheme.TOKENS.indexOf(name)
        return if (i >= 0) FLAG or i.toLong() else null
    }

    /** The token name a value references, or null for a plain ARGB color. */
    fun tokenName(value: Long): String? =
        if (value and FLAG != 0L) DesignTheme.TOKENS.getOrNull((value and 0xFFL).toInt()) else null

    /** ARGB of [value] under [theme]: token → the theme's effective color, else the value itself. */
    fun resolve(value: Long, theme: DesignTheme): Long =
        tokenName(value)?.let { theme.effective(it) } ?: value
}
