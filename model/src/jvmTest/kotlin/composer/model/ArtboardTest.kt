package composer.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ArtboardTest {

    private fun screen(id: String, vararg children: Node) =
        Node.Composable(id = id, children = children.toList())

    private fun artboard(vararg screens: Node.Composable) =
        Node.Artboard(id = "ab", composables = screens.toList())

    // --- migration -----------------------------------------------------------

    @Test
    fun oldFrameRootMigratesToArtboard() {
        // Old persistence: a single "Frame" root carrying theme + layerNames.
        val oldJson = """
            {"type":"Frame","id":"root","children":[{"type":"Text","id":"t1","text":"hi"}],
             "theme":{"dark":true},"layerNames":{"t1":"Hero"}}
        """.trimIndent()
        val decoded = DesignJson.decode(oldJson)
        val migrated = decoded.migrateToArtboard(frameWidth = 800, frameHeight = 600)

        assertEquals(true, migrated.theme.dark, "theme lifts onto the artboard")
        assertEquals("Hero", migrated.layerNames["t1"], "layer names lift onto the artboard")
        assertEquals(1, migrated.composables.size)
        val screen = assertIs<Node.Composable>(migrated.composables.single())
        assertEquals("root", screen.id)
        assertEquals(800 to 600, screen.width to screen.height, "old global frame size seeds the screen")
        assertEquals(DesignTheme(), screen.theme, "legacy fields reset on the screen")
        assertTrue(screen.layerNames.isEmpty())
        assertEquals("hi", (screen.children.single() as Node.Text).text)
    }

    @Test
    fun artboardRootPassesThroughUnchanged() {
        // (modulo theme normalization, which seeds the named-themes list once)
        val ab = artboard(screen("s1", Node.Text("t", "x"))).migrateThemes()
        assertEquals(ab, ab.migrateToArtboard())
    }

    @Test
    fun artboardRoundTripsThroughJson() {
        val ab = Node.Artboard(
            id = "ab",
            composables = listOf(Node.Composable("s1", listOf(Node.Text("t", "x")), x = 40, y = 8, width = 390, height = 844)),
            theme = DesignTheme(dark = true),
            layerNames = mapOf("s1" to "Login"),
        )
        assertEquals(ab, DesignJson.decode(DesignJson.encode(ab)))
    }

    // --- structural rules ----------------------------------------------------

    @Test
    fun componentCannotSitDirectlyUnderArtboard() {
        val ab = artboard(screen("s1", Node.Text("t1", "a"), Node.Box("b1")))
        assertEquals(ab, ab.moveInto("t1", "ab"), "moveInto artboard is a no-op for components")
        assertEquals(ab, ab.moveBefore("t1", "s1"), "before a screen = under the artboard: no-op")
    }

    @Test
    fun composableCannotNestInsideContainers() {
        val ab = artboard(screen("s1", Node.Box("b1")), screen("s2"))
        assertEquals(ab, ab.moveInto("s2", "b1"), "a screen can't nest in a Box")
    }

    @Test
    fun composablesReorderUnderArtboard() {
        val ab = artboard(screen("s1"), screen("s2"), screen("s3"))
        val moved = ab.moveBefore("s3", "s1") as Node.Artboard
        assertEquals(listOf("s3", "s1", "s2"), moved.composables.map { it.id })
    }

    @Test
    fun componentsStillMoveFreelyInsideAScreen() {
        val ab = artboard(screen("s1", Node.Text("t1", "a"), Node.Box("b1")))
        val moved = ab.moveInto("t1", "b1")
        val box = moved.findById("b1") as Node.Box
        assertEquals("t1", box.children.single().id)
    }

    @Test
    fun typeNamesAndContainment() {
        assertEquals("Artboard", artboard().typeName())
        assertEquals("Composable", screen("s").typeName())
        assertTrue(artboard().isContainer())
        assertTrue(screen("s").isContainer())
    }
}
