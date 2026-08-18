package composer

import composer.codeparse.DesignParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ComposableFileStructureTest {
    @Test
    fun kotlin_source_files_have_at_most_one_non_preview_composable() {
        val sourceRoot = File("src")
        assertTrue(sourceRoot.isDirectory, "App source directory was not found: ${sourceRoot.absolutePath}")

        val violations = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .mapNotNull { file ->
                val functions = DesignParser.nonPreviewComposableFunctions(file.readText()).map { it.name }
                if (functions.size > 1) {
                    "${file.invariantSeparatorsPath}: ${functions.joinToString()}"
                } else {
                    null
                }
            }
            .toList()

        assertTrue(
            violations.isEmpty(),
            "Kotlin files must contain at most one non-preview composable:\n${violations.joinToString("\n")}",
        )
    }
}
