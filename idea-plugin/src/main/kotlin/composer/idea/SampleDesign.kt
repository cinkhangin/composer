package composer.idea

import composer.model.DesignJson
import composer.model.ModifierSpec
import composer.model.Node

/**
 * P3 skeleton payload: a design built from the shared :model types, proving the
 * IDE ⇄ designer round trip before the code parser exists. Includes a RawCode
 * node so the locked-chip path is exercised in the embedded designer too.
 */
fun sampleDesignJson(): String = DesignJson.encode(
    Node.Artboard(
        id = "art",
        composables = listOf(
            Node.Composable(
                id = "screen1",
                children = listOf(
                    Node.Column(
                        id = "col",
                        children = listOf(
                            Node.Text("t1", "Hello from IntelliJ"),
                            Node.Text("t2", "This design rode the JCEF bridge."),
                            Node.Button("b1", children = listOf(Node.Text("bt", "Click me"))),
                            Node.RawCode("rc", "if (loading) {\n    CircularProgressIndicator()\n}"),
                        ),
                        spacing = 12,
                        modifier = listOf(ModifierSpec.Padding(16)),
                    ),
                ),
            ),
        ),
        layerNames = mapOf("screen1" to "SampleScreen"),
    ),
)
