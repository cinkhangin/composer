package composer.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ButtonContainerTest {

    @Test
    fun legacyLabelBecomesTextChild() {
        val old = Node.Composable("root", children = listOf(Node.Button("b", "Click me")))
        val migrated = old.migrateToArtboard()
        val button = migrated.findById("b") as Node.Button
        assertEquals("", button.label, "legacy label cleared")
        val caption = assertIs<Node.Text>(button.children.single())
        assertEquals("Click me", caption.text)
    }

    @Test
    fun normalizationIsIdempotentAndRecursive() {
        val nested = Node.Composable("root", children = listOf(
            Node.Box("box", children = listOf(Node.Button("b", "Deep"))),
        ))
        val once = nested.normalizeButtons()
        val twice = once.normalizeButtons()
        assertEquals(once, twice)
        assertEquals("Deep", ((once.findById("b") as Node.Button).children.single() as Node.Text).text)
    }

    @Test
    fun buttonIsAContainerAndAcceptsChildren() {
        val button = Node.Button("b")
        assertTrue(button.isContainer())
        val withIcon = Node.Artboard(
            "ab",
            composables = listOf(Node.Composable("s", children = listOf(button, Node.Icon("i")))),
        ).moveInto("i", "b")
        assertEquals("i", (withIcon.findById("b") as Node.Button).children.single().id)
    }

    @Test
    fun buttonRoundTripsThroughJson() {
        val ab = Node.Artboard("ab", composables = listOf(Node.Composable("s", children = listOf(
            Node.Button("b", children = listOf(Node.Icon("i"), Node.Text("t", "Go"))),
        ))))
        assertEquals(ab, DesignJson.decode(DesignJson.encode(ab)))
    }
}
