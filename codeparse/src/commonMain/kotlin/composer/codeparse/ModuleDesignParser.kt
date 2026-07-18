package composer.codeparse

import composer.model.Node

/** One Kotlin source file containing at least one renderable composable. */
data class ParsedModuleFile(
    val path: String,
    val design: ParsedDesign,
)

/** Module-wide projection used before stable annotation grouping is available. */
data class ParsedModuleDesign(
    val artboard: Node.Artboard,
    val files: List<ParsedModuleFile>,
    /** Screen id → original callable name. */
    val functionNamesById: Map<String, String>,
    val warnings: List<String> = emptyList(),
)

data class ModuleFilePlan(val path: String, val edits: List<TextEdit>)

data class ModuleWriteBackPlan(
    val files: List<ModuleFilePlan>,
    val warnings: List<String> = emptyList(),
    val blockedReason: String? = null,
)

/**
 * Aggregates every parseable top-level `@Composable` function in a module.
 * Source files are sorted by path and declarations keep source order, making
 * the projection deterministic. IDs are path + declaration-ordinal based until
 * explicit Composer annotations provide durable identity.
 */
object ModuleDesignParser {
    private data class Decl(
        val file: SourceFile,
        val ref: DesignParser.ComposableFunctionRef,
        val ordinal: Int,
        val id: String,
    )

    fun parse(files: List<SourceFile>): ParsedModuleDesign? {
        val sorted = files.sortedBy { it.path }
        val declarations = sorted.flatMap { file ->
            val fileKey = stablePathKey(file.path)
            DesignParser.composableFunctions(file.text).mapIndexed { index, ref ->
                Decl(file, ref, index, "m${fileKey}_${index + 1}")
            }
        }
        if (declarations.isEmpty()) return null

        // Simple-name calls can be linked across files only when unambiguous.
        val byName = declarations.groupBy { it.ref.name }
        val globalNames = byName
            .filterValues { it.size == 1 }
            .mapValues { (_, refs) -> refs.single().id }
        val indexById = declarations.mapIndexed { index, decl -> decl.id to index }.toMap()

        val parsedFiles = mutableListOf<ParsedModuleFile>()
        val allScreens = mutableListOf<Node.Composable>()
        val allLayerNames = mutableMapOf<String, String>()
        val componentIds = linkedSetOf<String>()

        for (file in sorted) {
            val fileDecls = declarations.filter { it.file === file }
            if (fileDecls.isEmpty()) continue
            val fixedByOffset = fileDecls.associate { it.ref.startOffset to it.id }
            val design = DesignParser.parse(
                file.text,
                DesignParser.ExternalNames(
                    screenIdsByName = globalNames,
                    navBaseToScreenId = globalNames,
                    fixedScreenIdsByOffset = fixedByOffset,
                    idPrefix = "f${stablePathKey(file.path)}-",
                ),
            ) ?: continue
            val positioned = design.artboard.composables.map { node ->
                val screen = node as Node.Composable
                val index = indexById.getValue(screen.id)
                screen.copy(x = (index % COLUMNS) * X_STEP, y = (index / COLUMNS) * Y_STEP)
            }
            val positionedArtboard = design.artboard.copy(composables = positioned)
            parsedFiles += ParsedModuleFile(file.path, design.copy(artboard = positionedArtboard))
            allScreens += positioned
            allLayerNames += positionedArtboard.layerNames
            componentIds += positionedArtboard.componentIds
        }
        if (allScreens.isEmpty()) return null

        // RawCode is preservation metadata, not visual content. Resolve this
        // module-wide so a screen containing only a cross-file instance still
        // survives when that instance eventually reaches real UI.
        val renderableIds = renderableScreenIds(allScreens)
        if (renderableIds.isEmpty()) return null
        val skippedIds = allScreens.mapTo(linkedSetOf()) { it.id } - renderableIds
        val renderedOrder = allScreens
            .filter { it.id in renderableIds }
            .mapIndexed { index, screen -> screen.id to index }
            .toMap()
        val sourceByPath = sorted.associateBy { it.path }
        val renderedFiles = parsedFiles.mapNotNull { parsedFile ->
            val source = sourceByPath.getValue(parsedFile.path)
            val retainedScreens = parsedFile.design.artboard.composables
                .filterIsInstance<Node.Composable>()
                .filter { it.id in renderableIds }
                .map { screen ->
                    val index = renderedOrder.getValue(screen.id)
                    (screen.preserveSkippedInstances(
                        skippedIds,
                        source.text,
                        parsedFile.design.sourceRanges,
                    ) as Node.Composable)
                        .copy(x = (index % COLUMNS) * X_STEP, y = (index / COLUMNS) * Y_STEP)
                }
            if (retainedScreens.isEmpty()) return@mapNotNull null
            val retainedById = retainedScreens.associateBy { it.id }
            val retainedFunctions = parsedFile.design.functions
                .filter { it.screenId in renderableIds }
                .map { fn -> fn.copy(treeHash = ParsedFunction.hashOf(retainedById.getValue(fn.screenId))) }
            val retainedArtboard = parsedFile.design.artboard.copy(
                composables = retainedScreens,
                layerNames = parsedFile.design.artboard.layerNames.filterKeys { it in renderableIds },
                componentIds = parsedFile.design.artboard.componentIds.filter { it in renderableIds },
            )
            ParsedModuleFile(
                parsedFile.path,
                parsedFile.design.copy(
                    artboard = retainedArtboard,
                    functions = retainedFunctions,
                    hasNonScreenDeclarations = parsedFile.design.hasNonScreenDeclarations ||
                        parsedFile.design.functions.any { it.screenId in skippedIds },
                ),
            )
        }
        val renderedScreens = renderedFiles
            .flatMap { it.design.artboard.composables }
            .filterIsInstance<Node.Composable>()
            .sortedBy { renderedOrder.getValue(it.id) }

        val extractedThemes = ThemeParser.parse(sorted)
        val base = Node.Artboard(id = "artboard")
        val artboard = base.copy(
            composables = renderedScreens,
            layerNames = allLayerNames.filterKeys { it in renderableIds },
            componentIds = componentIds.filter { it in renderableIds },
            themes = extractedThemes.themes,
            activeTheme = extractedThemes.active,
        )
        val renderedDeclarations = declarations.filter { it.id in renderableIds }
        val duplicateNames = renderedDeclarations.groupBy { it.ref.name }.filterValues { it.size > 1 }.keys.sorted()
        val warnings = if (duplicateNames.isEmpty()) emptyList() else listOf(
            "Duplicate composable names are rendered separately, but ambiguous cross-file calls stay locked: " +
                duplicateNames.joinToString(", "),
        )
        return ParsedModuleDesign(
            artboard = artboard,
            files = renderedFiles,
            functionNamesById = renderedDeclarations.associate { it.id to it.ref.name },
            warnings = warnings,
        )
    }

    private fun stablePathKey(path: String): String {
        var hash = 0x811C9DC5u
        for (char in path) hash = (hash xor char.code.toUInt()) * 0x01000193u
        return hash.toString(16)
    }

    private const val COLUMNS = 4
    private const val X_STEP = 470
    private const val Y_STEP = 920
}

/**
 * Safe pre-annotation write-back: existing function bodies may change, while
 * function creation/deletion/rename, theme ownership, and component ownership
 * wait for stable annotation IDs.
 */
object ModuleWriteBackPlanner {
    fun plan(
        previous: ParsedModuleDesign,
        filesNow: Map<String, String>,
        edited: Node.Artboard,
    ): ModuleWriteBackPlan {
        val expectedIds = previous.functionNamesById.keys
        val editedScreens = edited.composables.filterIsInstance<Node.Composable>()
        val editedById = editedScreens.associateBy { it.id }
        if (editedById.keys != expectedIds) {
            return blocked("Adding or deleting composable functions requires stable Composer annotations.")
        }
        val renamed = expectedIds.filter { id ->
            (edited.layerNames[id] ?: previous.functionNamesById.getValue(id)) != previous.functionNamesById.getValue(id)
        }
        if (renamed.isNotEmpty()) {
            return blocked("Renaming composable functions requires stable Composer annotations.")
        }
        if (edited.themes != previous.artboard.themes || edited.activeTheme != previous.artboard.activeTheme) {
            return blocked("Editing module theme declarations requires stable Composer annotations.")
        }
        if (edited.componentIds != previous.artboard.componentIds) {
            return blocked("Changing reusable-component ownership requires stable Composer annotations.")
        }

        val plans = mutableListOf<ModuleFilePlan>()
        val warnings = mutableListOf<String>()
        val externalComponents = previous.artboard.componentIds.toSet()
        for (file in previous.files) {
            val text = filesNow[file.path] ?: return blocked("${file.path} changed or disappeared during the edit.")
            val localIds = file.design.functions.map { it.screenId }
            val localArtboard = file.design.artboard.copy(
                composables = localIds.map { editedById.getValue(it) },
                layerNames = localIds.associateWith(previous.functionNamesById::getValue),
                componentIds = previous.artboard.componentIds,
            )
            val plan = WriteBackPlanner.plan(
                text = text,
                previous = file.design,
                edited = localArtboard,
                externalFunctionNames = previous.functionNamesById,
                externalComponentIds = externalComponents,
            )
            if (plan.renames.isNotEmpty()) return blocked("A composable rename could not be applied safely.")
            if (plan.edits.isNotEmpty()) plans += ModuleFilePlan(file.path, plan.edits)
            warnings += plan.warnings
        }
        return ModuleWriteBackPlan(plans, warnings.distinct())
    }

    private fun blocked(reason: String) =
        ModuleWriteBackPlan(emptyList(), warnings = listOf(reason), blockedReason = reason)
}
