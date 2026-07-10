package composer

/** No cross-screen eyedropper on the JVM host (yet) — the picker button hides itself. */
actual object ScreenEyeDropper {
    actual val supported: Boolean get() = false

    actual suspend fun pick(): Long? = null
}
