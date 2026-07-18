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
        modifier = listOf(FillMaxSize(), Padding(16)),
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

    @Test
    fun raw_code_round_trips_verbatim() {
        // Opaque preserved source (IDE plugin) — adversarial content must survive
        // JSON encode/decode byte-for-byte: quotes, $-templates, raw strings, newlines.
        val raw = Node.RawCode(
            "rc",
            "if (x > 0) {\n    println(\"v: \$x\")\n    val s = \"\"\"raw \" text\"\"\"\n}",
        )
        val wrapped = Node.Column("c", children = listOf(raw))
        val decoded = DesignJson.decode(DesignJson.encode(wrapped))
        assertEquals(wrapped, decoded)
        assertTrue("\"type\": \"RawCode\"" in DesignJson.encode(wrapped))
    }

    @Test
    fun source_backed_text_round_trips_with_external_modifier() {
        val sourceBacked = Node.Text(
            id = "dynamic",
            text = "Hello name!",
            modifier = listOf(
                ModifierSpec.External(
                    "modifier.fillMaxWidth().customLayout()",
                    opaque = true,
                    preview = listOf(ModifierSpec.FillMaxWidth()),
                ),
            ),
            textExpression = "\"Hello ${'$'}name!\"",
        )
        val json = DesignJson.encode(sourceBacked)
        assertEquals(sourceBacked, DesignJson.decode(json))
        assertTrue("\"type\": \"external\"" in json, json)
    }

    @Test
    fun scaffold_padding_marker_round_trips_in_modifier_order() {
        val box = Node.Box(
            id = "content",
            modifier = listOf(
                FillMaxSize(),
                ModifierSpec.ScaffoldPadding("innerPadding"),
                Padding(8),
            ),
        )
        val json = DesignJson.encode(box)
        assertEquals(box, DesignJson.decode(json))
        assertTrue("\"type\": \"scaffoldPadding\"" in json, json)
    }
}
