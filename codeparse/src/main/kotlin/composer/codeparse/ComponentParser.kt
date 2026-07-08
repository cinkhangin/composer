package composer.codeparse

import composer.model.BoxAlignment
import composer.model.ButtonVariant
import composer.model.HAlignment
import composer.model.HArrangement
import composer.model.IconKind
import composer.model.ModifierSpec
import composer.model.Node
import composer.model.PaddingMode
import composer.model.TextAlignment
import composer.model.TextFontFamily
import composer.model.TextWeight
import composer.model.TopAppBarVariant
import composer.model.VAlignment
import composer.model.VArrangement
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtProperty
import kotlin.math.roundToInt

/**
 * Statement-level parsing: the exact inverse of CodeGen's per-component emission.
 * Anything outside the recognized grammar becomes a [Node.RawCode] carrying the
 * statement's verbatim (dedented) source — child-level isolation, so one opaque
 * statement never poisons its recognized siblings.
 */
internal class ParseCtx(
    /** Simple names disabled file-wide by a conflicting explicit import. */
    val blockedNames: Set<String>,
    /** Top-level `@Composable` function name → its screen id (for instances). */
    val screenIdsByName: Map<String, String>,
) {
    private var n = 0
    fun newId(): String = "p${++n}"

    /** Screen ids referenced by parsed instances → Artboard.componentIds. */
    val referencedScreenIds = LinkedHashSet<String>()

    /** Best-effort node id → source range (parse-time offsets, end exclusive). */
    val sourceRanges = mutableMapOf<String, IntRange>()

    fun record(id: String, element: PsiElement) {
        sourceRanges[id] = element.textRange.startOffset until element.textRange.endOffset
    }
}

// ---- block parsing ---------------------------------------------------------

/** Parse a block's statements into nodes ([scopeParam] = Scaffold content lambda param). */
internal fun parseBlock(block: KtBlockExpression, ctx: ParseCtx, scopeParam: String? = null): List<Node> {
    val out = mutableListOf<Node>()
    var pending: PendingState? = null

    fun flushPending() {
        pending?.let { out += rawCodeNode(ctx, it.first, it.stmt) }
        pending = null
    }

    for (stmt in block.statements) {
        val comments = attachedComments(stmt)
        val trailing = trailingComment(stmt)
        if (trailing != null) {
            // A same-line trailing comment can't ride on a model node — preserve
            // the whole line (plus attached comments) verbatim.
            flushPending()
            out += rawCodeNode(ctx, comments.firstOrNull() ?: stmt, trailing)
            continue
        }
        if (comments.isEmpty()) {
            matchStateDecl(stmt)?.let { state ->
                flushPending()
                pending = state
                continue
            }
        }
        val parsed = parseComponent(stmt, comments, pending, ctx, scopeParam)
        if (parsed == null) {
            flushPending()
            out += rawCodeNode(ctx, comments.firstOrNull() ?: stmt, stmt)
        } else if (parsed.usedPending && stateUsedElsewhere(block, pending!!, stmt)) {
            // Swallowing renames the var to stateN on regeneration — unsafe when
            // any OTHER statement references it. Preserve decl + consumer verbatim.
            flushPending()
            out += rawCodeNode(ctx, stmt, stmt)
        } else {
            if (parsed.usedPending) {
                // The swallowed state decl regenerates with its consumer — one range.
                ctx.sourceRanges[parsed.node.id] =
                    pending!!.first.textRange.startOffset until stmt.textRange.endOffset
                pending = null
            } else {
                flushPending()
                ctx.record(parsed.node.id, stmt)
            }
            out += parsed.node
        }
    }
    flushPending()
    trailingBlockComments(block)?.let { (first, last) -> out += rawCodeNode(ctx, first, last) }
    return out
}

private class Parsed(val node: Node, val usedPending: Boolean = false)

// ---- comments & raw capture -------------------------------------------------

/** Comments in the contiguous comment/whitespace run directly above [stmt]. */
private fun attachedComments(stmt: PsiElement): List<PsiComment> {
    val comments = ArrayDeque<PsiComment>()
    var cur = stmt.prevSibling
    while (cur is PsiWhiteSpace || cur is PsiComment) {
        if (cur is PsiComment) comments.addFirst(cur)
        cur = cur.prevSibling
    }
    // Drop a leading comment that shares a line with earlier code (it trails the
    // previous statement and is handled there).
    while (comments.isNotEmpty() && !startsItsLine(comments.first())) comments.removeFirst()
    return comments.toList()
}

/** The last comment on [stmt]'s own line after it, or null. */
private fun trailingComment(stmt: PsiElement): PsiComment? {
    var last: PsiComment? = null
    var cur = stmt.nextSibling
    while (true) {
        when {
            cur is PsiComment -> last = cur
            cur is PsiWhiteSpace && !cur.text.contains('\n') -> Unit
            else -> return last
        }
        cur = cur.nextSibling
    }
}

/** Comments between the last statement and the closing brace (or a comment-only block). */
private fun trailingBlockComments(block: KtBlockExpression): Pair<PsiElement, PsiElement>? {
    // Lambda bodies have no own braces, so the last statement is often the last
    // child (nextSibling == null) — that means NO trailing run, not "scan from
    // the block start" (which would re-capture already-consumed comments).
    val lastStmt = block.statements.lastOrNull()
    var first: PsiComment? = null
    var last: PsiComment? = null
    var cur: PsiElement? = if (lastStmt != null) lastStmt.nextSibling else block.firstChild
    while (cur != null && cur != block.rBrace) {
        if (cur is PsiComment) {
            // Same-line trailers were already captured with their statement.
            if (first != null || startsItsLine(cur)) {
                if (first == null) first = cur
                last = cur
            }
        }
        cur = cur.nextSibling
    }
    return if (first != null && last != null) first to last else null
}

private fun startsItsLine(e: PsiElement): Boolean {
    val text = e.containingFile.text
    val start = e.textRange.startOffset
    val lineStart = text.lastIndexOf('\n', start - 1) + 1
    return text.substring(lineStart, start).isBlank()
}

internal fun rawCodeNode(ctx: ParseCtx, first: PsiElement, last: PsiElement): Node.RawCode {
    val text = first.containingFile.text
    var start = first.textRange.startOffset
    val lineStart = text.lastIndexOf('\n', start - 1) + 1
    if (text.substring(lineStart, start).isBlank()) start = lineStart
    val node = Node.RawCode(ctx.newId(), dedent(text.substring(start, last.textRange.endOffset)))
    ctx.sourceRanges[node.id] = start until last.textRange.endOffset
    return node
}

private fun dedent(s: String): String {
    // Raw strings keep captured indentation — re-indenting would edit the literal
    // (mirrors CodeGen's verbatim-emit exception).
    if (s.contains("\"\"\"")) return s
    val lines = s.lines()
    val min = lines.filter { it.isNotBlank() }
        .minOfOrNull { line -> line.takeWhile { it == ' ' }.length } ?: 0
    return lines.joinToString("\n") { if (it.isBlank()) "" else it.substring(minOf(min, it.length)) }
}

// ---- hoisted-state swallowing ------------------------------------------------

/**
 * `var v by remember { mutableStateOf(<literal>) }` — held as pending and consumed
 * by the IMMEDIATELY following stateful component (codegen's strict adjacency);
 * anything else re-materializes it as RawCode in place.
 */
internal class PendingState(
    val name: String,
    val bool: Boolean? = null,
    val str: String? = null,
    val float: Float? = null,
    val stmt: KtProperty,
    val first: PsiElement,
)

private fun matchStateDecl(stmt: KtExpression): PendingState? {
    val p = stmt as? KtProperty ?: return null
    if (!p.isVar || p.receiverTypeReference != null || p.typeReference != null) return null
    val name = p.name ?: return null
    val remember = p.delegateExpression?.unparen() as? KtCallExpression ?: return null
    if (callName(remember) != "remember") return null
    if (remember.valueArguments.any { it !is KtLambdaArgument }) return null
    val lambda = remember.lambdaArguments.singleOrNull()?.getLambdaExpression() ?: return null
    if (lambda.valueParameters.isNotEmpty()) return null
    val only = lambda.bodyExpression?.statements?.singleOrNull()?.unparen() as? KtCallExpression ?: return null
    if (callName(only) != "mutableStateOf") return null
    val arg = only.singlePositionalArg() ?: return null
    boolLit(arg)?.let { return PendingState(name, bool = it, stmt = p, first = p) }
    stringLit(arg)?.let { return PendingState(name, str = it, stmt = p, first = p) }
    floatLit(arg)?.let { return PendingState(name, float = it, stmt = p, first = p) }
    return null
}

/** `{ v = it }` */
private fun isAssignItLambda(expr: KtExpression?, v: String): Boolean {
    val body = singleLambdaStatement(expr) as? KtBinaryExpression ?: return false
    if (body.operationReference.getReferencedName() != "=") return false
    return nameOf(body.left) == v && nameOf(body.right) == "it"
}

/** `{ v = !v }` */
private fun isToggleLambda(expr: KtExpression?, v: String): Boolean {
    val body = singleLambdaStatement(expr) as? KtBinaryExpression ?: return false
    if (body.operationReference.getReferencedName() != "=") return false
    if (nameOf(body.left) != v) return false
    val not = body.right?.unparen() as? KtPrefixExpression ?: return false
    if (not.operationReference.getReferencedName() != "!") return false
    return nameOf(not.baseExpression) == v
}

private fun singleLambdaStatement(expr: KtExpression?): KtExpression? {
    val l = expr?.unparen() as? KtLambdaExpression ?: return null
    if (l.valueParameters.isNotEmpty()) return null
    return l.bodyExpression?.statements?.singleOrNull()?.unparen()
}

/** True when [state]'s var is referenced by any block statement other than its decl and [consumer]. */
private fun stateUsedElsewhere(block: KtBlockExpression, state: PendingState, consumer: KtExpression): Boolean =
    block.statements.any { sibling ->
        if (sibling === state.stmt || sibling === consumer) return@any false
        com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(sibling, org.jetbrains.kotlin.psi.KtNameReferenceExpression::class.java)
            .any { it.getReferencedName() == state.name }
    }

// ---- component dispatch ------------------------------------------------------

private val BUTTON_VARIANTS = mapOf(
    "Button" to ButtonVariant.Filled,
    "ElevatedButton" to ButtonVariant.Elevated,
    "FilledTonalButton" to ButtonVariant.FilledTonal,
    "OutlinedButton" to ButtonVariant.Outlined,
    "TextButton" to ButtonVariant.Text,
)

private val TOP_BAR_VARIANTS = mapOf(
    "TopAppBar" to TopAppBarVariant.Small,
    "CenterAlignedTopAppBar" to TopAppBarVariant.CenterAligned,
    "MediumTopAppBar" to TopAppBarVariant.Medium,
    "LargeTopAppBar" to TopAppBarVariant.Large,
)

private const val FONT_COMMENT_PREFIX = "// Font \""
private const val FONT_COMMENT_SUFFIX = "\" — embed it as a font resource and set fontFamily = FontFamily(Font(...))."
private const val LOCAL_IMAGE_COMMENT = "// Local image — set a URL or wire up a real painter/resource here."

private fun parseComponent(
    stmt: KtExpression,
    comments: List<PsiComment>,
    pending: PendingState?,
    ctx: ParseCtx,
    scopeParam: String?,
): Parsed? {
    val call = stmt.unparen() as? KtCallExpression ?: return null
    val shape = callShape(call) ?: return null
    if (shape.name in ctx.blockedNames) return null

    // Comment consumption contract: components may consume ONE special comment
    // (Text's device-font note, Image's local-image note); any other attached
    // comment forces RawCode so it is never silently dropped.
    val specialComment = comments.singleOrNull()?.text
    val hasPlainComments = comments.isNotEmpty() &&
        !(specialComment != null && (isFontComment(specialComment) || specialComment == LOCAL_IMAGE_COMMENT || isSymbolComment(specialComment)))

    if (hasPlainComments) return null

    val node: Parsed? = when (shape.name) {
        "Text" -> parseText(shape, ctx, scopeParam, specialComment)
        in BUTTON_VARIANTS -> parseButton(shape, ctx, scopeParam)
        "Spacer" -> simpleLeaf(shape, ctx, scopeParam) { id, m -> Node.Spacer(id, m) }
        "AsyncImage" -> if (specialComment == null) parseAsyncImage(shape, ctx, scopeParam) else null
        "Image" -> parseImagePlaceholder(shape, ctx, scopeParam) // local-image comment consumed (data: URL unrecoverable)
        "HorizontalDivider", "Divider" -> simpleLeaf(shape, ctx, scopeParam) { id, m -> Node.Divider(id, m) }
        "Icon" -> parseIcon(shape, ctx, scopeParam, specialComment)
        "IconButton" -> parseIconButton(shape, ctx, scopeParam)
        "OutlinedTextField" -> parseTextField(shape, pending, ctx, scopeParam)
        "Switch" -> parseChecked(shape, pending, ctx, scopeParam) { id, c, m -> Node.Switch(id, c, m) }
        "Checkbox" -> parseChecked(shape, pending, ctx, scopeParam) { id, c, m -> Node.Checkbox(id, c, m) }
        "RadioButton" -> parseRadio(shape, pending, ctx, scopeParam)
        "Slider" -> parseSlider(shape, pending, ctx, scopeParam)
        "CircularProgressIndicator" -> simpleLeaf(shape, ctx, scopeParam) { id, m -> Node.CircularProgress(id, m) }
        "LinearProgressIndicator" -> simpleLeaf(shape, ctx, scopeParam) { id, m -> Node.LinearProgress(id, m) }
        "Card" -> parsePlainContainer(shape, ctx, scopeParam) { id, kids, m -> Node.Card(id, kids, m) }
        "FloatingActionButton" -> parseFab(shape, ctx, scopeParam)
        "Dialog" -> parseDialog(shape, ctx, scopeParam)
        "ModalBottomSheet" -> parseBottomSheet(shape, ctx, scopeParam)
        "Scaffold" -> parseScaffold(shape, ctx, scopeParam)
        in TOP_BAR_VARIANTS -> parseTopAppBar(shape, ctx, scopeParam)
        "Column" -> parseColumn(shape, ctx, scopeParam)
        "Row" -> parseRow(shape, ctx, scopeParam)
        "Box" -> parseBox(shape, ctx, scopeParam)
        else -> null
    }
    if (node != null) return node

    // Bare call to another parsed screen = a component instance.
    if (shape.name in ctx.screenIdsByName &&
        shape.positional.isEmpty() && shape.named.isEmpty() && shape.trailingLambda == null &&
        specialComment == null
    ) {
        val refId = ctx.screenIdsByName.getValue(shape.name)
        ctx.referencedScreenIds += refId
        return Parsed(Node.Instance(ctx.newId(), refId))
    }
    return null
}

private fun isFontComment(text: String): Boolean =
    text.startsWith(FONT_COMMENT_PREFIX) && text.endsWith(FONT_COMMENT_SUFFIX) &&
        text.length > FONT_COMMENT_PREFIX.length + FONT_COMMENT_SUFFIX.length

/** The symbol-icon sourcing note codegen emits above `painterResource(Res.drawable.ic_x)`. */
private fun isSymbolComment(text: String): Boolean =
    text.startsWith("// Icon \"") && "Material Symbols" in text

// ---- shared helpers ----------------------------------------------------------

/** Parse an optional `modifier =` argument; Result.failure = unrecognized chain. */
private fun modifierOf(shape: CallShape, scopeParam: String?): List<ModifierSpec>? {
    val expr = shape.named["modifier"] ?: return emptyList()
    return parseModifierChain(expr, scopeParam)
}

private inline fun simpleLeaf(
    shape: CallShape,
    ctx: ParseCtx,
    scopeParam: String?,
    build: (id: String, modifier: List<ModifierSpec>) -> Node,
): Parsed? {
    if (!shape.argsWithin(setOf("modifier")) || shape.trailingLambda != null) return null
    val m = modifierOf(shape, scopeParam) ?: return null
    return Parsed(build(ctx.newId(), m))
}

private fun childrenOf(lambda: KtLambdaExpression?, ctx: ParseCtx): List<Node>? {
    if (lambda == null) return emptyList()
    if (lambda.valueParameters.isNotEmpty()) return null
    val body = lambda.bodyExpression ?: return emptyList()
    return parseBlock(body, ctx)
}

// ---- leaves -------------------------------------------------------------------

private fun parseText(shape: CallShape, ctx: ParseCtx, scopeParam: String?, comment: String?): Parsed? {
    if (shape.trailingLambda != null || shape.positional.size != 1) return null
    if (!shape.named.keys.all { it in setOf("modifier", "color", "fontSize", "fontWeight", "fontFamily", "lineHeight", "textAlign") }) return null
    val text = stringLit(shape.positional[0]) ?: return null
    val m = modifierOf(shape, scopeParam) ?: return null
    val color = shape.named["color"]?.let { colorValue(it) ?: return null }
    val fontSize = shape.named["fontSize"]?.let { spInt(it) ?: return null } ?: 0
    val fontWeight = shape.named["fontWeight"]?.let { enumFrom(it, "FontWeight") { TextWeight.valueOf(it) } ?: return null }
        ?: TextWeight.Normal
    val fontFamily = shape.named["fontFamily"]?.let { enumFrom(it, "FontFamily") { TextFontFamily.valueOf(it) } ?: return null }
        ?: TextFontFamily.Default
    val textAlign = shape.named["textAlign"]?.let { enumFrom(it, "TextAlign") { TextAlignment.valueOf(it) } ?: return null }
        ?: TextAlignment.Start
    var lineHeight = shape.named["lineHeight"]?.let { spInt(it) ?: return null } ?: 0
    // Auto line height (fontSize × 1.2) round-trips as 0 so the inspector shows "Auto".
    if (fontSize > 0 && lineHeight == (fontSize * 1.2).roundToInt()) lineHeight = 0
    val customFont = comment
        ?.takeIf { isFontComment(it) }
        ?.removePrefix(FONT_COMMENT_PREFIX)?.removeSuffix(FONT_COMMENT_SUFFIX)
        ?: ""
    if (comment != null && customFont.isEmpty()) return null // local-image comment on a Text — not ours
    return Parsed(
        Node.Text(
            id = ctx.newId(), text = text, modifier = m, fontSize = fontSize,
            fontWeight = fontWeight, fontFamily = fontFamily, color = color,
            lineHeight = lineHeight, customFont = customFont, textAlign = textAlign,
        ),
    )
}

private fun parseAsyncImage(shape: CallShape, ctx: ParseCtx, scopeParam: String?): Parsed? {
    if (shape.trailingLambda != null || shape.positional.isNotEmpty()) return null
    if (!shape.named.keys.all { it in setOf("model", "contentDescription", "modifier") }) return null
    val url = stringLit(shape.named["model"] ?: return null) ?: return null
    val desc = stringOrNullLit(shape.named["contentDescription"] ?: return null)?.getOrNull() ?: ""
    val m = modifierOf(shape, scopeParam) ?: return null
    return Parsed(Node.Image(id = ctx.newId(), contentDescription = desc, modifier = m, url = url))
}

private fun parseImagePlaceholder(shape: CallShape, ctx: ParseCtx, scopeParam: String?): Parsed? {
    if (shape.trailingLambda != null || shape.positional.isNotEmpty()) return null
    if (!shape.named.keys.all { it in setOf("painter", "contentDescription", "modifier") }) return null
    val painter = shape.named["painter"]?.unparen() as? KtCallExpression ?: return null
    if (callName(painter) != "ColorPainter") return null
    val color = colorValue(painter.singlePositionalArg()) ?: return null
    val desc = stringOrNullLit(shape.named["contentDescription"] ?: return null)?.getOrNull() ?: ""
    val m = modifierOf(shape, scopeParam) ?: return null
    return Parsed(Node.Image(id = ctx.newId(), contentDescription = desc, placeholderColor = color, modifier = m))
}

private fun parseIcon(shape: CallShape, ctx: ParseCtx, scopeParam: String?, comment: String? = null): Parsed? {
    if (shape.trailingLambda != null || shape.positional.size != 1) return null
    if (!shape.named.keys.all { it in setOf("contentDescription", "modifier") }) return null
    val desc = shape.named["contentDescription"]?.let { stringOrNullLit(it) ?: return null }?.getOrNull() ?: ""
    val m = modifierOf(shape, scopeParam) ?: return null
    // Free-form Material Symbols form: painterResource(Res.drawable.ic_x) + a
    // sourcing comment. The comment (when present) must be OUR canonical one for
    // this symbol — anything else stays verbatim as RawCode.
    painterSymbol(shape.positional[0])?.let { symbol ->
        if (comment != null && comment != composer.codegen.CodeGen.symbolComment(symbol)) return null
        return Parsed(Node.Icon(ctx.newId(), IconKind.Favorite, desc, m, symbol = symbol))
    }
    if (comment != null) return null // a symbol comment on a non-symbol Icon — not ours
    val icon = iconKind(shape.positional[0]) ?: return null
    return Parsed(Node.Icon(ctx.newId(), icon, desc, m))
}

/** `painterResource(Res.drawable.ic_<x>)` → `x`, or null for any other shape. */
private fun painterSymbol(expr: KtExpression?): String? {
    val call = expr?.unparen() as? KtCallExpression ?: return null
    if (callName(call) != "painterResource") return null
    val arg = call.singlePositionalArg()?.unparen() as? KtDotQualifiedExpression ?: return null
    val sel = nameOf(arg.selectorExpression) ?: return null
    if (!sel.startsWith("ic_")) return null
    val recv = arg.receiverExpression.unparen() as? KtDotQualifiedExpression ?: return null
    if (nameOf(recv.receiverExpression) != "Res" || nameOf(recv.selectorExpression) != "drawable") return null
    return sel.removePrefix("ic_").takeIf { it.isNotEmpty() }
}

private fun parseIconButton(shape: CallShape, ctx: ParseCtx, scopeParam: String?): Parsed? {
    if (shape.positional.isNotEmpty()) return null
    if (!shape.named.keys.all { it in setOf("onClick", "modifier") }) return null
    if (!isEmptyLambda(shape.named["onClick"] ?: return null)) return null
    val m = modifierOf(shape, scopeParam) ?: return null
    // Body must be exactly `Icon(Icons.Default.X, contentDescription = null)` —
    // the model has no children slot here.
    val body = shape.trailingLambda ?: return null
    if (body.valueParameters.isNotEmpty()) return null
    val only = body.bodyExpression?.statements?.singleOrNull()?.unparen() as? KtCallExpression ?: return null
    val inner = callShape(only) ?: return null
    if (inner.name != "Icon" || inner.trailingLambda != null || inner.positional.size != 1) return null
    if (!inner.named.keys.all { it == "contentDescription" }) return null
    if (inner.named["contentDescription"]?.let { stringOrNullLit(it)?.getOrNull() } != null) return null
    painterSymbol(inner.positional[0])?.let { symbol ->
        return Parsed(Node.IconButton(ctx.newId(), IconKind.Menu, m, symbol = symbol))
    }
    val icon = iconKind(inner.positional[0]) ?: return null
    return Parsed(Node.IconButton(ctx.newId(), icon, m))
}

private fun parseTextField(shape: CallShape, pending: PendingState?, ctx: ParseCtx, scopeParam: String?): Parsed? {
    val v = pending?.takeIf { it.str != null } ?: return null
    if (shape.trailingLambda != null || shape.positional.isNotEmpty()) return null
    if (!shape.named.keys.all { it in setOf("value", "onValueChange", "modifier", "label") }) return null
    if (nameOf(shape.named["value"]) != v.name) return null
    if (!isAssignItLambda(shape.named["onValueChange"], v.name)) return null
    val m = modifierOf(shape, scopeParam) ?: return null
    var placeholder = ""
    shape.named["label"]?.let { label ->
        val only = singleLambdaStatement(label) as? KtCallExpression ?: return null
        val inner = callShape(only) ?: return null
        if (inner.name != "Text" || inner.named.isNotEmpty() || inner.trailingLambda != null) return null
        placeholder = stringLit(inner.positional.singleOrNull()) ?: return null
    }
    return Parsed(Node.TextField(ctx.newId(), v.str!!, placeholder, m), usedPending = true)
}

private inline fun parseChecked(
    shape: CallShape,
    pending: PendingState?,
    ctx: ParseCtx,
    scopeParam: String?,
    build: (id: String, checked: Boolean, modifier: List<ModifierSpec>) -> Node,
): Parsed? {
    val v = pending?.takeIf { it.bool != null } ?: return null
    if (shape.trailingLambda != null || shape.positional.isNotEmpty()) return null
    if (!shape.named.keys.all { it in setOf("checked", "onCheckedChange", "modifier") }) return null
    if (nameOf(shape.named["checked"]) != v.name) return null
    if (!isAssignItLambda(shape.named["onCheckedChange"], v.name)) return null
    val m = modifierOf(shape, scopeParam) ?: return null
    return Parsed(build(ctx.newId(), v.bool!!, m), usedPending = true)
}

private fun parseRadio(shape: CallShape, pending: PendingState?, ctx: ParseCtx, scopeParam: String?): Parsed? {
    val v = pending?.takeIf { it.bool != null } ?: return null
    if (shape.trailingLambda != null || shape.positional.isNotEmpty()) return null
    if (!shape.named.keys.all { it in setOf("selected", "onClick", "modifier") }) return null
    if (nameOf(shape.named["selected"]) != v.name) return null
    if (!isToggleLambda(shape.named["onClick"], v.name)) return null
    val m = modifierOf(shape, scopeParam) ?: return null
    return Parsed(Node.RadioButton(ctx.newId(), v.bool!!, m), usedPending = true)
}

private fun parseSlider(shape: CallShape, pending: PendingState?, ctx: ParseCtx, scopeParam: String?): Parsed? {
    val v = pending?.takeIf { it.float != null } ?: return null
    if (shape.trailingLambda != null || shape.positional.isNotEmpty()) return null
    if (!shape.named.keys.all { it in setOf("value", "onValueChange", "modifier") }) return null
    if (nameOf(shape.named["value"]) != v.name) return null
    if (!isAssignItLambda(shape.named["onValueChange"], v.name)) return null
    val m = modifierOf(shape, scopeParam) ?: return null
    return Parsed(Node.Slider(ctx.newId(), v.float!!, m), usedPending = true)
}

// ---- containers ---------------------------------------------------------------

private fun parseButton(shape: CallShape, ctx: ParseCtx, scopeParam: String?): Parsed? {
    if (shape.positional.isNotEmpty()) return null
    if (!shape.named.keys.all { it in setOf("onClick", "modifier") }) return null
    if (!isEmptyLambda(shape.named["onClick"] ?: return null)) return null
    val m = modifierOf(shape, scopeParam) ?: return null
    val id = ctx.newId()
    val kids = childrenOf(shape.trailingLambda, ctx) ?: return null
    return Parsed(Node.Button(id = id, modifier = m, variant = BUTTON_VARIANTS.getValue(shape.name), children = kids))
}

private inline fun parsePlainContainer(
    shape: CallShape,
    ctx: ParseCtx,
    scopeParam: String?,
    build: (id: String, children: List<Node>, modifier: List<ModifierSpec>) -> Node,
): Parsed? {
    if (shape.positional.isNotEmpty() || !shape.named.keys.all { it == "modifier" }) return null
    val m = modifierOf(shape, scopeParam) ?: return null
    val id = ctx.newId()
    val kids = childrenOf(shape.trailingLambda, ctx) ?: return null
    return Parsed(build(id, kids, m))
}

private fun parseFab(shape: CallShape, ctx: ParseCtx, scopeParam: String?): Parsed? {
    if (shape.positional.isNotEmpty()) return null
    if (!shape.named.keys.all { it in setOf("onClick", "modifier") }) return null
    if (!isEmptyLambda(shape.named["onClick"] ?: return null)) return null
    val m = modifierOf(shape, scopeParam) ?: return null
    val id = ctx.newId()
    val kids = childrenOf(shape.trailingLambda, ctx) ?: return null
    return Parsed(Node.Fab(id, kids, m))
}

private fun parseColumn(shape: CallShape, ctx: ParseCtx, scopeParam: String?): Parsed? {
    if (shape.positional.isNotEmpty()) return null
    if (!shape.named.keys.all { it in setOf("modifier", "verticalArrangement", "horizontalAlignment") }) return null
    val m = modifierOf(shape, scopeParam) ?: return null
    var arrangement = VArrangement.Top
    var spacing = 0
    shape.named["verticalArrangement"]?.let { expr ->
        spacedBy(expr)?.let { spacing = it } ?: run {
            arrangement = arrangementFrom(expr) { VArrangement.valueOf(it) } ?: return null
        }
    }
    val align = shape.named["horizontalAlignment"]?.let { hAlignFrom(it) ?: return null } ?: HAlignment.Start
    val id = ctx.newId()
    val kids = childrenOf(shape.trailingLambda, ctx) ?: return null
    return Parsed(Node.Column(id, kids, arrangement, align, m, spacing))
}

private fun parseRow(shape: CallShape, ctx: ParseCtx, scopeParam: String?): Parsed? {
    if (shape.positional.isNotEmpty()) return null
    if (!shape.named.keys.all { it in setOf("modifier", "horizontalArrangement", "verticalAlignment") }) return null
    val m = modifierOf(shape, scopeParam) ?: return null
    var arrangement = HArrangement.Start
    var spacing = 0
    shape.named["horizontalArrangement"]?.let { expr ->
        spacedBy(expr)?.let { spacing = it } ?: run {
            arrangement = arrangementFrom(expr) { HArrangement.valueOf(it) } ?: return null
        }
    }
    val align = shape.named["verticalAlignment"]?.let { vAlignFrom(it) ?: return null } ?: VAlignment.Top
    val id = ctx.newId()
    val kids = childrenOf(shape.trailingLambda, ctx) ?: return null
    return Parsed(Node.Row(id, kids, arrangement, align, m, spacing))
}

private fun parseBox(shape: CallShape, ctx: ParseCtx, scopeParam: String?): Parsed? {
    if (shape.positional.isNotEmpty()) return null
    if (!shape.named.keys.all { it in setOf("modifier", "contentAlignment") }) return null
    val m = modifierOf(shape, scopeParam) ?: return null
    val align = shape.named["contentAlignment"]
        ?.let { enumFrom(it, "Alignment") { BoxAlignment.valueOf(it) } ?: return null }
        ?: BoxAlignment.TopStart
    val id = ctx.newId()
    val kids = childrenOf(shape.trailingLambda, ctx) ?: return null
    // Codegen's instance-with-modifier wrapper: Box(modifier = M) { Name() } —
    // collapse back to the Instance so the wrapper doesn't accrete on round trips.
    if (align == BoxAlignment.TopStart && kids.size == 1) {
        val inst = kids[0] as? Node.Instance
        if (inst != null && inst.modifier.isEmpty()) return Parsed(inst.copy(modifier = m))
    }
    return Parsed(Node.Box(id, kids, align, m))
}

private fun parseDialog(shape: CallShape, ctx: ParseCtx, scopeParam: String?): Parsed? {
    // Exact inverse of codegen's wrapper idiom — anything else stays RawCode.
    if (shape.positional.isNotEmpty() || !shape.named.keys.all { it == "onDismissRequest" }) return null
    if (!isEmptyLambda(shape.named["onDismissRequest"] ?: return null)) return null
    val surfCall = singleLambdaStatement(shape.trailingLambda) as? KtCallExpression ?: return null
    val surf = callShape(surfCall) ?: return null
    if (surf.name != "Surface" || surf.positional.isNotEmpty()) return null
    if (!surf.named.keys.all { it in setOf("modifier", "shape") }) return null
    if (shapeCorner(surf.named["shape"] ?: return null) != (16 to composer.model.CornerUnit.Dp)) return null
    val m = surf.named["modifier"]?.let { parseModifierChain(it, scopeParam) ?: return null } ?: emptyList()
    val kids = wrapperColumnChildren(surf.trailingLambda, paddingAll = 24, ctx) ?: return null
    return Parsed(Node.Dialog(ctx.newId(), kids, m))
}

private fun parseBottomSheet(shape: CallShape, ctx: ParseCtx, scopeParam: String?): Parsed? {
    if (shape.positional.isNotEmpty()) return null
    if (!shape.named.keys.all { it in setOf("onDismissRequest", "modifier") }) return null
    if (!isEmptyLambda(shape.named["onDismissRequest"] ?: return null)) return null
    val m = modifierOf(shape, scopeParam) ?: return null
    val kids = wrapperColumnChildren(shape.trailingLambda, paddingAll = 16, ctx) ?: return null
    return Parsed(Node.BottomSheet(ctx.newId(), kids, m))
}

/** The `Column(modifier = Modifier.padding(N.dp)) { children }` inside Dialog/BottomSheet. */
private fun wrapperColumnChildren(lambda: KtLambdaExpression?, paddingAll: Int, ctx: ParseCtx): List<Node>? {
    val colCall = singleLambdaStatement(lambda) as? KtCallExpression ?: return null
    val col = callShape(colCall) ?: return null
    if (col.name != "Column" || col.positional.isNotEmpty() || !col.named.keys.all { it == "modifier" }) return null
    val chain = parseModifierChain(col.named["modifier"] ?: return null) ?: return null
    if (chain != listOf(ModifierSpec.Padding(all = paddingAll, mode = PaddingMode.All))) return null
    return childrenOf(col.trailingLambda, ctx)
}

private fun parseScaffold(shape: CallShape, ctx: ParseCtx, scopeParam: String?): Parsed? {
    if (shape.positional.isNotEmpty()) return null
    if (!shape.named.keys.all { it in setOf("modifier", "topBar", "bottomBar", "floatingActionButton") }) return null
    val m = modifierOf(shape, scopeParam) ?: return null
    // Slot args must be lambdas when present.
    for (slotName in listOf("topBar", "bottomBar", "floatingActionButton")) {
        if (shape.named.containsKey(slotName) && shape.lambdaArg(slotName) == null) return null
    }
    // Only codegen's canonical content param survives round trips: RawCode
    // children may reference it by name, and regeneration always calls it
    // `innerPadding`.
    val content = shape.trailingLambda
    val param = content?.valueParameters?.singleOrNull()?.name
    if (content != null && content.valueParameters.size > 1) return null
    if (param != null && param != "innerPadding") return null

    val id = ctx.newId()
    fun slot(argName: String, slotId: String, slotLabel: String): Node.Slot? {
        val lambda = shape.lambdaArg(argName) ?: return Node.Slot("$id-$slotId", slotLabel)
        if (lambda.valueParameters.isNotEmpty()) return null
        val kids = lambda.bodyExpression?.let { parseBlock(it, ctx) } ?: emptyList()
        ctx.record("$id-$slotId", lambda)
        return Node.Slot("$id-$slotId", slotLabel, kids)
    }
    val topBar = slot("topBar", "topBar", "topBar") ?: return null
    val bottomBar = slot("bottomBar", "bottomBar", "bottomBar") ?: return null
    val fab = slot("floatingActionButton", "fab", "fab") ?: return null
    val kids = content?.bodyExpression?.let { parseBlock(it, ctx, scopeParam = param) } ?: emptyList()
    return Parsed(Node.Scaffold(id, kids, topBar, bottomBar, fab, m))
}

private fun parseTopAppBar(shape: CallShape, ctx: ParseCtx, scopeParam: String?): Parsed? {
    if (shape.trailingLambda != null || shape.positional.isNotEmpty()) return null
    if (!shape.named.keys.all { it in setOf("title", "navigationIcon", "actions", "modifier") }) return null
    val m = modifierOf(shape, scopeParam) ?: return null
    val id = ctx.newId()
    val title = shape.named["title"]?.let { singleSlotNode(it, ctx) ?: return null }?.firstOrNull()
    val nav = shape.named["navigationIcon"]?.let { singleSlotNode(it, ctx) ?: return null }?.firstOrNull()
    val actions = shape.named["actions"]?.let { expr ->
        val lambda = expr.unparen() as? KtLambdaExpression ?: return null
        if (lambda.valueParameters.isNotEmpty()) return null
        lambda.bodyExpression?.let { parseBlock(it, ctx) } ?: emptyList()
    } ?: emptyList()
    return Parsed(Node.TopAppBar(id, title, nav, actions, m, TOP_BAR_VARIANTS.getValue(shape.name)))
}

/**
 * A single-node slot (`title = { … }`): 0 statements → empty, 1 → parsed
 * normally, 2+ → ONE RawCode spanning the lambda body (the slot holds one node).
 */
private fun singleSlotNode(expr: KtExpression, ctx: ParseCtx): List<Node>? {
    val lambda = expr.unparen() as? KtLambdaExpression ?: return null
    if (lambda.valueParameters.isNotEmpty()) return null
    val body = lambda.bodyExpression ?: return emptyList()
    if (body.statements.size >= 2) {
        // Span from the first comment/statement so leading comments aren't dropped.
        val first = generateSequence(body.firstChild?.nextSibling) { it.nextSibling }
            .firstOrNull { it is PsiComment || (it is KtExpression && it in body.statements) }
            ?: body.statements.first()
        return listOf(rawCodeNode(ctx, first, body.statements.last()))
    }
    return parseBlock(body, ctx)
}

// ---- small expression readers --------------------------------------------------

/** `Prefix.Name` (e.g. `FontWeight.Bold`) → enum via [convert]; null on mismatch. */
private inline fun <T> enumFrom(expr: KtExpression, prefix: String, convert: (String) -> T): T? {
    val (receiver, name) = dottedName(expr) ?: return null
    if (receiver != prefix) return null
    return runCatching { convert(name) }.getOrNull()
}

/** `Arrangement.spacedBy(N.dp)` → N. */
private fun spacedBy(expr: KtExpression): Int? {
    val dot = expr.unparen() as? KtDotQualifiedExpression ?: return null
    if (nameOf(dot.receiverExpression) != "Arrangement") return null
    val call = dot.selectorExpression?.unparen() as? KtCallExpression ?: return null
    if (callName(call) != "spacedBy") return null
    return dpInt(call.singlePositionalArg())
}

private inline fun <T> arrangementFrom(expr: KtExpression, convert: (String) -> T): T? =
    enumFrom(expr, "Arrangement", convert)

private fun hAlignFrom(expr: KtExpression): HAlignment? = when (dottedName(expr)) {
    "Alignment" to "Start" -> HAlignment.Start
    "Alignment" to "CenterHorizontally" -> HAlignment.Center
    "Alignment" to "End" -> HAlignment.End
    else -> null
}

private fun vAlignFrom(expr: KtExpression): VAlignment? = when (dottedName(expr)) {
    "Alignment" to "Top" -> VAlignment.Top
    "Alignment" to "CenterVertically" -> VAlignment.Center
    "Alignment" to "Bottom" -> VAlignment.Bottom
    else -> null
}

/** `Icons.Default.X` → [IconKind.X]. */
private fun iconKind(expr: KtExpression): IconKind? {
    val outer = expr.unparen() as? KtDotQualifiedExpression ?: return null
    val name = nameOf(outer.selectorExpression) ?: return null
    val inner = outer.receiverExpression.unparen() as? KtDotQualifiedExpression ?: return null
    if (nameOf(inner.receiverExpression) != "Icons" || nameOf(inner.selectorExpression) != "Default") return null
    return runCatching { IconKind.valueOf(name) }.getOrNull()
}
