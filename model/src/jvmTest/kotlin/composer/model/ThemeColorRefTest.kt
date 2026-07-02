package composer.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThemeColorRefTest {
    @Test
    fun token_round_trips_for_every_token() {
        for (name in DesignTheme.TOKENS) {
            val ref = ThemeColorRef.token(name)!!
            assertEquals(name, ThemeColorRef.tokenName(ref))
        }
    }

    @Test
    fun plain_argb_is_not_a_token() {
        assertNull(ThemeColorRef.tokenName(0xFF2196F3))
        assertNull(ThemeColorRef.tokenName(0x00000000))
        assertNull(ThemeColorRef.token("notAToken"))
    }

    @Test
    fun resolve_follows_the_theme() {
        val theme = DesignTheme(primary = 0xFF123456)
        val ref = ThemeColorRef.token("primary")!!
        assertEquals(0xFF123456, ThemeColorRef.resolve(ref, theme))
        // plain colors pass through
        assertEquals(0xFFABCDEF, ThemeColorRef.resolve(0xFFABCDEF, theme))
    }
}
