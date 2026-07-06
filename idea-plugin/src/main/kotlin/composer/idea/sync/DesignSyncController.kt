package composer.idea.sync

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import composer.codeparse.DesignParser
import composer.model.DesignJson
import composer.model.Node
import org.jetbrains.kotlin.psi.KtFile

/**
 * Code → designer sync (read direction): watches the file's document, re-parses
 * on a 500ms merge window, and pushes the design JSON to the embedded designer
 * when it actually changed. The write direction (designer → code) lands with
 * write-back; designer edits are ignored here.
 */
class DesignSyncController(
    private val project: Project,
    private val file: com.intellij.openapi.vfs.VirtualFile,
    private val push: (designJson: String) -> Unit,
) : Disposable {

    private val queue = MergingUpdateQueue("composer-design-sync", 500, true, null, this)

    @Volatile
    private var lastPushed: String? = null

    fun start() {
        FileDocumentManager.getInstance().getDocument(file)?.addDocumentListener(
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) = schedule()
            },
            this,
        )
        schedule()
    }

    fun schedule() {
        queue.queue(Update.create("parse") { parseAndPush() })
    }

    /** Runs on the EDT (MergingUpdateQueue default) — implicit read access. */
    private fun parseAndPush() {
        if (project.isDisposed || !file.isValid) return
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val ktFile = PsiManager.getInstance(project).findFile(file) as? KtFile ?: return
        val artboard = ReadAction.compute<Node.Artboard?, RuntimeException> {
            DesignParser.parse(ktFile)?.artboard
        } ?: Node.Artboard(id = "artboard") // no parseable @Composable → empty canvas
        val json = DesignJson.encode(artboard)
        if (json != lastPushed) {
            lastPushed = json
            push(json)
        }
    }

    override fun dispose() {}
}
