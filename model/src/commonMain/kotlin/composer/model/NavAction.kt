package composer.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a clickable component's `onClick` does in the generated app. Modeled as
 * a sealed action (not a lambda string) so codegen, the renderer, and the
 * inspector can `when` over it exhaustively, and future trigger kinds
 * (OpenUrl, ShowDialog…) extend without migration.
 *
 * [Navigate.screenId] follows the [Node.Instance.refId] contract: it names a
 * [Node.Composable] screen by node id; the FUNCTION/route name is resolved at
 * emit time via the artboard's layerNames (renames are free), and a dangling
 * id degrades to [None] at emit so output always compiles. Serialization:
 * [None] is the default, so old saves decode unchanged and unset actions are
 * omitted on encode.
 */
@Serializable
sealed interface NavAction {
    @Serializable
    @SerialName("nav.none")
    data object None : NavAction

    @Serializable
    @SerialName("nav.navigate")
    data class Navigate(val screenId: String) : NavAction

    @Serializable
    @SerialName("nav.back")
    data object Back : NavAction
}

/** The node's nav action, or null for node types that aren't clickable. */
fun Node.navAction(): NavAction? = when (this) {
    is Node.Button -> navAction
    is Node.IconButton -> navAction
    is Node.Fab -> navAction
    is Node.Chip -> navAction
    is Node.Card -> navAction
    else -> null
}

/** Copy with [action]; identity for node types that aren't clickable. */
fun Node.withNavAction(action: NavAction): Node = when (this) {
    is Node.Button -> copy(navAction = action)
    is Node.IconButton -> copy(navAction = action)
    is Node.Fab -> copy(navAction = action)
    is Node.Chip -> copy(navAction = action)
    is Node.Card -> copy(navAction = action)
    else -> this
}

/**
 * Screen ids this screen navigates to, in preorder first-use order, filtered
 * to screens that still exist on [artboard] (dangling targets are dropped —
 * the shared definition of "live target" for codegen, Canon, and the UI).
 */
fun Node.Composable.navTargets(artboard: Node.Artboard): List<String> {
    val live = artboard.composables.mapTo(mutableSetOf()) { it.id }
    val out = mutableListOf<String>()
    fun walk(n: Node) {
        val a = n.navAction()
        if (a is NavAction.Navigate && a.screenId in live && a.screenId !in out) out += a.screenId
        n.childNodes().forEach(::walk)
    }
    walk(this)
    return out
}

/** True when any node in this screen has a [NavAction.Back] action. */
fun Node.Composable.hasBackAction(): Boolean {
    fun walk(n: Node): Boolean = n.navAction() == NavAction.Back || n.childNodes().any(::walk)
    return walk(this)
}
