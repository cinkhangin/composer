package composer

/**
 * Screen color sampling. Web: the Chromium EyeDropper API (the browser shows
 * its own magnifier loupe; [supported] false elsewhere). JVM: unsupported for
 * now — the picker button hides itself.
 */
expect object ScreenEyeDropper {
    val supported: Boolean

    /**
     * Open the eyedropper and return the sampled color as opaque `0xFFRRGGBB`,
     * or null when unsupported or cancelled.
     */
    suspend fun pick(): Long?
}
