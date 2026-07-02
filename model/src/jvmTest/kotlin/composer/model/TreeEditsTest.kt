package composer.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TreeEditsTest {

    private fun tree() = Node.Column(
        "root",
        children = listOf(
            Node.Text("a", "A"),
            Node.Row("row", children = listOf(Node.Button("b", "B"), Node.Text("c", "C"))),
        ),
    )

    @Test
    fun findById_and_parentOf() {
        val t = tree()
        assertEquals("B", (t.findById("b") as Node.Button).label)
        assertEquals("row", t.parentOf("b")?.id)
        assertEquals("root", t.parentOf("row")?.id)
        assertNull(t.parentOf("root"))
    }

    @Test
    fun insertChild_appends_into_container() {
        val t = tree().insertChild("row", Node.Text("new", "N"))
        val row = t.findById("row") as Node.Row
        assertEquals(listOf("b", "c", "new"), row.children.map { it.id })
    }

    @Test
    fun insertChild_at_index() {
        val t = tree().insertChild("row", Node.Text("new", "N"), index = 1)
        val row = t.findById("row") as Node.Row
        assertEquals(listOf("b", "new", "c"), row.children.map { it.id })
    }

    @Test
    fun insertChild_into_leaf_is_noop() {
        val t = tree()
        assertEquals(t, t.insertChild("a", Node.Text("new", "N")))
    }

    @Test
    fun removeById_removes_nested_node() {
        val t = tree().removeById("b")
        assertNull(t.findById("b"))
        assertEquals(listOf("c"), (t.findById("row") as Node.Row).children.map { it.id })
    }

    @Test
    fun moveById_reorders_within_siblings() {
        val down = tree().moveById("a", +1)
        assertEquals(listOf("row", "a"), (down as Node.Column).children.map { it.id })
        val up = down.moveById("a", -1)
        assertEquals(listOf("a", "row"), (up as Node.Column).children.map { it.id })
    }

    @Test
    fun moveById_at_boundary_is_noop() {
        val t = tree()
        assertEquals(t, t.moveById("a", -1)) // already first
        assertEquals(t, t.moveById("row", +1)) // already last
    }

    @Test
    fun isContainer_classifies_nodes() {
        assertTrue(Node.Column("x").isContainer())
        assertTrue(!Node.Text("x", "t").isContainer())
    }

    @Test
    fun moveBefore_reparents_and_orders() {
        // Move "a" to just before "c" (inside "row").
        val t = tree().moveBefore("a", "c")
        val row = t.findById("row") as Node.Row
        assertEquals(listOf("b", "a", "c"), row.children.map { it.id })
        assertEquals(listOf("row"), (t as Node.Column).children.map { it.id }) // "a" left root
    }

    @Test
    fun moveAfter_places_after_target() {
        val t = tree().moveAfter("a", "b")
        val row = t.findById("row") as Node.Row
        assertEquals(listOf("b", "a", "c"), row.children.map { it.id })
    }

    @Test
    fun moveInto_appends_into_container() {
        val t = tree().moveInto("a", "row")
        val row = t.findById("row") as Node.Row
        assertEquals(listOf("b", "c", "a"), row.children.map { it.id })
    }

    @Test
    fun moves_that_would_create_a_cycle_are_noops() {
        val t = tree()
        // Can't move "row" before its own descendant "b", or into itself.
        assertEquals(t, t.moveBefore("row", "b"))
        assertEquals(t, t.moveInto("row", "row"))
    }

    @Test
    fun cloneWithNewIds_reassigns_every_id_and_preserves_structure() {
        var n = 0
        val original = tree()
        val clone = original.cloneWithNewIds { "c${++n}" } as Node.Column

        // Structure + content preserved: Row's first child is still Button "B".
        val clonedButton = (clone.children[1] as Node.Row).children[0] as Node.Button
        assertEquals("B", clonedButton.label)

        // Every id is fresh (no overlap) and all ids are unique.
        val cloneIds = collectIds(clone)
        assertTrue(cloneIds.none { it in collectIds(original) }, cloneIds.toString())
        assertEquals(cloneIds.size, cloneIds.toSet().size)
        assertEquals(5, cloneIds.size)
    }

    private fun collectIds(node: Node): List<String> =
        listOf(node.id) + node.childNodes().flatMap { collectIds(it) }

    @Test
    fun dedupeIds_makes_all_ids_unique_keeping_first() {
        val tree = Node.Column(
            "root",
            children = listOf(Node.Text("dup", "a"), Node.Row("dup", children = listOf(Node.Text("dup", "b")))),
        )
        val deduped = tree.dedupeIds()
        val ids = collectIds(deduped)
        assertEquals(ids.size, ids.toSet().size, "all ids unique: $ids")
        assertEquals("root", deduped.id)
        assertEquals("dup", (deduped as Node.Column).children[0].id) // first keeps its id
    }
}
