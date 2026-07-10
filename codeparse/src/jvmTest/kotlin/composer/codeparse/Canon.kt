package composer.codeparse

import composer.model.ModifierSpec
import composer.model.NavAction
import composer.model.Node
import composer.model.navAction
import composer.model.withNavAction
import composer.model.childNodes
import composer.model.mapChildren
import composer.model.withId
import composer.model.withModifier

/**
 * Canonical form for round-trip comparison: ids renumbered in document order
 * (code carries none), editor-only geometry and theme state zeroed, and the few
 * legitimately lossy encodings normalized on BOTH sides:
 *  - gradient `Background.color` mirrors the first stop (parser convention),
 *  - `Text.lineHeight` == auto (fontSize × 1.2) collapses to 0,
 *  - `Text.customFont` newlines become spaces (codegen's comment sanitizer),
 *  - web-image `placeholderColor` resets (not emitted for URL images);
 *    non-http urls parse back as placeholder images (data: is unrecoverable).
 */
private fun ModifierSpec.Background.normalizedUnit(): composer.model.CornerUnit =
    if (corner <= 0) composer.model.CornerUnit.Dp else cornerUnit

internal fun canon(root: Node): Node {
    val idMap = mutableMapOf<String, String>()
    var counter = 0
    fun assign(node: Node) {
        idMap[node.id] = "c${++counter}"
        node.childNodes().forEach(::assign)
    }
    assign(root)
    // Live navigation targets = screens on the artboard; a Navigate to anything
    // else is invisible in emitted code (codegen degrades it to None).
    val screenIds = (root as? Node.Artboard)?.composables?.mapTo(mutableSetOf()) { it.id } ?: emptySet<String>()

    fun rewrite(node: Node): Node {
        val renamed = node.withId(idMap.getValue(node.id))
        val fixed = when (renamed) {
            is Node.Artboard -> renamed.copy(
                theme = composer.model.DesignTheme(),
                themes = emptyList(),
                activeTheme = 0,
                layerNames = renamed.layerNames.mapKeys { (k, _) -> idMap[k] ?: k },
                componentIds = renamed.componentIds.mapNotNull { idMap[it] }.sorted(),
            )
            is Node.Composable -> renamed.copy(
                x = 0, y = 0, width = 390, height = 844,
                theme = composer.model.DesignTheme(), layerNames = emptyMap(),
            )
            is Node.Instance -> renamed.copy(refId = idMap[renamed.refId] ?: renamed.refId)
            is Node.Text -> renamed.copy(
                lineHeight = if (renamed.fontSize > 0 &&
                    renamed.lineHeight == Math.round(renamed.fontSize * 1.2).toInt()
                ) 0 else renamed.lineHeight,
                customFont = renamed.customFont.replace('\n', ' ').replace('\r', ' '),
            )
            is Node.Image -> when {
                renamed.url.startsWith("http://") || renamed.url.startsWith("https://") ->
                    renamed.copy(placeholderColor = 0xFFCFD4DC)
                else -> renamed.copy(url = "")
            }
            else -> renamed
        }
        val navFixed = when (val a = fixed.navAction()) {
            is NavAction.Navigate ->
                if (a.screenId in screenIds) fixed.withNavAction(NavAction.Navigate(idMap.getValue(a.screenId)))
                else fixed.withNavAction(NavAction.None)
            else -> fixed
        }
        val withMods = navFixed.let { n ->
            val mods = n.modifier.map { spec ->
                when {
                    spec is ModifierSpec.Background && spec.gradientStops().isNotEmpty() ->
                        spec.copy(color = spec.colors.first(), cornerUnit = spec.normalizedUnit())
                    // corner == 0 emits no shape argument, so the unit is unrecoverable —
                    // (0, Percent) and (0, Dp) are the same shape.
                    spec is ModifierSpec.Background -> spec.copy(cornerUnit = spec.normalizedUnit())
                    spec is ModifierSpec.Border -> spec.copy(cornerUnit = if (spec.corner <= 0) composer.model.CornerUnit.Dp else spec.cornerUnit)
                    spec is ModifierSpec.Clip -> spec.copy(cornerUnit = if (spec.corner <= 0) composer.model.CornerUnit.Dp else spec.cornerUnit)
                    spec is ModifierSpec.DropShadow -> spec.copy(cornerUnit = if (spec.corner <= 0) composer.model.CornerUnit.Dp else spec.cornerUnit)
                    spec is ModifierSpec.InnerShadow -> spec.copy(cornerUnit = if (spec.corner <= 0) composer.model.CornerUnit.Dp else spec.cornerUnit)
                    else -> spec
                }
            }
            if (mods == n.modifier) n else n.withModifier(mods)
        }
        return withMods.mapChildren(::rewrite)
    }
    return rewrite(root)
}
