package composer.codeparse

import composer.model.Node
import composer.model.childNodes
import composer.model.mapChildren

/**
 * Screens that can produce visible Compose UI. RawCode is preservation data,
 * not a designer component. An instance counts only when its target eventually
 * reaches a concrete renderable node; this also rejects instance-only cycles.
 */
internal fun renderableScreenIds(screens: List<Node.Composable>): Set<String> {
    val renderable = screens
        .filter { it.hasConcreteRenderableNode() }
        .mapTo(linkedSetOf()) { it.id }

    var changed: Boolean
    do {
        changed = false
        for (screen in screens) {
            if (screen.id !in renderable && screen.referencesAny(renderable)) {
                renderable += screen.id
                changed = true
            }
        }
    } while (changed)
    return renderable
}

private fun Node.hasConcreteRenderableNode(): Boolean = when (this) {
    is Node.RawCode, is Node.Instance -> false
    is Node.Artboard, is Node.Composable, is Node.Slot -> childNodes().any { it.hasConcreteRenderableNode() }
    else -> true
}

private fun Node.referencesAny(screenIds: Set<String>): Boolean =
    (this is Node.Instance && refId in screenIds) || childNodes().any { it.referencesAny(screenIds) }

/** Turn calls to filtered-out screens back into hidden preserved source. */
internal fun Node.preserveSkippedInstances(
    skippedIds: Set<String>,
    source: String,
    sourceRanges: Map<String, IntRange>,
): Node {
    if (this is Node.Instance && refId in skippedIds) {
        val range = sourceRanges[id]
        val code = range?.let {
            source.substring(it.first.coerceAtLeast(0), (it.last + 1).coerceAtMost(source.length))
        } ?: "// Preserved call to filtered composable: $refId"
        return Node.RawCode(id, code)
    }
    return mapChildren { it.preserveSkippedInstances(skippedIds, source, sourceRanges) }
}
