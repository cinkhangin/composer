package composer.model

import composer.model.ModifierSpec.Background
import composer.model.ModifierSpec.FillMaxSize
import composer.model.ModifierSpec.Padding
import composer.model.ModifierSpec.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesignJsonTest {

    private val tree: Node = Node.Column(
        id = "root",
        modifier = listOf(FillMaxSize, Padding(16)),
        children = listOf(
            Node.Text("title", "Hello", listOf(Padding(8))),
            Node.Row(
                "actions",
                children = listOf(
                    Node.Button("b", "Go"),
                    Node.Spacer("gap", listOf(Size(12, 0))),
                ),
            ),
            Node.Box("panel", modifier = listOf(Background(0xFFE0E0E0))),
        ),
    )

    @Test
    fun round_trips_through_json() {
        val json = DesignJson.encode(tree)
        assertEquals(tree, DesignJson.decode(json))
    }

    @Test
    fun uses_readable_type_discriminators() {
        val json = DesignJson.encode(tree)
        assertTrue("\"type\": \"Column\"" in json, json)
        assertTrue("\"type\": \"padding\"" in json, json)
        assertTrue("\"type\": \"fillMaxSize\"" in json, json)
    }

    @Test
    fun decode_is_stable_across_two_round_trips() {
        val once = DesignJson.decode(DesignJson.encode(tree))
        val twice = DesignJson.decode(DesignJson.encode(once))
        assertEquals(once, twice)
    }
}
