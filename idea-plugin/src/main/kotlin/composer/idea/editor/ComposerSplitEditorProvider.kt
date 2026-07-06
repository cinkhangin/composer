package composer.idea.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileTooBigException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.fileEditor.impl.LoadTextUtil

/**
 * Replaces the plain text editor for Kotlin files containing `@Composable` with
 * a [TextEditorWithPreview] (Code | Split | Design — the Compose-Preview shape):
 * the real Kotlin editor plus [ComposerPreviewEditor]. Defaults to the code
 * layout; the designer (JCEF + wasm) only boots when its pane is first shown.
 */
class ComposerSplitEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean {
        if (file.extension != "kt" || FileTypeRegistry.getInstance().isFileOfType(file, com.intellij.openapi.fileTypes.FileTypes.ARCHIVE)) return false
        if (file.length > MAX_SCAN_BYTES) return false
        // Cheap text scan — accept() must stay fast and PSI-free.
        val text = try {
            LoadTextUtil.loadText(file, MAX_SCAN_BYTES)
        } catch (e: FileTooBigException) {
            return false
        }
        return text.contains("@Composable")
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val textEditor = TextEditorProvider.getInstance().createEditor(project, file) as TextEditor
        val preview = ComposerPreviewEditor(project, file, textEditor)
        return TextEditorWithPreview(
            textEditor,
            preview,
            "Composer Designer",
            TextEditorWithPreview.Layout.SHOW_EDITOR,
        )
    }

    override fun getEditorTypeId(): String = "composer-designer"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

    private companion object {
        const val MAX_SCAN_BYTES = 512 * 1024
    }
}
