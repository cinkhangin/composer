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
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import composer.codeparse.ModuleDesignParser
import composer.codeparse.ModuleWriteBackPlan
import composer.codeparse.ModuleWriteBackPlanner
import composer.codeparse.ParsedModuleDesign
import composer.codeparse.SourceFile
import composer.idea.ComposerNotifications
import composer.model.DesignJson
import composer.model.Node
import org.jetbrains.concurrency.CancellablePromise

/**
 * Project-scoped whole-app designer coordinator: uses MainActivity only as the
 * module anchor, discovers every production Kotlin source file in that module,
 * aggregates its top-level composables via [ModuleDesignParser] off the EDT,
 * and pushes them to the tool-window designer.
 * Re-parses on any owned-document edit (one application-level listener,
 * filtered) and on VFS changes under the module's source roots.
 *
 * Designer edits are planned off the EDT and written as one undoable command.
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
    var lastParsed: ParsedModuleDesign? = null
        private set

    @Volatile
    private var lastPushed: String? = null

    /** Per-path modification stamps of the texts [lastParsed] was parsed from. */
    @Volatile
    private var lastParsedStamps: Map<String, Long> = emptyMap()

    private var mainActivity: VirtualFile? = null
    @Volatile
    private var sourceRoots: List<VirtualFile> = emptyList()
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
        val fileIndex = ProjectRootManager.getInstance(project).fileIndex
        val module = fileIndex.getModuleForFile(main)
        sourceRoots = module?.let { ModuleRootManager.getInstance(it).sourceRoots.toList() }
            ?.filter { it.isDirectory && !fileIndex.isInTestSourceContent(it) }
            ?.ifEmpty { null }
            ?: listOfNotNull(main.parent)
        lastPushed = null
        installListeners()
        scheduleParse()
    }

    fun stop() {
        pendingEdit?.cancel()
        pendingEdit = null
        queue.cancelAllUpdates()
        mainActivity = null
        sourceRoots = emptyList()
        lastParsed = null
        lastPushed = null
        lastParsedStamps = emptyMap()
    }

    /** The file + source range of [nodeId] in the last parse (selection sync). */
    fun sourceRangeOf(nodeId: String): Pair<VirtualFile, IntRange>? {
        val parsed = lastParsed ?: return null
        for (f in parsed.files) {
            val range = f.design.sourceRanges[nodeId] ?: continue
            val vf = LocalFileSystem.getInstance().findFileByPath(f.path) ?: continue
            return vf to range
        }
        return null
    }

    /** Deepest parsed node whose range contains [offset] in [file], or null. */
    fun nodeIdAt(file: VirtualFile, offset: Int): String? {
        val parsed = lastParsed ?: return null
        val design = parsed.files.firstOrNull { it.path == file.path }?.design ?: return null
        return design.sourceRanges
            .filter { offset in it.value }
            .minByOrNull { it.value.last - it.value.first }
            ?.key
    }

    /** True when [file] belongs to the app's owned set (advisory checks). */
    fun ownsFile(file: VirtualFile): Boolean = isOwnedCandidate(file)

    /** Re-push the last good design to a freshly booted designer. */
    fun repushForNewDesigner() {
        // A parse can finish before the ComposePanel reports ready. Replaying
        // the canonical cached payload closes that startup race; reparsing is
        // only needed when no successful parse has completed yet.
        val cached = lastPushed
        if (cached != null) pushDesign?.invoke(cached) else scheduleParse()
    }

    fun scheduleParse() {
        if (mainActivity == null) return
        queue.queue(Update.create("parse") { parseAndPush() })
    }

    private sealed interface EditOutcome
    private data object Drop : EditOutcome
    private class Planned(
        val plan: ModuleWriteBackPlan,
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
            val text = doc?.text ?: LoadTextUtil.loadText(vf).toString()
            // Every supported declaration necessarily spells Composable in its
            // annotation or import. Avoid parsing/stamping unrelated module files
            // on every keystroke while document/VFS listeners still detect when a
            // file gains its first composable.
            if ("Composable" !in text) continue
            sources += SourceFile(vf.path, text)
            stamps[vf.path] = doc?.modificationStamp ?: vf.modificationStamp
            byPath[vf.path] = vf
        }
        return Triple(sources, stamps, byPath)
    }

    private fun stampsMatch(expected: Map<String, Long>): Boolean {
        // Module discovery can cover hundreds of files. Check only the relevant
        // parsed files here—without rescanning/loading the whole module on the EDT.
        return expected.all { (path, stamp) ->
            val vf = LocalFileSystem.getInstance().findFileByPath(path) ?: return@all false
            val doc = FileDocumentManager.getInstance().getCachedDocument(vf)
            (doc?.modificationStamp ?: vf.modificationStamp) == stamp
        }
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
            ?: ModuleDesignParser.parse(sources)
            ?: return Drop
        val plan = ModuleWriteBackPlanner.plan(previous, sources.associate { it.path to it.text }, edited)
        if (plan.files.isEmpty() && plan.warnings.isEmpty() && plan.blockedReason == null) return Drop // geometry-only or true no-op
        return Planned(plan, stamps)
    }

    private fun applyPlan(plan: ModuleWriteBackPlan) {
        plan.blockedReason?.let { reason ->
            ComposerNotifications.warnOnce(
                project,
                "composer.app.blocked:${reason.hashCode()}",
                reason,
            )
            repushForNewDesigner()
            return
        }
        val byPath = plan.files.mapNotNull { filePlan ->
            LocalFileSystem.getInstance().findFileByPath(filePlan.path)?.let { filePlan.path to it }
        }.toMap()
        val editedFiles = plan.files.mapNotNull { byPath[it.path] }.distinct()
        val psiFiles = editedFiles.mapNotNull { PsiManager.getInstance(project).findFile(it) }
        val writePlans = plan.files.filter { it.edits.isNotEmpty() }
        val hasWrites = writePlans.isNotEmpty()
        val multiFile = writePlans.size > 1
        if (hasWrites) {
            suppressDocEvents = true
            try {
                WriteCommandAction.runWriteCommandAction(
                    project,
                    "Edit App Design",
                    "composer.app.${++editSeq}",
                    {
                        if (multiFile) CommandProcessor.getInstance().markCurrentCommandAsGlobal(project)
                        for (filePlan in plan.files) {
                            val vf = byPath[filePlan.path] ?: continue
                            val doc = FileDocumentManager.getInstance().getDocument(vf) ?: continue
                            for (e in filePlan.edits.sortedByDescending { it.start }) {
                                doc.replaceString(e.start, e.end, e.replacement)
                            }
                        }
                    },
                    *psiFiles.toTypedArray(),
                )
            } finally {
                suppressDocEvents = false
            }
        }
        for (w in plan.warnings) {
            ComposerNotifications.infoOnce(project, "composer.app.warn:${w.hashCode()}", w)
        }
        // Canonical re-push keeps designer ids in sync. If preservation rules
        // rejected every write, force the old source design back into the UI.
        if (hasWrites) scheduleParse() else repushForNewDesigner()
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
                    val prefixes = sourceRoots.map { "${it.path}/" }
                    if (events.any { event ->
                            event.path.endsWith(".kt") && prefixes.any(event.path::startsWith)
                        }
                    ) scheduleParse()
                }
            },
        )
    }

    private fun isOwnedCandidate(file: VirtualFile): Boolean {
        if (file.extension != "kt") return false
        return sourceRoots.any { VfsUtilCore.isAncestor(it, file, false) }
    }

    /** Every production Kotlin source file in the anchored Android module. */
    private fun candidateFiles(): List<VirtualFile> {
        val files = linkedMapOf<String, VirtualFile>()
        fun collect(file: VirtualFile) {
            if (file.isDirectory) {
                file.children.forEach(::collect)
            } else if (file.extension == "kt") {
                files[file.path] = file
            }
        }
        sourceRoots.forEach(::collect)
        return files.values.sortedBy { it.path }
    }

    private fun parseAndPush() {
        if (project.isDisposed || mainActivity?.isValid != true) return
        ReadAction.nonBlocking<Triple<String, ParsedModuleDesign, Map<String, Long>>?> {
            val (sources, stamps, _) = snapshot()
            if (sources.isEmpty()) return@nonBlocking null
            val parsed = ModuleDesignParser.parse(sources) ?: return@nonBlocking null
            // Project identity belongs to the plugin host. Store its display
            // name on the otherwise unnamed design root so shared Layers and
            // Inspector UI can show the application name without affecting the
            // standalone website or the generated source.
            val namedArtboard = parsed.artboard.copy(
                layerNames = parsed.artboard.layerNames + (parsed.artboard.id to project.name),
            )
            val named = parsed.copy(artboard = namedArtboard)
            Triple(DesignJson.encode(namedArtboard), named, stamps)
        }
            .expireWith(this)
            .coalesceBy(this)
            .finishOnUiThread(ModalityState.defaultModalityState()) { result ->
                if (result == null) {
                    onStatus?.invoke("No parseable top-level @Composable functions were found in this module.")
                    return@finishOnUiThread
                }
                val (json, parsed, stamps) = result
                lastParsed = parsed
                lastParsedStamps = stamps
                log.info(
                    "Composer discovered ${parsed.artboard.composables.size} composables " +
                        "across ${parsed.files.size} module files: " +
                        parsed.artboard.composables.joinToString { screen ->
                            parsed.artboard.layerNames[screen.id] ?: screen.id
                        },
                )
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
