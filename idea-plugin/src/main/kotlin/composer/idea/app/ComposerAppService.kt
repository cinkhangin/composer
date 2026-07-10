package composer.idea.app

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import composer.codeparse.AppParser
import composer.codeparse.AppWriteBackPlan
import composer.codeparse.AppWriteBackPlanner
import composer.codeparse.ParsedApp
import composer.codeparse.SourceFile
import composer.idea.ComposerNotifications
import composer.model.DesignJson
import composer.model.Node
import org.jetbrains.concurrency.CancellablePromise

/**
 * Project-scoped whole-app designer coordinator: discovers the app's files
 * around the configured MainActivity, aggregates them into one design
 * ([AppParser]) off the EDT, and pushes it to the tool-window designer.
 * Re-parses on any owned-document edit (one application-level listener,
 * filtered) and on VFS changes under the app directory.
 *
 * P-A scope: read-only — designer edits are acknowledged with a status note;
 * the multi-file write path lands next.
 */
@Service(Service.Level.PROJECT)
class ComposerAppService(private val project: Project) : Disposable {
    private val log = thisLogger()
    private val queue = MergingUpdateQueue("composer-app-sync", 500, true, null, this)

    /** Set by the tool window while its designer is alive. */
    var pushDesign: ((json: String) -> Unit)? = null

    /** Status line for the tool window (null = healthy). */
    var onStatus: ((String?) -> Unit)? = null

    @Volatile
    var lastParsed: ParsedApp? = null
        private set

    @Volatile
    private var lastPushed: String? = null

    /** Per-path modification stamps of the texts [lastParsed] was parsed from. */
    @Volatile
    private var lastParsedStamps: Map<String, Long> = emptyMap()

    private var mainActivity: VirtualFile? = null
    private var listenersInstalled = false

    @Volatile
    private var suppressDocEvents = false
    private var editSeq = 0
    private var pendingEdit: CancellablePromise<*>? = null

    val started: Boolean get() = mainActivity != null

    /** Begin coordinating around [main] (idempotent per file). */
    fun start(main: VirtualFile) {
        if (mainActivity == main) return
        mainActivity = main
        lastPushed = null
        installListeners()
        scheduleParse()
    }

    fun stop() {
        mainActivity = null
        lastParsed = null
        lastPushed = null
    }

    /** Re-push the last good design to a freshly booted designer. */
    fun repushForNewDesigner() {
        lastPushed = null
        scheduleParse()
    }

    fun scheduleParse() {
        if (mainActivity == null) return
        queue.queue(Update.create("parse") { parseAndPush() })
    }

    private sealed interface EditOutcome
    private data object Drop : EditOutcome
    private class Planned(
        val plan: AppWriteBackPlan,
        val stamps: Map<String, Long>,
    ) : EditOutcome

    /**
     * Designer → code: decode the edited artboard, plan per-file edits off the
     * EDT, and apply everything as ONE write command (global undo when it spans
     * files). A document that changed between plan and write re-plans; our own
     * writes are suppressed from the listeners and followed by a canonical
     * re-push (dropped by the designer's echo guard when identical).
     */
    fun applyDesignerEdit(designJson: String) {
        if (project.isDisposed || !started) return
        if (designJson == lastPushed) return // echo of our own push
        pendingEdit?.cancel()
        pendingEdit = ReadAction.nonBlocking<EditOutcome> { planEdit(designJson) }
            .expireWith(this)
            .finishOnUiThread(ModalityState.defaultModalityState()) { outcome ->
                when (outcome) {
                    Drop -> Unit
                    is Planned ->
                        if (stampsMatch(outcome.stamps)) {
                            applyPlan(outcome.plan)
                        } else {
                            applyDesignerEdit(designJson) // changed between plan and write
                        }
                    else -> Unit
                }
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun snapshot(): Triple<List<SourceFile>, Map<String, Long>, Map<String, VirtualFile>> {
        val sources = mutableListOf<SourceFile>()
        val stamps = mutableMapOf<String, Long>()
        val byPath = mutableMapOf<String, VirtualFile>()
        for (vf in candidateFiles()) {
            val doc = FileDocumentManager.getInstance().getCachedDocument(vf)
            sources += SourceFile(vf.path, doc?.text ?: LoadTextUtil.loadText(vf).toString())
            stamps[vf.path] = doc?.modificationStamp ?: vf.modificationStamp
            byPath[vf.path] = vf
        }
        return Triple(sources, stamps, byPath)
    }

    private fun stampsMatch(expected: Map<String, Long>): Boolean {
        val (_, now, _) = snapshot()
        return expected.all { (path, stamp) -> now[path] == stamp }
    }

    private fun planEdit(designJson: String): EditOutcome {
        val edited = runCatching { DesignJson.decode(designJson) }.getOrNull() as? Node.Artboard
        if (edited == null) {
            ComposerNotifications.warnOnce(
                project,
                "composer.app.decode",
                "Composer couldn't decode an app-designer edit; the change was dropped.",
            )
            return Drop
        }
        val (sources, stamps, _) = snapshot()
        if (sources.isEmpty()) return Drop
        // Reuse the read path's parse when nothing moved underneath it.
        val previous = lastParsed?.takeIf { lastParsedStamps == stamps }
            ?: AppParser.parse(sources)
            ?: return Drop
        val plan = AppWriteBackPlanner.plan(previous, sources.associate { it.path to it.text }, edited)
        if (plan.files.isEmpty()) return Drop // geometry-only or true no-op
        return Planned(plan, stamps)
    }

    private fun applyPlan(plan: AppWriteBackPlan) {
        val (_, _, byPath) = snapshot()
        val dir = mainActivity?.parent ?: return
        val editedFiles = plan.files.filter { it.edits.isNotEmpty() }.mapNotNull { byPath[it.path] }
        val psiFiles = editedFiles.mapNotNull { PsiManager.getInstance(project).findFile(it) }
        val multiFile = plan.files.count { it.edits.isNotEmpty() || it.createText != null } > 1
        val skippedDeletions = plan.files.filter { it.delete }
        suppressDocEvents = true
        try {
            WriteCommandAction.runWriteCommandAction(
                project,
                "Edit App Design",
                "composer.app.${++editSeq}",
                {
                    if (multiFile) CommandProcessor.getInstance().markCurrentCommandAsGlobal(project)
                    for (filePlan in plan.files) {
                        when {
                            filePlan.delete -> Unit // conservative: files stay, notified below
                            filePlan.createText != null -> {
                                val name = filePlan.path.substringAfterLast('/')
                                val vf = dir.findChild(name) ?: dir.createChildData(this, name)
                                VfsUtil.saveText(vf, filePlan.createText!!)
                            }
                            else -> {
                                val vf = byPath[filePlan.path] ?: continue
                                val doc = FileDocumentManager.getInstance().getDocument(vf) ?: continue
                                for (e in filePlan.edits.sortedByDescending { it.start }) {
                                    doc.replaceString(e.start, e.end, e.replacement)
                                }
                            }
                        }
                    }
                },
                *psiFiles.toTypedArray(),
            )
        } finally {
            suppressDocEvents = false
        }
        for (w in plan.warnings) {
            ComposerNotifications.infoOnce(project, "composer.app.warn:${w.hashCode()}", w)
        }
        if (skippedDeletions.isNotEmpty()) {
            ComposerNotifications.infoOnce(
                project,
                "composer.app.deleted:${skippedDeletions.hashCode()}",
                "Screen removed from navigation — " +
                    skippedDeletions.joinToString(", ") { it.path.substringAfterLast('/') } +
                    " were left in place; delete them manually if unwanted.",
            )
        }
        for (rename in plan.renames) {
            ComposerNotifications.infoOnce(
                project,
                "composer.app.rename:${rename.from}->${rename.to}",
                "Composer renamed ${rename.from} to ${rename.to} — call sites in hand-written code were not updated.",
            )
        }
        // Canonical re-push keeps designer ids in sync; identical pushes are dropped.
        scheduleParse()
    }

    private fun installListeners() {
        if (listenersInstalled) return
        listenersInstalled = true
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    if (suppressDocEvents) return
                    val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
                    if (isOwnedCandidate(file)) scheduleParse()
                }
            },
            this,
        )
        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (suppressDocEvents) return
                    val dir = mainActivity?.parent?.path ?: return
                    if (events.any { it.path.startsWith(dir) && it.path.endsWith(".kt") }) scheduleParse()
                }
            },
        )
    }

    private fun isOwnedCandidate(file: VirtualFile): Boolean {
        val dir = mainActivity?.parent ?: return false
        if (file.parent != dir || file.extension != "kt") return false
        val name = file.nameWithoutExtension
        return name == "MainActivity" || name.endsWith("ScreenUI") ||
            name.endsWith("Screen") || name.endsWith("ViewModel")
    }

    /** The candidate file set: MainActivity + triplet-named .kt files beside it. */
    private fun candidateFiles(): List<VirtualFile> {
        val main = mainActivity ?: return emptyList()
        val dir = main.parent ?: return emptyList()
        return dir.children.filter { it == main || (!it.isDirectory && isOwnedCandidate(it)) }
    }

    private fun parseAndPush() {
        if (project.isDisposed || mainActivity?.isValid != true) return
        ReadAction.nonBlocking<Triple<String, ParsedApp, Map<String, Long>>?> {
            val (sources, stamps, _) = snapshot()
            if (sources.isEmpty()) return@nonBlocking null
            val parsed = AppParser.parse(sources) ?: return@nonBlocking null
            Triple(DesignJson.encode(parsed.artboard), parsed, stamps)
        }
            .expireWith(this)
            .coalesceBy(this)
            .finishOnUiThread(ModalityState.defaultModalityState()) { result ->
                if (result == null) {
                    onStatus?.invoke("Couldn't assemble the app design — check MainActivity and the screen files.")
                    return@finishOnUiThread
                }
                val (json, parsed, stamps) = result
                lastParsed = parsed
                lastParsedStamps = stamps
                onStatus?.invoke(parsed.warnings.firstOrNull())
                if (json != lastPushed) {
                    lastPushed = json
                    pushDesign?.invoke(json)
                }
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    override fun dispose() {}

    companion object {
        fun getInstance(project: Project): ComposerAppService = project.service()
    }
}
