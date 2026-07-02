package composer.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SlotTest {

    private fun scaffoldWithSlots(): Node.Scaffold = Node.Scaffold(
        id = "sc",
        topBar = Node.Slot("sc-topBar", "topBar"),
        bottomBar = Node.Slot("sc-bottomBar", "bottomBar"),
        fab = Node.Slot("sc-fab", "fab"),
        children = listOf(Node.Text("body", "content")),
    )

    // --- normalization (migration) -------------------------------------------

    @Test
    fun legacyScaffoldSlotsNormalizeIntoSlotNodes() {
        // Old shape: bare TopAppBar in topBar, fab absent.
        val legacy = Node.Scaffold(
            id = "sc",
            topBar = Node.TopAppBar("bar", title = Node.Text("t", "Title")),
            children = listOf(Node.Text("body", "content")),
        )
        val normalized = legacy.normalizeSlots() as Node.Scaffold
        val top = assertIs<Node.Slot>(normalized.topBar)
        assertEquals("topBar", top.name)
        assertEquals("bar", top.children.single().id, "legacy content moves inside the slot")
        val bottom = assertIs<Node.Slot>(normalized.bottomBar)
        assertTrue(bottom.children.isEmpty(), "absent slot becomes an empty Slot")
        assertIs<Node.Slot>(normalized.fab)
    }

    @Test
    fun normalizationRecursesAndIsIdempotent() {
        val nested = Node.Composable("s1", children = listOf(
            Node.Box("box", children = listOf(Node.Scaffold("sc"))),
        ))
        val once = nested.normalizeSlots()
        val sc = once.findById("sc") as Node.Scaffold
        assertIs<Node.Slot>(sc.topBar)
        assertEquals(once, once.normalizeSlots(), "already-normalized trees pass through")
    }

    @Test
    fun migrateToArtboardNormalizesSlots() {
        val migrated = Node.Scaffold("sc").migrateToArtboard()
        val sc = migrated.findById("sc") as Node.Scaffold
        assertIs<Node.Slot>(sc.topBar)
    }

    // --- permanence -----------------------------------------------------------

    @Test
    fun slotsCannotBeRemoved() {
        val ab = Node.Artboard("ab", composables = listOf(Node.Composable("s1", children = listOf(scaffoldWithSlots()))))
        val after = ab.removeById("sc-topBar")
        assertNotNull(after.findById("sc-topBar"), "removing a slot is a no-op")
    }

    @Test
    fun slotChildrenCanBeRemoved() {
        val sc = scaffoldWithSlots().copy(topBar = Node.Slot("sc-topBar", "topBar", children = listOf(Node.Text("x", "hi"))))
        val ab = Node.Artboard("ab", composables = listOf(Node.Composable("s1", children = listOf(sc))))
        val after = ab.removeById("x")
        assertEquals(null, after.findById("x"))
        assertNotNull(after.findById("sc-topBar"))
    }

    @Test
    fun slotsCannotBeMoved() {
        val sc = scaffoldWithSlots()
        val ab = Node.Artboard("ab", composables = listOf(Node.Composable("s1", children = listOf(sc, Node.Box("box")))))
        assertEquals(ab, ab.moveInto("sc-topBar", "box"), "a slot can't be dragged elsewhere")
        assertEquals(ab, ab.moveBefore("sc-topBar", "body"), "a slot can't be reordered into content")
    }

    @Test
    fun componentsMoveIntoSlots() {
        val ab = Node.Artboard("ab", composables = listOf(Node.Composable("s1", children = listOf(scaffoldWithSlots(), Node.Text("t2", "drag me")))))
        val moved = ab.moveInto("t2", "sc-topBar")
        val slot = moved.findById("sc-topBar") as Node.Slot
        assertEquals("t2", slot.children.single().id)
    }

    @Test
    fun slotRoundTripsThroughJson() {
        val ab = Node.Artboard("ab", composables = listOf(Node.Composable("s1", children = listOf(scaffoldWithSlots()))))
        assertEquals(ab, DesignJson.decode(DesignJson.encode(ab)))
    }
}
