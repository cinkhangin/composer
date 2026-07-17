package composer.idea.app

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.FormBuilder
import composer.codegen.CodeGen
import composer.codeparse.AppParser
import composer.codeparse.DesignParser
import composer.codeparse.NewComposableSource
import composer.idea.ComposerNotifications
import java.awt.event.ItemEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTextField

/** IDE-owned creation flow for app-mode composables. */
object NewComposableCreator {
    private enum class Destination { NewFile, CurrentFile }

    private class DestinationOption(
        val destination: Destination,
        private val label: String,
    ) {
        override fun toString(): String = label
    }

    private class Dialog(
        project: Project,
        private val currentFile: VirtualFile,
        suggestedName: String,
        private val knownNames: Set<String>,
    ) : DialogWrapper(project) {
        val nameField = JTextField(suggestedName, 28)
        private val newFileOption = DestinationOption(
            Destination.NewFile,
            "Create a new Kotlin file next to ${currentFile.name}",
        )
        private val currentFileOption = DestinationOption(
            Destination.CurrentFile,
            "Add to current file (${currentFile.name})",
        )
        val destinationCombo = ComboBox(arrayOf(newFileOption, currentFileOption))
        private val targetLabel = JLabel()

        val functionName: String
            get() = CodeGen.sanitizeName(nameField.text.trim()).orEmpty()

        val destination: Destination
            get() = (destinationCombo.selectedItem as DestinationOption).destination

        init {
            title = "New Composable"
            destinationCombo.addItemListener {
                if (it.stateChange == ItemEvent.SELECTED) updateTarget(currentFile)
            }
            updateTarget(currentFile)
            init()
        }

        private fun updateTarget(currentFile: VirtualFile) {
            targetLabel.text = when (destination) {
                Destination.NewFile -> currentFile.parent?.path.orEmpty() + "/<name>.kt"
                Destination.CurrentFile -> currentFile.path
            }
        }

        override fun createCenterPanel(): JComponent = FormBuilder.createFormBuilder()
            .addLabeledComponent("Composable name:", nameField)
            .addLabeledComponent("Create in:", destinationCombo)
            .addLabeledComponent("Target:", targetLabel)
            .panel

        override fun doValidate(): ValidationInfo? {
            val name = functionName
            return when {
                name.isEmpty() -> ValidationInfo("Enter a valid composable name.", nameField)
                name in knownNames -> ValidationInfo("A top-level function named $name already exists.", nameField)
                destination == Destination.NewFile && currentFile.parent?.findChild("$name.kt") != null ->
                    ValidationInfo("$name.kt already exists.", nameField)
                else -> null
            }
        }
    }

    fun show(project: Project, service: ComposerAppService) {
        val current = FileEditorManager.getInstance(project).selectedFiles
            .firstOrNull { it.extension == "kt" && service.ownsFile(it) }
            ?: service.defaultSourceFile()
        if (current == null || !current.isValid) {
            ComposerNotifications.warnOnce(
                project,
                "composer.app.new.no-current-file",
                "Composer couldn't find a Kotlin source file for the new composable.",
            )
            return
        }

        val fileManager = FileDocumentManager.getInstance()
        val currentText = fileManager.getDocument(current)?.text ?: LoadTextUtil.loadText(current).toString()
        val namesInCurrent = DesignParser.skeleton(currentText).topLevelFunctionNames
        val knownNames = namesInCurrent + service.lastParsed?.functionNamesById?.values.orEmpty()
        val dialog = Dialog(project, current, service.suggestedComposableName(), knownNames.toSet())
        if (!dialog.showAndGet()) return

        val name = dialog.functionName
        val result = runCatching {
            when (dialog.destination) {
                Destination.NewFile -> createInNewFile(project, service, current, name)
                Destination.CurrentFile -> appendToCurrentFile(project, service, current, currentText, name)
            }
        }
        result.onFailure { error ->
            ComposerNotifications.warnOnce(
                project,
                "composer.app.new.failed:${error.message.hashCode()}",
                "Composer couldn't create $name: ${error.message ?: "unknown error"}",
            )
        }
    }

    private fun createInNewFile(
        project: Project,
        service: ComposerAppService,
        current: VirtualFile,
        name: String,
    ) {
        val parent = current.parent ?: error("The current file has no source directory.")
        check(parent.findChild("$name.kt") == null) { "$name.kt already exists." }
        val packageName = AppParser.packageNameOf(
            FileDocumentManager.getInstance().getDocument(current)?.text
                ?: LoadTextUtil.loadText(current).toString(),
        )
        val initial = packageName?.let { "package $it\n" }.orEmpty()
        val source = NewComposableSource.append(initial, name)
        var created: VirtualFile? = null
        WriteCommandAction.runWriteCommandAction(project, "Create Composable", "composer.app.new-composable", {
            val file = parent.createChildData(this, "$name.kt")
            VfsUtil.saveText(file, source)
            created = file
        })
        val file = created ?: error("The Kotlin file could not be created.")
        service.scheduleParse()
        navigateToFunction(project, file, source, name)
    }

    private fun appendToCurrentFile(
        project: Project,
        service: ComposerAppService,
        current: VirtualFile,
        originalText: String,
        name: String,
    ) {
        val updated = NewComposableSource.append(originalText, name)
        val document = FileDocumentManager.getInstance().getDocument(current)
            ?: error("The current Kotlin file is not editable.")
        WriteCommandAction.runWriteCommandAction(project, "Create Composable", "composer.app.new-composable", {
            document.setText(updated)
        })
        service.scheduleParse()
        navigateToFunction(project, current, updated, name)
    }

    private fun navigateToFunction(project: Project, file: VirtualFile, text: String, name: String) {
        val offset = text.indexOf("fun $name").coerceAtLeast(0)
        OpenFileDescriptor(project, file, offset).navigate(true)
    }
}
