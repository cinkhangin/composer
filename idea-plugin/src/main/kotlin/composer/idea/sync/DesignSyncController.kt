package composer.idea.sync

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import composer.codeparse.DesignParser
import composer.codeparse.ParsedDesign
import composer.codeparse.WriteBackPlan
import composer.codeparse.WriteBackPlanner
import composer.idea.ComposerNotifications
import composer.model.DesignJson
import composer.model.Node
import org.jetbrains.concurrency.CancellablePromise
import org.jetbrains.kotlin.psi.KtFile

/**
 * Two-way sync between the Kotlin document and the embedded designer.
 *
 * Read direction: a DocumentListener + 500ms merge window re-parses (in a
 * non-blocking background read action) and pushes the design when it actually
 * changed; a parse that finds no screens keeps the designer's last good design
 * and reports it via [onScreensChanged]. Write direction ([applyDesignerEdit]):
 * decode + plan minimal edits ([WriteBackPlanner]) in a background read action,
 * then hop to the EDT for one undoable write command (unique undo group per
 * designer edit) and re-parse; a document that changed between plan and write
 * re-plans. Loop guards: [suppressDocEvents] skips our own document writes;
 * [lastPushed] drops designer echoes; zero-edit plans (e.g. dragging a screen
 * on the artboard) leave both the document and the designer untouched.
 */
class DesignSyncController(
    private val project: Project,
    private val file: com.intellij.openapi.vfs.VirtualFile,
    private val push: (designJson: String) -> Unit,
    /** EDT. False = the file parsed to no screens (designer keeps the last good design). */
    private val onScreensChanged: (hasScreens: Boolean) -> Unit = {},
) : Disposable {
    private val log = thisLogger()
    private val queue = MergingUpdateQueue("composer-design-sync", 500, true, null, this)

    @Volatile
    private var lastPushed: String? = null

    @Volatile
    private var suppressDocEvents = false

    /** Last successful parse + the document stamp it was parsed at. */
    private class CachedParse(val design: ParsedDesign, val stamp: Long)

    @Volatile
    private var cachedParse: CachedParse? = null

    /** Unique undo group per applied designer edit — distinct actions undo individually. */
    private var editSeq = 0

    private var pendingEdit: CancellablePromise<*>? = null

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

    // ---- selection sync lookups (best-effort; ranges refresh with each parse) ----

    /** Source range of [nodeId] in the last successfully parsed text, or null. */
    fun rangeOf(nodeId: String): IntRange? = cachedParse?.design?.sourceRanges?.get(nodeId)

    /** Id of the deepest parsed node whose source range contains [offset], or null. */
    fun nodeIdAt(offset: Int): String? = cachedParse?.design?.sourceRanges
        ?.filter { offset in it.value }
        ?.minByOrNull { it.value.last - it.value.first }
        ?.key

    // ---- designer → code -------------------------------------------------------

    private sealed interface EditOutcome
    private data object Drop : EditOutcome
    private data object Stale : EditOutcome
    private class PlannedEdit(val plan: WriteBackPlan, val stamp: Long, val ktFile: KtFile) : EditOutcome

    /**
     * Designer → code. Entered on the EDT (bridge messages arrive via
     * invokeLater); the decode + parse + plan run in a background read action,
     * only the final write command hops back to the EDT. A newer designer edit
     * cancels an in-flight plan; a document that changed under the plan
     * (modification-stamp mismatch) re-plans instead of writing stale offsets.
     */
    fun applyDesignerEdit(designJson: String) {
        if (project.isDisposed || !file.isValid) return
        if (designJson == lastPushed) return // echo of our own push
        val doc = FileDocumentManager.getInstance().getDocument(file) ?: return
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        pendingEdit?.cancel() // superseded by this newer edit
        pendingEdit = ReadAction.nonBlocking<EditOutcome> { planEdit(designJson, doc) }
            .expireWith(this)
            .finishOnUiThread(ModalityState.defaultModalityState()) { outcome ->
                when (outcome) {
                    Drop -> Unit
                    Stale -> applyDesignerEdit(designJson) // re-commit + re-plan
                    is PlannedEdit ->
                        if (doc.modificationStamp != outcome.stamp) {
                            applyDesignerEdit(designJson) // changed between plan and write
                        } else {
                            applyPlan(doc, outcome)
                        }
                }
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    /** Background read action: decode the designer's tree and plan the text edits. */
    private fun planEdit(designJson: String, doc: Document): EditOutcome {
        val edited = runCatching { DesignJson.decode(designJson) }.getOrNull() as? Node.Artboard
        if (edited == null) {
            ComposerNotifications.warnOnce(
                project,
                "composer.design.decode:${file.path}",
                "Composer couldn't decode a designer edit for ${file.name}; the change was dropped.",
            )
            return Drop
        }
        // The document changed after our EDT commit — PSI offsets would be stale.
        if (!PsiDocumentManager.getInstance(project).isCommitted(doc)) return Stale
        val ktFile = PsiManager.getInstance(project).findFile(file) as? KtFile ?: return Drop
        val stamp = doc.modificationStamp
        // parseAndPush just parsed this text for the designer — reuse it as `previous`.
        val cached = cachedParse?.takeIf { it.stamp == stamp }
        val previous = cached?.design ?: DesignParser.parse(ktFile) ?: emptyPrevious(ktFile)
        val plan = WriteBackPlanner.plan(doc.text, previous, edited)
        if (plan.edits.isEmpty()) return Drop // geometry-only change or true no-op
        return PlannedEdit(plan, stamp, ktFile)
    }

    /** EDT: apply the planned edits as one write command in its own undo group. */
    private fun applyPlan(doc: Document, planned: PlannedEdit) {
        suppressDocEvents = true
        try {
            WriteCommandAction.runWriteCommandAction(project, "Edit Design", "composer.design.${++editSeq}", {
                for (e in planned.plan.edits.sortedByDescending { it.start }) {
                    doc.replaceString(e.start, e.end, e.replacement)
                }
            }, planned.ktFile)
            PsiDocumentManager.getInstance(project).commitDocument(doc)
        } finally {
            suppressDocEvents = false
        }
        for (rename in planned.plan.renames) {
            ComposerNotifications.infoOnce(
                project,
                "composer.rename:${rename.from}->${rename.to}",
                "Composer renamed ${rename.from} to ${rename.to} — call sites in other files were not updated.",
            )
        }
        // Re-push the canonical (re-parsed) design so ids stay in sync; skipped
        // when it decodes back to what the designer already showed.
        parseAndPush()
        log.debug("Composer write-back applied ${planned.plan.edits.size} edit(s) to ${file.name}")
    }

    // ---- code → designer -------------------------------------------------------

    /**
     * Entered on the EDT (MergingUpdateQueue default): documents commit there,
     * then the parse itself runs as a non-blocking read action off the EDT
     * (SlowOperations hygiene — PSI walks of big files don't belong on the UI
     * thread) and the push hops back to the EDT. A failed parse (no screen-shaped
     * functions) pushes NOTHING — the designer keeps the last good design and the
     * editor shows its no-screens hint via [onScreensChanged].
     */
    private fun parseAndPush() {
        if (project.isDisposed || !file.isValid) return
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        ReadAction.nonBlocking<String?> {
            val ktFile = PsiManager.getInstance(project).findFile(file) as? KtFile
                ?: return@nonBlocking null
            val parsed = DesignParser.parse(ktFile) ?: return@nonBlocking null
            val doc = FileDocumentManager.getInstance().getDocument(file)
            if (doc != null && PsiDocumentManager.getInstance(project).isCommitted(doc)) {
                cachedParse = CachedParse(parsed, doc.modificationStamp)
            }
            DesignJson.encode(parsed.artboard)
        }
            .expireWith(this)
            .coalesceBy(this)
            .finishOnUiThread(ModalityState.defaultModalityState()) { json ->
                onScreensChanged(json != null)
                if (json == null) return@finishOnUiThread
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
