package composer.idea.app

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.FormBuilder
import composer.codegen.AppCodeGen
import composer.idea.ComposerNotifications
import composer.model.ComposablePreview
import composer.model.Node
import javax.swing.JComponent
import javax.swing.JTextField

/**
 * "Enable Composer App" scaffolding: pick a source root + package + first
 * screen name, then generate MainActivity plus the first screen triplet in one
 * global-undo write command. Build files are NEVER touched — required Gradle
 * dependencies are surfaced as a notification with a copy action.
 */
object ComposerAppScaffold {

    /** Coordinates the generated app needs; kept in one place for the notification. */
    private val REQUIRED_DEPS = """
        implementation("androidx.navigation3:navigation3-runtime:1.0.0-alpha01")
        implementation("androidx.navigation3:navigation3-ui:1.0.0-alpha01")
        implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
        // plus the kotlinx-serialization Gradle plugin:
        // kotlin("plugin.serialization")
    """.trimIndent()

    class Dialog(project: Project, roots: List<VirtualFile>, defaultPackage: String) : DialogWrapper(project) {
        val rootCombo = ComboBox(roots.map { it.path }.toTypedArray())
        val packageField = JTextField(defaultPackage, 30)
        val screenField = JTextField("Home", 30)
        val roots: List<VirtualFile> = roots

        init {
            title = "Enable Composer App Designer"
            init()
        }

        override fun createCenterPanel(): JComponent = FormBuilder.createFormBuilder()
            .addLabeledComponent("Source root:", rootCombo)
            .addLabeledComponent("Package:", packageField)
            .addLabeledComponent("First screen:", screenField)
            .panel

        override fun doValidate() = when {
            !packageField.text.matches(Regex("[A-Za-z_][\\w]*(\\.[A-Za-z_][\\w]*)*")) ->
                com.intellij.openapi.ui.ValidationInfo("Not a valid package name.", packageField)
            screenField.text.isBlank() -> com.intellij.openapi.ui.ValidationInfo("Screen name required.", screenField)
            else -> null
        }
    }

    /** Show the dialog and scaffold; returns the created MainActivity, or null when cancelled. */
    fun scaffold(project: Project): VirtualFile? {
        val rootManager = ProjectRootManager.getInstance(project)
        val roots = rootManager.contentSourceRoots
            .filter {
                it.isDirectory &&
                    !rootManager.fileIndex.isInTestSourceContent(it) &&
                    it.name !in setOf("res", "resources")
            }
        if (roots.isEmpty()) {
            ComposerNotifications.warnOnce(project, "composer.app.noroots", "No source roots found to scaffold into.")
            return null
        }
        val dialog = Dialog(project, roots, defaultPackageFor(roots.first()))
        if (!dialog.showAndGet()) return null
        val root = dialog.roots[dialog.rootCombo.selectedIndex]
        val pkg = dialog.packageField.text.trim()
        val screenName = dialog.screenField.text.trim()

        val artboard = Node.Artboard(
            id = "artboard",
            composables = listOf(
                Node.Composable(
                    "s1",
                    children = listOf(Node.Text("t1", "Hello, $screenName")),
                    preview = ComposablePreview(functionName = "${screenName}ScreenUIPreview"),
                ),
            ),
            layerNames = mapOf("s1" to screenName),
        )
        val files = AppCodeGen.generate(artboard, pkg)

        val relativePackage = pkg.replace('.', '/')
        val existingDir = root.findFileByRelativePath(relativePackage)
        val conflicts = files.map { it.path }.filter { existingDir?.findChild(it) != null }
        if (conflicts.isNotEmpty()) {
            ComposerNotifications.warnOnce(
                project,
                "composer.app.scaffold-conflict:${conflicts.sorted().joinToString("|")}",
                "Composer didn't scaffold the app because these files already exist: ${conflicts.joinToString(", ")}",
            )
            return null
        }

        var main: VirtualFile? = null
        WriteCommandAction.runWriteCommandAction(project, "Enable Composer App", "composer.app.scaffold", {
            CommandProcessor.getInstance().markCurrentCommandAsGlobal(project)
            val dir = VfsUtil.createDirectoryIfMissing(root, relativePackage) ?: return@runWriteCommandAction
            for (f in files) {
                // The preflight happens immediately before this write command on
                // the EDT. Use create-only semantics so a collision can never
                // degrade into an implicit source overwrite.
                val vf = dir.createChildData(this, f.path)
                VfsUtil.saveText(vf, f.text)
                if (f.path == "MainActivity.kt") main = vf
            }
        })
        main?.let { notifyDepsIfMissing(project) }
        return main
    }

    /** After enabling, surface any missing Gradle deps (never edit build files). */
    fun notifyDepsIfMissing(project: Project) {
        val base = project.guessProjectDir() ?: return
        val buildTexts = sequenceOf("build.gradle.kts", "build.gradle", "app/build.gradle.kts", "app/build.gradle")
            .mapNotNull { base.findFileByRelativePath(it) }
            .map { LoadTextUtil.loadText(it).toString() }
            .toList()
        val hasNav3 = buildTexts.any { "navigation3" in it }
        if (!hasNav3) {
            ComposerNotifications.infoOnceWithCopy(
                project,
                "composer.app.deps",
                "The generated app needs Navigation 3, ViewModel-Compose, and kotlinx-serialization. " +
                    "Add the dependencies to your module's build file.",
                copyLabel = "Copy dependencies",
                copyText = REQUIRED_DEPS,
            )
        }
    }

    private fun defaultPackageFor(root: VirtualFile): String {
        // Cheap text scan: the package of any .kt under the root, else a stub.
        fun scan(dir: VirtualFile, depth: Int): String? {
            if (depth > 4) return null
            for (child in dir.children) {
                if (!child.isDirectory && child.extension == "kt") {
                    return composer.codeparse.AppParser.packageNameOf(LoadTextUtil.loadText(child).toString())
                }
                if (child.isDirectory) scan(child, depth + 1)?.let { return it }
            }
            return null
        }
        return scan(root, 0) ?: "com.example.app"
    }
}
