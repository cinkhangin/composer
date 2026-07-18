package composer

import composer.model.DesignJson
import composer.model.Node
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesignerSessionTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun replays_newest_design_when_editor_callback_attaches_after_initial_load() {
        val session = DesignerSession {}
        val first = design("first", "First")
        val newest = design("newest", "Newest")

        session.deliver(loadEnvelope(1, first))
        session.deliver(loadEnvelope(2, newest))

        var loaded: Node? = null
        session.onLoadDesign = { loaded = it }

        assertEquals(newest, loaded)
        assertTrue(session.appMode)
    }

    @Test
    fun replays_pending_selection_when_editor_callback_attaches() {
        val session = DesignerSession {}
        session.deliver(
            json.encodeToString(
                DesignerMessage.serializer(),
                DesignerMessage(type = "selectNode", rev = 1, nodeId = "greeting"),
            ),
        )

        var selected: String? = null
        session.onSelectNode = { selected = it }

        assertEquals("greeting", selected)
    }

    private fun design(id: String, name: String): Node.Artboard {
        val screenId = "$id-screen"
        return Node.Artboard(
            id = id,
            composables = listOf(Node.Composable(id = screenId)),
            layerNames = mapOf(screenId to name),
        )
    }

    private fun loadEnvelope(rev: Int, design: Node): String = json.encodeToString(
        DesignerMessage.serializer(),
        DesignerMessage(
            type = "loadDesign",
            rev = rev,
            design = DesignJson.encode(design),
            appMode = true,
        ),
    )
}
