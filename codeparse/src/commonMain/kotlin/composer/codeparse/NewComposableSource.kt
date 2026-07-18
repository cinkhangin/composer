package composer.codeparse

import composer.codegen.CodeGen
import composer.model.Node
import composer.model.findById

/** Safely append one blank, renderable composable to an existing Kotlin file. */
object NewComposableSource {
    fun append(text: String, functionName: String): String {
        require(CodeGen.sanitizeName(functionName) == functionName) { "Invalid composable function name: $functionName" }
        val previous = DesignParser.parse(text) ?: DesignParser.skeleton(text)
        require(functionName !in previous.topLevelFunctionNames) { "A top-level function named $functionName already exists." }

        var suffix = 1
        var screenId: String
        var boxId: String
        do {
            screenId = "composer-new-$suffix"
            boxId = "composer-new-box-$suffix"
            suffix++
        } while (previous.artboard.findById(screenId) != null || previous.artboard.findById(boxId) != null)

        // An empty Box keeps the new composable visible to the designer while
        // preserving the 0x0 empty-content sizing contract.
        val screen = Node.Composable(
            id = screenId,
            children = listOf(Node.Box(id = boxId)),
        )
        val edited = previous.artboard.copy(
            composables = previous.artboard.composables + screen,
            layerNames = previous.artboard.layerNames + (screenId to functionName),
        )
        return WriteBackPlanner.apply(text, WriteBackPlanner.plan(text, previous, edited))
    }
}
