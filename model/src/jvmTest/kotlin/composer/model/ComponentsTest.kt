package composer.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComponentsTest {
    private fun board(vararg screens: Node.Composable, components: List<String> = emptyList()) =
        Node.Artboard("art", composables = screens.toList(), componentIds = components)

    @Test
    fun valid_component_ids_drops_dangling_refs() {
        val b = board(
            Node.Composable("s1", children = listOf(Node.Text("card", "hi"))),
            components = listOf("card", "deleted"),
        )
        assertEquals(listOf("card"), b.validComponentIds())
    }

    @Test
    fun expanded_ids_follows_instances_transitively() {
        // badge (Text) <- card (Box containing an Instance of badge)
        val b = board(
            Node.Composable(
                "s1",
                children = listOf(
                    Node.Text("badge", "hi"),
                    Node.Box("card", children = listOf(Node.Instance("i1", "badge"))),
                ),
            ),
        )
        val ids = b.expandedIds("card")
        assertTrue("card" in ids && "i1" in ids && "badge" in ids, ids.toString())
    }

    @Test
    fun cannot_instantiate_inside_own_subtree() {
        val b = board(
            Node.Composable("s1", children = listOf(Node.Box("card", children = listOf(Node.Text("t", "x"))))),
            components = listOf("card"),
        )
        // inserting an instance of card INTO card (ancestors: art -> s1 -> card)
        assertFalse(b.canInstantiate("card", listOf("art", "s1", "card")))
        // but fine as a sibling (ancestors: art -> s1)
        assertTrue(b.canInstantiate("card", listOf("art", "s1")))
    }

    @Test
    fun transitive_cycle_is_rejected() {
        // a contains instance of b; inserting instance of a inside b would loop
        val b = board(
            Node.Composable(
                "s1",
                children = listOf(
                    Node.Box("a", children = listOf(Node.Instance("i1", "b"))),
                    Node.Box("b", children = listOf(Node.Text("t", "x"))),
                ),
            ),
        )
        assertFalse(b.canInstantiate("a", listOf("art", "s1", "b")))
        // another instance of b inside a is NOT a cycle — a merely uses b twice
        assertTrue(b.canInstantiate("b", listOf("art", "s1", "a")))
    }
}
