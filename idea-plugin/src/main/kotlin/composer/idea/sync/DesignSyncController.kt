package composer.idea.sync

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import composer.codeparse.DesignParser
import composer.codeparse.ParsedDesign
import composer.codeparse.WriteBackPlanner
import composer.model.DesignJson
import composer.model.Node
import org.jetbrains.kotlin.psi.KtFile

/**
 * Two-way sync between the Kotlin document and the embedded designer.
 *
 * Read direction: a DocumentListener + 500ms merge window re-parses and pushes
 * the design when it actually changed. Write direction ([applyDesignerEdit]):
 * decode → plan minimal edits ([WriteBackPlanner]) → one undoable write command
 * → re-parse. Loop guards: [suppressDocEvents] skips our own document writes;
 * [lastPushed] drops designer echoes; zero-edit plans (e.g. dragging a screen
 * on the artboard) leave both the document and the designer untouched.
 */
class DesignSyncController(
    private val project: Project,
    private val file: com.intellij.openapi.vfs.VirtualFile,
    private val push: (designJson: String) -> Unit,
) : Disposable {
    private val log = thisLogger()
    private val queue = MergingUpdateQueue("composer-design-sync", 500, true, null, this)

    @Volatile
    private var lastPushed: String? = null

    @Volatile
    private var suppressDocEvents = false

    fun start() {
        FileDocumentManager.getInstance().getDocument(file)?.addDocumentListener(
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    if (!suppressDocEvents) schedule()
                }
            },
            this,
        )
        schedule()
    }

    fun schedule() {
        queue.queue(Update.create("parse") { parseAndPush() })
    }

    /** Designer → code. Runs on the EDT (bridge messages arrive via invokeLater). */
    fun applyDesignerEdit(designJson: String) {
        if (project.isDisposed || !file.isValid) return
        if (designJson == lastPushed) return // echo of our own push
        val edited = runCatching { DesignJson.decode(designJson) }.getOrNull() as? Node.Artboard ?: return
        val doc = FileDocumentManager.getInstance().getDocument(file) ?: return
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val ktFile = PsiManager.getInstance(project).findFile(file) as? KtFile ?: return
        val previous = ReadAction.compute<ParsedDesign?, RuntimeException> { DesignParser.parse(ktFile) }
            ?: emptyPrevious(ktFile)
        val plan = WriteBackPlanner.plan(doc.text, previous, edited)
        if (plan.edits.isEmpty()) return // geometry-only change or true no-op
        suppressDocEvents = true
        try {
            WriteCommandAction.runWriteCommandAction(project, "Edit Design", "composer.design", {
                for (e in plan.edits.sortedByDescending { it.start }) {
                    doc.replaceString(e.start, e.end, e.replacement)
                }
            }, ktFile)
            PsiDocumentManager.getInstance(project).commitDocument(doc)
        } finally {
            suppressDocEvents = false
        }
        // Re-push the canonical (re-parsed) design so ids stay in sync; skipped
        // when it decodes back to what the designer already showed.
        parseAndPush()
        log.info("Composer write-back applied ${plan.edits.size} edit(s) to ${file.name}")
    }

    /**
     * Entered on the EDT (MergingUpdateQueue default): documents commit there,
     * then the parse itself runs as a non-blocking read action off the EDT
     * (SlowOperations hygiene — PSI walks of big files don't belong on the UI
     * thread) and the push hops back to the EDT.
     */
    private fun parseAndPush() {
        if (project.isDisposed || !file.isValid) return
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        ReadAction.nonBlocking<String> {
            val ktFile = PsiManager.getInstance(project).findFile(file) as? KtFile
            val artboard = ktFile?.let { DesignParser.parse(it)?.artboard }
                ?: Node.Artboard(id = "artboard") // no parseable @Composable → empty canvas
            DesignJson.encode(artboard)
        }
            .expireWith(this)
            .coalesceBy(this)
            .finishOnUiThread(ModalityState.defaultModalityState()) { json ->
                if (json != lastPushed) {
                    lastPushed = json
                    push(json)
                }
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun emptyPrevious(ktFile: KtFile): ParsedDesign = ParsedDesign(
        artboard = Node.Artboard(id = "artboard"),
        functions = emptyList(),
        existingImports = ktFile.importDirectives.mapNotNull { it.importPath?.pathStr },
        importInsertOffset = ktFile.importDirectives.lastOrNull()?.textRange?.endOffset
            ?: ktFile.packageDirective?.textRange?.endOffset ?: 0,
        topLevelFunctionNames = ktFile.declarations
            .filterIsInstance<org.jetbrains.kotlin.psi.KtNamedFunction>()
            .mapNotNull { it.name },
    )

    override fun dispose() {}
}
