package composer.model

import kotlin.test.Test
import kotlin.test.assertEquals

class NamedThemeTest {

    @Test
    fun legacySingleThemeLiftsIntoNamedList() {
        val legacyDark = Node.Artboard("ab", theme = DesignTheme(dark = true))
        val migrated = legacyDark.migrateThemes()
        assertEquals(listOf(NamedTheme("Dark", DesignTheme(dark = true))), migrated.themes)
        assertEquals(0, migrated.activeTheme)
        assertEquals(DesignTheme(dark = true), migrated.currentTheme())

        val legacyLight = Node.Artboard("ab", theme = DesignTheme(primary = 0xFF123456))
        assertEquals("Light", legacyLight.migrateThemes().themes.single().name)
    }

    @Test
    fun migrationIsIdempotentAndPreservesExistingThemes() {
        val ab = Node.Artboard(
            "ab",
            themes = listOf(NamedTheme("Light"), NamedTheme("Dark", DesignTheme(dark = true))),
            activeTheme = 1,
        )
        assertEquals(ab, ab.migrateThemes())
        assertEquals(DesignTheme(dark = true), ab.currentTheme())
    }

    @Test
    fun currentThemeClampsOutOfRangeIndex() {
        val ab = Node.Artboard("ab", themes = listOf(NamedTheme("Light")), activeTheme = 7)
        assertEquals(DesignTheme(), ab.currentTheme())
    }

    @Test
    fun migrateToArtboardSeedsThemesFromOldFrameRoot() {
        val old = Node.Composable("root", theme = DesignTheme(dark = true))
        val ab = old.migrateToArtboard()
        assertEquals("Dark", ab.themes.single().name)
        assertEquals(DesignTheme(dark = true), ab.currentTheme())
    }

    @Test
    fun themesRoundTripThroughJson() {
        val ab = Node.Artboard(
            "ab",
            composables = listOf(Node.Composable("s1")),
            themes = listOf(NamedTheme("Light"), NamedTheme("Brand", DesignTheme(primary = 0xFF112233))),
            activeTheme = 1,
        )
        assertEquals(ab, DesignJson.decode(DesignJson.encode(ab)))
    }
}
