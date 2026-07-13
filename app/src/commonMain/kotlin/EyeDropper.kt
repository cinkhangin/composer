package composer

/**
 * Screen color sampling. Supported browsers use the EyeDropper API; unsupported
 * browsers and the JVM designer hide the picker button.
 */
expect object ScreenEyeDropper {
    val supported: Boolean

    /**
     * Open the eyedropper and return the sampled color as opaque `0xFFRRGGBB`,
     * or null when unsupported or cancelled.
     */
    suspend fun pick(): Long?
}
