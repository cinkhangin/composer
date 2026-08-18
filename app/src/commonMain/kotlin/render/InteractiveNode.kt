package composer.render

import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import composer.model.ModifierSpec
import composer.model.Node

/**
 * Renders an interactive [node] with a transparent tap-capturing overlay on top, so
 * a single tap selects it and a double tap drills in — the component's own gesture
 * (which would otherwise swallow the pointer) is left visually intact but inert.
 * The node's real modifier chain (size, background, …) is applied to [content].
 */
@Composable
internal fun InteractiveNode(
    node: Node,
    onSelect: (id: String, deep: Boolean) -> Unit,
    onBounds: (String, LayoutCoordinates) -> Unit,
    scopeModifier: Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    // Split the modifier: positional Offset goes on the wrapper Box (so the tap overlay
    // and measured bounds move with the component), everything else (size, background, …)
    // styles the inner component. Otherwise a moved component's hit-area lagged its visual.
    val scheme = MaterialTheme.colorScheme
    val previewModifiers = node.modifier.previewSpecs()
    val offsetMod = previewModifiers.filterIsInstance<ModifierSpec.Offset>().toModifier(scheme)
    val innerMod = previewModifiers.filterNot { it is ModifierSpec.Offset }.toModifier(scheme)
    Box(modifier = scopeModifier.then(offsetMod).onGloballyPositioned { onBounds(node.id, it) }) {
        content(innerMod)
        Box(
            Modifier.matchParentSize().pointerInput(node.id) {
                awaitEachGesture {
                    // Consume the DOWN immediately. The overlay is the top sibling, so it
                    // processes the press first in the Main pass; consuming it here means the
                    // component's own clickable/toggle/drag (a lower sibling) sees it already
                    // consumed and never fires. detectTapGestures doesn't consume the down
                    // early enough — that's why Buttons in particular swallowed the tap.
                    awaitFirstDown().consume()
                    val up = waitForUpOrCancellation()
                    if (up != null) {
                        up.consume()
                        onSelect(node.id, false)
                    }
                }
            },
        )
    }
}
