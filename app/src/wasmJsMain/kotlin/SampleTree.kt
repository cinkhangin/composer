package composer

import composer.model.DesignTheme
import composer.model.NamedTheme
import composer.model.Node

/**
 * The starting design — shown on first load and after "New File".
 *
 * The root is a [Node.Artboard]: the canvas that owns the named themes and
 * holds any number of [Node.Composable] screens. Each screen is one generated
 * `@Composable fun`, named after its layer name. The artboard itself can't be
 * deleted; screens can be added, renamed, moved, resized, and removed.
 *
 * New designs ship with a **Light + Dark** theme pair by default (Light active),
 * so the generated code always includes the `AppTheme` scaffold with both
 * schemes ready to switch.
 */
val emptyDesign: Node = Node.Artboard(
    id = "root",
    composables = listOf(
        Node.Composable(
            id = "screen1",
            children = listOf(
                Node.Text(id = "hello", text = "Hello world"),
            ),
            x = 0,
            y = 0,
            width = 390,
            height = 844,
        ),
    ),
    layerNames = mapOf("screen1" to "Screen 1"),
    themes = listOf(
        NamedTheme("Light"),
        NamedTheme("Dark", DesignTheme(dark = true)),
    ),
    activeTheme = 0,
)
