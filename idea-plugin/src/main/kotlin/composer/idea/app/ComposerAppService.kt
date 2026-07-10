package composer.idea.app

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import composer.codeparse.AppParser
import composer.codeparse.ParsedApp
import composer.codeparse.SourceFile
import composer.model.DesignJson

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

    private var mainActivity: VirtualFile? = null
    private var listenersInstalled = false

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

    /** P-A: the tool window is read-only; edits are dropped with a visible note. */
    fun applyDesignerEdit(@Suppress("UNUSED_PARAMETER") designJson: String) {
        onStatus?.invoke("Read-only preview — designer edits aren't written back yet.")
    }

    private fun installListeners() {
        if (listenersInstalled) return
        listenersInstalled = true
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
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
        ReadAction.nonBlocking<Pair<String, ParsedApp>?> {
            val sources = candidateFiles().mapNotNull { vf ->
                val text = FileDocumentManager.getInstance().getCachedDocument(vf)?.text
                    ?: LoadTextUtil.loadText(vf).toString()
                SourceFile(vf.path, text)
            }
            if (sources.isEmpty()) return@nonBlocking null
            val parsed = AppParser.parse(sources) ?: return@nonBlocking null
            DesignJson.encode(parsed.artboard) to parsed
        }
            .expireWith(this)
            .coalesceBy(this)
            .finishOnUiThread(ModalityState.defaultModalityState()) { result ->
                if (result == null) {
                    onStatus?.invoke("Couldn't assemble the app design — check MainActivity and the screen files.")
                    return@finishOnUiThread
                }
                val (json, parsed) = result
                lastParsed = parsed
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
