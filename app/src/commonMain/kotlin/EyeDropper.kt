package composer

/**
 * Screen color sampling. The JVM designer does not support this yet, so the
 * picker button hides itself.
 */
expect object ScreenEyeDropper {
    val supported: Boolean

    /**
     * Open the eyedropper and return the sampled color as opaque `0xFFRRGGBB`,
     * or null when unsupported or cancelled.
     */
    suspend fun pick(): Long?
}
