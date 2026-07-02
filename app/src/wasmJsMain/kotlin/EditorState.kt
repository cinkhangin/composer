package composer

import androidx.compose.runtime.getValue
import kotlin.math.abs
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import composer.model.ModifierSpec
import composer.model.Node
import composer.model.childNodes
import composer.model.cloneWithNewIds
import composer.model.contentChildren
import composer.model.dedupeIds
import composer.model.DesignTheme
import composer.model.IconKind
import composer.model.findById
import composer.model.indexInParent
import composer.model.insertChild
import composer.model.insertOrdered
import composer.model.isContainer
import composer.model.migrateToArtboard
import composer.model.NamedTheme
import composer.model.moveAfter
import composer.model.moveBefore
import composer.model.moveById
import composer.model.moveInto
import composer.model.parentOf
import composer.model.removeById
import composer.model.replaceById
import composer.model.toColumn
import composer.model.toRow
import composer.model.withModifier

/**
 * Hoisted editor state: the design tree, current selection, and undo/redo
 * history. The tree is immutable; every edit replaces it, so the canvas,
 * inspector, and code panel — all projections of [root] — update automatically.
 */
class EditorState(initial: Node) {
    // Old saves have a single Frame root; migrate lifts it into an Artboard,
    // seeding the screen size from the legacy global frame-size preference.
    var root by mutableStateOf(initial.migrated().dedupeIds())
        private set

    var selectedId by mutableStateOf<String?>(null)
        private set

    var clipboard by mutableStateOf<Node?>(null)
        private set

    /** When true, the center pane shows the generated code instead of the canvas. */
    var showCode by mutableStateOf(false)
        private set

    fun setCodeView(value: Boolean) {
        showCode = value
    }

    /** The design root — always an [Node.Artboard] (see the migration in the constructor). */
    val artboard: Node.Artboard get() = root as Node.Artboard

    /** The artboard's screens, in order. */
    val composables: List<Node.Composable> get() = artboard.composables.filterIsInstance<Node.Composable>()

    /** The screen containing [id] (or the screen itself), null for the artboard/absent ids. */
    fun screenOf(id: String): Node.Composable? {
        val topLevel = pathFromRoot(id).getOrNull(1) ?: return null
        return root.findById(topLevel) as? Node.Composable
    }

    // Per-screen canvas geometry (dp). Coalesced so a whole drag is one undo step.

    /** Translate screen [id] by (dx, dy) dp on the artboard canvas. */
    fun moveComposable(id: String, dx: Int, dy: Int) {
        update(id, coalesceKey = "cmove:$id") { node ->
            if (node is Node.Composable) node.copy(x = node.x + dx, y = node.y + dy) else node
        }
    }

    // --- artboard snap (screen drags) -----------------------------------

    /** Active snap guides (artboard dp) while a screen drag is snapping — drawn by the canvas. */
    var snapGuideV by mutableStateOf<Int?>(null)
        private set
    var snapGuideH by mutableStateOf<Int?>(null)
        private set

    // The drag's UN-snapped position: snapping is computed against where the
    // cursor actually is, so the screen can escape a snap by dragging past it.
    private var dragDesiredX: Int? = null
    private var dragDesiredY: Int? = null

    /** Body-drag move with Figma-style snapping to other screens' edges/centers. */
    fun moveComposableSnapped(id: String, dx: Int, dy: Int, threshold: Int) {
        val screen = root.findById(id) as? Node.Composable ?: return
        val desX = (dragDesiredX ?: screen.x) + dx
        val desY = (dragDesiredY ?: screen.y) + dy
        dragDesiredX = desX
        dragDesiredY = desY
        val snap = snapScreenPosition(desX, desY, screen.width, screen.height, composables.filter { it.id != id }, threshold)
        snapGuideV = snap.guideV
        snapGuideH = snap.guideH
        setComposablePos(id, snap.x, snap.y)
    }

    /** Drag finished — clear the snap session and its guides. */
    fun endScreenDrag() {
        dragDesiredX = null
        dragDesiredY = null
        snapGuideV = null
        snapGuideH = null
    }

    /** Set screen [id]'s canvas position (dp). */
    fun setComposablePos(id: String, x: Int, y: Int) {
        update(id, coalesceKey = "cmove:$id") { node ->
            if (node is Node.Composable) node.copy(x = x, y = y) else node
        }
    }

    /** Resize screen [id] by (dw, dh) dp. Min 1dp — a Composable can be as small as 1×1. */
    fun resizeComposable(id: String, dw: Int, dh: Int) {
        update(id, coalesceKey = "csize:$id") { node ->
            if (node is Node.Composable) node.copy(
                width = (node.width + dw).coerceIn(1, 3840),
                height = (node.height + dh).coerceIn(1, 3840),
            ) else node
        }
    }

    /** Set screen [id]'s size (dp) — presets and the inspector W/H fields. */
    fun setComposableSize(id: String, width: Int, height: Int) {
        update(id, coalesceKey = "csize:$id") { node ->
            if (node is Node.Composable) node.copy(
                width = width.coerceIn(1, 3840),
                height = height.coerceIn(1, 3840),
            ) else node
        }
    }

    /**
     * Add a new screen to the artboard, placed to the right of the rightmost
     * existing screen (Figma-style side-by-side flow). Named "Screen N" so the
     * generated function name is stable and readable. Selects it.
     */
    fun addComposable(width: Int = 390, height: Int = 844) {
        val id = nextId()
        val screen = Node.Composable(id = id, x = nextScreenX(), y = 0, width = width, height = height)
        val name = "Screen ${composables.size + 1}"
        commit(
            root.insertChild(root.id, screen, Int.MAX_VALUE)
                .let { (it as Node.Artboard).copy(layerNames = it.layerNames + (id to name)) },
        )
        select(id)
    }

    private fun nextScreenX(): Int =
        composables.maxOfOrNull { it.x + it.width + SCREEN_GAP } ?: 0

    // --- Themes: a named list on the artboard; the ACTIVE one drives the preview
    // --- and the generated AppTheme default. Post-migration the list is never empty.

    /** The design's named themes. */
    val themes: List<NamedTheme> get() = (root as? Node.Artboard)?.themes ?: emptyList()

    /** Index of the active theme. */
    val activeTheme: Int get() = (root as? Node.Artboard)?.activeTheme ?: 0

    /** The ACTIVE Material theme — what the preview renders. */
    val theme: DesignTheme get() = (root as? Node.Artboard)?.currentTheme() ?: DesignTheme()

    /** Replace the ACTIVE theme's colors (undoable; [coalesceKey] merges rapid edits, e.g. dragging a color picker). */
    fun setTheme(theme: DesignTheme, coalesceKey: String? = "theme") {
        update(root.id, coalesceKey) { node ->
            if (node !is Node.Artboard) return@update node
            val i = node.activeTheme.coerceIn(0, (node.themes.size - 1).coerceAtLeast(0))
            if (node.themes.isEmpty()) node.copy(themes = listOf(NamedTheme("Light", theme)))
            else node.copy(themes = node.themes.toMutableList().apply { this[i] = this[i].copy(theme = theme) })
        }
    }

    /** Switch the active theme (undoable — it's a design property, not an editor preference). */
    fun setActiveTheme(index: Int) {
        update(root.id) { node ->
            if (node is Node.Artboard) node.copy(activeTheme = index.coerceIn(0, (node.themes.size - 1).coerceAtLeast(0))) else node
        }
    }

    /** Add a new theme (a copy of the active one, so it's a good starting point) and select it. */
    fun addTheme() {
        update(root.id) { node ->
            if (node !is Node.Artboard) return@update node
            val base = node.currentTheme()
            val name = uniqueThemeName(node.themes, "Theme ${node.themes.size + 1}")
            node.copy(themes = node.themes + NamedTheme(name, base), activeTheme = node.themes.size)
        }
    }

    /** Rename theme [index]. Coalesced so typing is one undo step. */
    fun renameTheme(index: Int, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        update(root.id, coalesceKey = "themename:$index") { node ->
            if (node !is Node.Artboard || index !in node.themes.indices) return@update node
            node.copy(themes = node.themes.toMutableList().apply { this[index] = this[index].copy(name = trimmed) })
        }
    }

    /** Delete theme [index]; the last theme can't be deleted. */
    fun deleteTheme(index: Int) {
        update(root.id) { node ->
            if (node !is Node.Artboard || node.themes.size <= 1 || index !in node.themes.indices) return@update node
            val next = node.themes.toMutableList().apply { removeAt(index) }
            node.copy(themes = next, activeTheme = node.activeTheme.coerceIn(0, next.size - 1))
        }
    }

    private fun uniqueThemeName(themes: List<NamedTheme>, base: String): String {
        if (themes.none { it.name == base }) return base
        var n = 2
        while (themes.any { it.name == "$base $n" }) n++
        return "$base $n"
    }

    private val undoStack = mutableStateListOf<Node>()
    private val redoStack = mutableStateListOf<Node>()

    // Consecutive edits sharing a coalesce key collapse into one undo step
    // (e.g. typing in a text field is one undo, not one-per-keystroke).
    private var lastCommitKey: String? = null

    private var idCounter = maxGeneratedId(root)

    val selected: Node?
        get() = selectedId?.let { root.findById(it) }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val hasSelection: Boolean get() = selectedId != null
    val canPaste: Boolean get() = clipboard != null

    fun select(id: String) {
        selectedId = id
        lastCommitKey = null
    }

    /** Ids from the root down to [id], inclusive: `[root, …, id]`. */
    private fun pathFromRoot(id: String): List<String> {
        val path = mutableListOf<String>()
        var cur: String? = id
        while (cur != null) {
            path.add(0, cur)
            cur = root.parentOf(cur)?.id
        }
        return path
    }

    /**
     * Figma-style click selection. A single tap selects the **top-level** component
     * (direct child of the root frame) under the cursor; a double tap **drills one
     * level deeper** toward the clicked node from the current selection.
     */
    fun selectAt(deepestId: String, deep: Boolean) {
        val path = pathFromRoot(deepestId)
        val topLevel = path.getOrNull(1) ?: deepestId // path[0] is the root frame
        if (!deep) {
            select(topLevel)
            return
        }
        val idx = path.indexOf(selectedId)
        val next = when {
            idx < 0 -> deepestId                  // not drilling yet → select the clicked child directly
            idx < path.lastIndex -> path[idx + 1] // already an ancestor is selected → one level deeper
            else -> path.last()                   // already at the deepest
        }
        select(next)
    }

    fun clearSelection() {
        selectedId = null
        lastCommitKey = null
    }

    /** Edit the node with [id]. Pass a [coalesceKey] to merge consecutive edits into one undo step. */
    fun update(id: String, coalesceKey: String? = null, transform: (Node) -> Node) {
        commit(root.replaceById(id, transform), coalesceKey)
    }

    /**
     * Insert a new node (built by [factory] with a fresh id) relative to the
     * current selection: into the selected container, else as a sibling after
     * the selected leaf, else into the root.
     */
    fun insert(factory: (id: String) -> Node) {
        insertNode(factory(nextId()))
    }

    /** Copy the selected node to the clipboard (cloned on paste, so it's reusable). The artboard and slots can't be copied. */
    fun copy() {
        selected?.takeUnless { it is Node.Artboard || it is Node.Slot }?.let { clipboard = it }
    }

    /** Paste a fresh-id clone of the clipboard, relative to the current selection. */
    fun paste() {
        val template = clipboard ?: return
        insertNode(template.cloneWithNewIds(::nextId))
    }

    /** Duplicate the selected node in place (fresh ids), relative to the selection. */
    fun duplicate() {
        val sel = selected ?: return
        insertNode(sel.cloneWithNewIds(::nextId))
    }

    private fun insertNode(child: Node) {
        // A whole screen (paste/duplicate of a Composable) always lands on the
        // artboard, shifted right so it doesn't stack on its source.
        if (child is Node.Composable) {
            commit(root.insertChild(root.id, child.copy(x = nextScreenX(), y = 0), Int.MAX_VALUE))
            select(child.id)
            return
        }
        var tree = root
        var (parentId, index) = resolveInsertTarget()
        if (parentId == null) {
            // No screen to insert into yet — create one, then insert into it.
            val screen = Node.Composable(id = nextId(), x = nextScreenX(), y = 0)
            tree = tree.insertChild(tree.id, screen, Int.MAX_VALUE)
            parentId = screen.id
            index = Int.MAX_VALUE
        }
        commit(tree.insertChild(parentId, child, index))
        select(child.id)
    }

    fun delete(id: String) {
        if (id == root.id) return
        if (root.findById(id) is Node.Slot) return // slots are permanent; delete their children instead
        val parentId = root.parentOf(id)?.id
        commit(root.removeById(id))
        selectedId = parentId
        lastCommitKey = null
    }

    /** Move [id] among its siblings: delta -1 (up/before) or +1 (down/after). */
    fun move(id: String, delta: Int) {
        commit(root.moveById(id, delta))
    }

    // On-canvas direct manipulation. Coalesced so a whole drag is one undo step.

    /** Set node [id]'s absolute position (x, y) dp — adds/updates an Offset modifier. */
    fun setNodeOffset(id: String, x: Int, y: Int) {
        update(id, coalesceKey = "offset:$id") { node ->
            val mods = node.modifier
            val i = mods.indexOfFirst { it is ModifierSpec.Offset }
            val next = if (i >= 0) {
                mods.toMutableList().apply { this[i] = ModifierSpec.Offset(x, y) }
            } else {
                mods + ModifierSpec.Offset(x, y)
            }
            node.withModifier(next)
        }
    }

    /** Translate node [id] by (dx, dy) dp — adds/updates an Offset modifier. */
    fun offsetNode(id: String, dx: Int, dy: Int) {
        update(id, coalesceKey = "offset:$id") { node ->
            val mods = node.modifier
            val i = mods.indexOfFirst { it is ModifierSpec.Offset }
            val next = if (i >= 0) {
                val cur = mods[i] as ModifierSpec.Offset
                mods.toMutableList().apply { this[i] = ModifierSpec.Offset(cur.x + dx, cur.y + dy) }
            } else {
                mods + ModifierSpec.Offset(dx, dy)
            }
            node.withModifier(next)
        }
    }

    /**
     * Resize node [id] by (dw, dh) dp — adds/updates a Size modifier, seeding from
     * [baseW]×[baseH]. Min 1dp (Figma allows 1×1 boxes; a 0/negative size would
     * make the node unrenderable and unselectable on canvas).
     */
    fun resizeNode(id: String, baseW: Int, baseH: Int, dw: Int, dh: Int) {
        update(id, coalesceKey = "size:$id") { node ->
            val mods = node.modifier
            val i = mods.indexOfFirst { it is ModifierSpec.Size }
            val next = if (i >= 0) {
                val cur = mods[i] as ModifierSpec.Size
                mods.toMutableList().apply { this[i] = ModifierSpec.Size((cur.width + dw).coerceAtLeast(1), (cur.height + dh).coerceAtLeast(1)) }
            } else {
                mods + ModifierSpec.Size((baseW + dw).coerceAtLeast(1), (baseH + dh).coerceAtLeast(1))
            }
            node.withModifier(next)
        }
    }

    // Drag & drop moves from the tree view. Each is undoable and re-selects the node.
    fun dropBefore(nodeId: String, targetId: String) = applyMove(root.moveBefore(nodeId, targetId), nodeId)
    fun dropAfter(nodeId: String, targetId: String) = applyMove(root.moveAfter(nodeId, targetId), nodeId)
    fun dropInto(nodeId: String, containerId: String) = applyMove(root.moveInto(nodeId, containerId), nodeId)

    private fun applyMove(newRoot: Node, nodeId: String) {
        if (newRoot == root) return
        commit(newRoot)
        selectedId = nodeId
        lastCommitKey = null
    }

    /** Replace the entire tree (e.g. after loading from storage/file). Undoable. */
    fun load(tree: Node) {
        commit(tree.migrated().dedupeIds())
        idCounter = maxOf(idCounter, maxGeneratedId(root))
        selectedId = null
        lastCommitKey = null
    }

    /** Reset to a fresh "Hello world" design. Undoable. */
    fun newFile() {
        commit(emptyDesign.migrated().dedupeIds())
        idCounter = maxOf(idCounter, maxGeneratedId(root))
        selectedId = null
        lastCommitKey = null
    }

    fun canMove(id: String?, delta: Int): Boolean {
        if (id == null) return false
        val idx = root.indexInParent(id)
        if (idx < 0) return false
        val siblings = root.parentOf(id)?.let { p -> countChildren(p) } ?: return false
        val target = idx + delta
        return target in 0 until siblings
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(root)
        root = undoStack.removeAt(undoStack.lastIndex)
        lastCommitKey = null
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(root)
        root = redoStack.removeAt(redoStack.lastIndex)
        lastCommitKey = null
    }

    private fun commit(newRoot: Node, coalesceKey: String? = null) {
        if (newRoot == root) return
        if (coalesceKey == null || coalesceKey != lastCommitKey) {
            undoStack.add(root)
            redoStack.clear()
        }
        lastCommitKey = coalesceKey
        root = newRoot
    }

    /**
     * Where a new component goes: the selected container, after the selected leaf,
     * or the first screen. A null parentId means "no screen exists yet — create one"
     * (components can never sit directly under the artboard).
     */
    private fun resolveInsertTarget(): Pair<String?, Int> {
        val sel = selectedId?.let { root.findById(it) }
        return when {
            sel == null || sel is Node.Artboard ->
                (composables.firstOrNull()?.id) to Int.MAX_VALUE
            sel.isContainer() -> sel.id to Int.MAX_VALUE // append into the selected container
            else -> {
                val parentId = root.parentOf(sel.id)?.id ?: composables.firstOrNull()?.id
                parentId to (root.indexInParent(sel.id) + 1) // after the selected leaf
            }
        }
    }

    private fun countChildren(node: Node): Int = node.contentChildren().size

    // Scaffold slots (topBar/bottomBar/fab) are permanent Node.Slot containers in
    // the Layers tree — add/remove their CHILDREN like any container; the slot
    // itself is created with the Scaffold and can't be toggled or deleted.

    /** The custom layer name for [id], or null if it uses its default type name. */
    fun layerName(id: String): String? = (root as? Node.Artboard)?.layerNames?.get(id)

    /** Rename a layer (stored on the Artboard). Blank clears the custom name. Undoable, coalesced. */
    fun renameLayer(id: String, name: String) {
        val ab = root as? Node.Artboard ?: return
        val trimmed = name.trim()
        val newMap = if (trimmed.isEmpty()) ab.layerNames - id else ab.layerNames + (id to trimmed)
        if (newMap == ab.layerNames) return
        update(ab.id, coalesceKey = "rename:$id") { (it as Node.Artboard).copy(layerNames = newMap) }
    }

    /** Flip an auto-layout container between vertical ([Node.Column]) and horizontal ([Node.Row]). */
    fun setContainerDirection(id: String, horizontal: Boolean) {
        update(id) { node ->
            when (node) {
                is Node.Column -> if (horizontal) node.toRow() else node
                is Node.Row -> if (horizontal) node else node.toColumn()
                else -> node
            }
        }
    }

    /** Convert a plain [Node.Box] into an auto-layout [Node.Column] ("Use auto layout"). */
    fun boxToAutoLayout(id: String) {
        update(id) { node -> if (node is Node.Box) node.toColumn() else node }
    }

    /** Upsert the container's [ModifierSpec.Padding] (Figma frame padding). Coalesced per node. */
    fun setContainerPadding(id: String, padding: ModifierSpec.Padding) {
        update(id, coalesceKey = "pad:$id") { node ->
            val mods = node.modifier
            val i = mods.indexOfFirst { it is ModifierSpec.Padding }
            val next = if (i >= 0) mods.toMutableList().apply { this[i] = padding } else mods + padding
            node.withModifier(next)
        }
    }

    /**
     * Insert, replace, or (when [spec] is null) remove the single [T] modifier on node [id].
     * NEW modifiers are inserted at their canonical appearance position (see
     * [insertOrdered]) — e.g. a Fill lands BEFORE any Padding so it paints the whole
     * box instead of reading as a margin. Replacements keep their position.
     */
    private inline fun <reified T : ModifierSpec> upsertModifier(id: String, coalesceKey: String?, spec: T?) {
        update(id, coalesceKey) { node ->
            val mods = node.modifier
            val i = mods.indexOfFirst { it is T }
            val next = when {
                spec == null && i >= 0 -> mods.toMutableList().apply { removeAt(i) }
                spec == null -> mods
                i >= 0 -> mods.toMutableList().apply { this[i] = spec }
                else -> mods.insertOrdered(spec)
            }
            node.withModifier(next)
        }
    }

    /** Set (or clear, when null) the fill color — the node's [ModifierSpec.Background], keeping its corner. */
    fun setFill(id: String, color: Long?) {
        val node = root.findById(id) ?: return
        val corner = (node.modifier.firstOrNull { it is ModifierSpec.Background } as? ModifierSpec.Background)?.corner ?: 0
        upsertModifier(id, "fill:$id", color?.let { ModifierSpec.Background(it, corner) })
    }

    /** Set the corner radius (0 = square) via a [ModifierSpec.Clip]. */
    fun setCornerRadius(id: String, corner: Int) =
        upsertModifier(id, "corner:$id", if (corner > 0) ModifierSpec.Clip(corner) else null)

    /** Set opacity 0f–1f via [ModifierSpec.Alpha] (1f = fully opaque removes the modifier). */
    fun setOpacity(id: String, alpha: Float) =
        upsertModifier(id, "alpha:$id", if (alpha < 1f) ModifierSpec.Alpha(alpha.coerceIn(0f, 1f)) else null)

    /** Set (or clear, when null) the stroke via [ModifierSpec.Border]. */
    fun setStroke(id: String, border: ModifierSpec.Border?) =
        upsertModifier(id, "stroke:$id", border)

    /** Add/clear the navigation icon on a [Node.TopAppBar]. */
    fun setTopBarNavIcon(id: String, present: Boolean) {
        update(id) { node ->
            val t = node as? Node.TopAppBar ?: return@update node
            t.copy(navigationIcon = if (present) t.navigationIcon ?: Node.IconButton(nextId()) else null)
        }
    }

    /** A fresh id, guaranteed not to collide with any node currently in the tree. */
    private fun nextId(): String {
        var id: String
        do { id = "n${++idCounter}" } while (root.findById(id) != null)
        return id
    }
}

/** Horizontal gap between side-by-side screens on the artboard canvas (dp). */
private const val SCREEN_GAP = 60

/**
 * Migrate any historical root shape to the Artboard model, seeding a migrated
 * screen's size from the legacy global frame-size preference (pre-Artboard
 * designs kept the frame size outside the model, in localStorage).
 */
private fun Node.migrated(): Node.Artboard =
    migrateToArtboard(loadFrameSize()?.first ?: 1024, loadFrameSize()?.second ?: 680)

/** Highest numeric suffix among `n<number>` ids in the tree (so the counter resumes past them). */
private fun maxGeneratedId(node: Node): Int {
    val self = node.id.let {
        if (it.length > 1 && it[0] == 'n' && it.drop(1).all(Char::isDigit)) it.drop(1).toIntOrNull() ?: 0 else 0
    }
    val childMax = node.childNodes().maxOfOrNull { maxGeneratedId(it) } ?: 0
    return maxOf(self, childMax)
}

/** Result of [snapScreenPosition]: the snapped position + guide lines (artboard dp) if snapped. */
data class ScreenSnap(val x: Int, val y: Int, val guideV: Int?, val guideH: Int?)

/**
 * Snap a screen at desired position ([x], [y], size [w]×[h]) to the edges and
 * centers of [others]: each of the moving screen's left/center/right (and
 * top/center/bottom) anchors is tested against every other screen's matching
 * lines; the closest hit within [threshold] dp wins per axis.
 */
fun snapScreenPosition(x: Int, y: Int, w: Int, h: Int, others: List<Node.Composable>, threshold: Int): ScreenSnap {
    var bestDx: Int? = null
    var guideV: Int? = null
    var bestDy: Int? = null
    var guideH: Int? = null
    for (o in others) {
        for (t in intArrayOf(o.x, o.x + o.width / 2, o.x + o.width)) {
            for (a in intArrayOf(0, w / 2, w)) {
                val d = t - (x + a)
                if (abs(d) <= threshold && (bestDx == null || abs(d) < abs(bestDx!!))) {
                    bestDx = d
                    guideV = t
                }
            }
        }
        for (t in intArrayOf(o.y, o.y + o.height / 2, o.y + o.height)) {
            for (a in intArrayOf(0, h / 2, h)) {
                val d = t - (y + a)
                if (abs(d) <= threshold && (bestDy == null || abs(d) < abs(bestDy!!))) {
                    bestDy = d
                    guideH = t
                }
            }
        }
    }
    return ScreenSnap(
        x = x + (bestDx ?: 0),
        y = y + (bestDy ?: 0),
        guideV = if (bestDx != null) guideV else null,
        guideH = if (bestDy != null) guideH else null,
    )
}
