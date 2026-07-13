package composer.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavActionTest {

    private fun screen(vararg children: Node) = Node.Composable("s1", children = children.toList())

    @Test
    fun old_json_without_the_field_decodes_to_none() {
        val json = """{"type":"Button","id":"b1"}"""
        val node = DesignJson.decode("""{"type":"Artboard","id":"a","composables":[{"type":"Frame","id":"s1","children":[$json]}]}""")
        val button = (node as Node.Artboard).composables.first().childNodes().first() as Node.Button
        assertEquals(NavAction.None, button.navAction)
    }

    @Test
    fun unset_action_is_omitted_on_encode() {
        val tree = Node.Artboard("a", composables = listOf(screen(Node.Button("b1"))))
        val encoded = DesignJson.encode(tree)
        assertFalse("navAction" in encoded, encoded)
    }

    @Test
    fun navigate_and_back_round_trip_with_stable_serial_names() {
        val tree = Node.Artboard(
            "a",
            composables = listOf(
                screen(
                    Node.Button("b1", navAction = NavAction.Navigate("s2")),
                    Node.Fab("f1", navAction = NavAction.Back),
                ),
                Node.Composable("s2"),
            ),
        )
        val encoded = DesignJson.encode(tree)
        assertTrue("nav.navigate" in encoded, encoded)
        assertTrue("nav.back" in encoded, encoded)
        val decoded = DesignJson.decode(encoded) as Node.Artboard
        val kids = decoded.composables.first().childNodes()
        assertEquals(NavAction.Navigate("s2"), (kids[0] as Node.Button).navAction)
        assertEquals(NavAction.Back, (kids[1] as Node.Fab).navAction)
    }

    @Test
    fun helpers_read_and_write_all_five_clickables() {
        val nodes = listOf(
            Node.Button("b"), Node.IconButton("i"), Node.Fab("f"),
            Node.Chip("c"), Node.Card("k"),
        )
        for (n in nodes) {
            assertEquals(NavAction.None, n.navAction())
            val set = n.withNavAction(NavAction.Back)
            assertEquals(NavAction.Back, set.navAction(), n.typeName())
        }
        // Non-clickables: read null, write is identity.
        val text = Node.Text("t", "x")
        assertEquals(null, text.navAction())
        assertEquals(text, text.withNavAction(NavAction.Back))
    }

    @Test
    fun nav_targets_are_preorder_first_use_and_drop_dangling() {
        val s1 = screen(
            Node.Column(
                "col",
                children = listOf(
                    Node.Button("b1", navAction = NavAction.Navigate("s3")),
                    Node.Button("b2", navAction = NavAction.Navigate("s2")),
                    Node.Button("b3", navAction = NavAction.Navigate("s3")), // duplicate target
                    Node.Button("b4", navAction = NavAction.Navigate("gone")), // dangling
                ),
            ),
        )
        val artboard = Node.Artboard("a", composables = listOf(s1, Node.Composable("s2"), Node.Composable("s3")))
        assertEquals(listOf("s3", "s2"), s1.navTargets(artboard))
    }

    @Test
    fun has_back_action_walks_the_subtree() {
        val with = screen(Node.Card("k", children = listOf(Node.IconButton("i", navAction = NavAction.Back))))
        val without = screen(Node.Button("b"))
        assertTrue(with.hasBackAction())
        assertFalse(without.hasBackAction())
    }

    @Test
    fun clone_with_new_ids_keeps_nav_actions() {
        val original = Node.Button("b1", navAction = NavAction.Navigate("s9"))
        var n = 0
        val clone = original.cloneWithNewIds { "new${++n}" } as Node.Button
        assertEquals(NavAction.Navigate("s9"), clone.navAction)
    }
}
