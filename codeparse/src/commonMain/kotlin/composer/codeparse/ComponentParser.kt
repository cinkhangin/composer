package composer.codeparse

import composer.model.BoxAlignment
import composer.model.ChipVariant
import composer.model.ButtonVariant
import composer.model.HAlignment
import composer.model.HArrangement
import composer.model.IconKind
import composer.model.ModifierSpec
import composer.model.NavAction
import composer.model.Node
import composer.model.PaddingMode
import composer.model.TextAlignment
import composer.model.TextFontFamily
import composer.model.TextWeight
import composer.model.TopAppBarVariant
import composer.model.VAlignment
import composer.model.VArrangement
import kotlin.math.roundToInt

/**
 * Statement-level parsing: the exact inverse of CodeGen's per-component emission.
 * Anything outside the recognized grammar becomes a [Node.RawCode] carrying the
 * statement's verbatim (dedented) source — child-level isolation, so one opaque
 * statement never poisons its recognized siblings.
 */
internal class ParseCtx(
    /** The full source text (raw capture + line-start expansion). */
    val text: String,
    /** All comments in the file, sorted by offset. */
    val comments: List<KComment>,
    /** Simple names disabled file-wide by a conflicting explicit import. */
    val blockedNames: Set<String>,
    /** Top-level `@Composable` function name → its screen id (for instances). */
    val screenIdsByName: Map<String, String>,
    /** Prepended to node ids — app mode parses each file separately and ids must stay app-unique. */
    val idPrefix: String = "",
) {
    private var n = 0
    fun newId(): String = "${idPrefix}p${++n}"

    /** Exact source for an expression whose range follows end-exclusive PSI semantics. */
    fun sourceOf(expr: KExpr): String = text.substring(expr.range.first, expr.range.last + 1)

    /** Screen ids referenced by parsed instances → Artboard.componentIds. */
    val referencedScreenIds = LinkedHashSet<String>()

    /** Best-effort node id → source range (parse-time offsets, end exclusive). */
    val sourceRanges = mutableMapOf<String, IntRange>()

    /**
     * The current function's canonical nav-callback params (name → action);
     * set per screen by DesignParser, empty for non-canonical signatures.
     */
    var navParams: Map<String, NavAction> = emptyMap()

    fun record(id: String, range: IntRange) {
        sourceRanges[id] = range
    }
}

// ---- block parsing ---------------------------------------------------------

/** Parse a block's statements into nodes ([scopeParam] = Scaffold content lambda param). */
internal fun parseBlock(block: KBlock, ctx: ParseCtx, scopeParam: String? = null): List<Node> {
    val out = mutableListOf<Node>()
    var pending: PendingState? = null

    fun flushPending() {
        pending?.let { out += rawCodeNode(ctx, it.stmt.range.first, it.stmt.range.last + 1) }
        pending = null
    }

    block.statements.forEachIndexed { index, stmt ->
        val comments = attachedComments(ctx.text, ctx.comments, block, index)
        val trailing = trailingComment(ctx.text, ctx.comments, block, index)
        if (trailing != null) {
            // A same-line trailing comment can't ride on a model node — preserve
            // the whole line (plus attached comments) verbatim.
            flushPending()
            out += rawCodeNode(ctx, comments.firstOrNull()?.range?.first ?: stmt.range.first, trailing.range.last + 1)
            return@forEachIndexed
        }
        if (comments.isEmpty()) {
            matchStateDecl(stmt)?.let { state ->
                flushPending()
                pending = state
                return@forEachIndexed
            }
        }
        val parsed = parseComponent(stmt, comments, pending, ctx, scopeParam)
        if (parsed == null) {
            flushPending()
            out += rawCodeNode(ctx, comments.firstOrNull()?.range?.first ?: stmt.range.first, stmt.range.last + 1)
        } else if (parsed.usedPending && stateUsedElsewhere(block, pending!!, stmt)) {
            // Swallowing renames the var to stateN on regeneration — unsafe when
            // any OTHER statement references it. Preserve decl + consumer verbatim.
            flushPending()
            out += rawCodeNode(ctx, stmt.range.first, stmt.range.last + 1)
        } else {
            if (parsed.usedPending) {
                // The swallowed state decl regenerates with its consumer — one range.
                ctx.sourceRanges[parsed.node.id] = pending!!.stmt.range.first until stmt.range.last + 1
                pending = null
            } else {
                flushPending()
                ctx.record(parsed.node.id, stmt.range)
            }
            out += parsed.node
        }
    }
    flushPending()
    trailingBlockComments(ctx.text, ctx.comments, block)?.let { (first, last) -> out += rawCodeNode(ctx, first, last) }
    return out
}

private class Parsed(val node: Node, val usedPending: Boolean = false)

// ---- raw capture -------------------------------------------------------------

internal fun rawCodeNode(ctx: ParseCtx, startOffset: Int, endOffset: Int): Node.RawCode {
    val text = ctx.text
    var start = startOffset
    val lineStart = text.lastIndexOf('\n', start - 1) + 1
    if (text.substring(lineStart, start).isBlank()) start = lineStart
    val node = Node.RawCode(ctx.newId(), dedent(text.substring(start, endOffset)))
    ctx.sourceRanges[node.id] = start until endOffset
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
    val int: Int? = null,
    val stmt: KStatement,
)

private fun matchStateDecl(stmt: KStatement): PendingState? {
    val p = stmt as? KPropertyStatement ?: return null
    if (!p.isVar || p.hasReceiver || p.hasType) return null
    val name = p.name ?: return null
    val remember = p.delegate?.unparen() as? KCall ?: return null
    if (callName(remember) != "remember") return null
    if (remember.args.isNotEmpty()) return null
    val lambda = remember.trailingLambdas.singleOrNull() ?: return null
    if (lambda.params.isNotEmpty()) return null
    val only = lambda.body.singleExprStatement() as? KCall ?: return null
    if (callName(only) != "mutableStateOf") return null
    val arg = only.singlePositionalArg() ?: return null
    boolLit(arg)?.let { return PendingState(name, bool = it, stmt = p) }
    stringLit(arg)?.let { return PendingState(name, str = it, stmt = p) }
    floatLit(arg)?.let { return PendingState(name, float = it, stmt = p) }
    intLit(arg)?.let { return PendingState(name, int = it, stmt = p) }
    return null
}

/** The single expression statement of a block, unparenthesized, or null. */
private fun KBlock.singleExprStatement(): KExpr? =
    (statements.singleOrNull() as? KExprStatement)?.expr?.unparen()

/**
 * The canonical onClick inverse: `{}` → [NavAction.None]; a bare identifier
 * naming one of the function's declared nav params → its action; anything
 * else → null (whole statement RawCode, as before).
 */
private fun navActionOf(expr: KExpr?, ctx: ParseCtx): NavAction? = when {
    isEmptyLambda(expr) -> NavAction.None
    else -> nameOf(expr?.unparen())?.let { ctx.navParams[it] }
}

/** `{ v = it }` */
private fun isAssignItLambda(expr: KExpr?, v: String): Boolean {
    val body = singleLambdaStatement(expr) as? KBinary ?: return false
    if (body.op != "=") return false
    return nameOf(body.left) == v && nameOf(body.right) == "it"
}

/** `{ v = !v }` */
private fun isToggleLambda(expr: KExpr?, v: String): Boolean {
    val body = singleLambdaStatement(expr) as? KBinary ?: return false
    if (body.op != "=") return false
    if (nameOf(body.left) != v) return false
    val not = body.right?.unparen() as? KPrefix ?: return false
    if (not.op != "!") return false
    return nameOf(not.base) == v
}

private fun singleLambdaStatement(expr: KExpr?): KExpr? {
    val l = expr?.unparen() as? KLambda ?: return null
    if (l.params.isNotEmpty()) return null
    return l.body.singleExprStatement()
}

/** True when [state]'s var is referenced by any block statement other than its decl and [consumer]. */
private fun stateUsedElsewhere(block: KBlock, state: PendingState, consumer: KStatement): Boolean =
    block.statements.any { sibling ->
        if (sibling === state.stmt || sibling === consumer) return@any false
        sibling.tokens.any { it.kind == TokKind.IDENT && it.text == state.name }
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

private val CHIP_VARIANTS = mapOf(
    "AssistChip" to ChipVariant.Assist,
    "FilterChip" to ChipVariant.Filter,
    "InputChip" to ChipVariant.Input,
    "SuggestionChip" to ChipVariant.Suggestion,
)

private const val FONT_COMMENT_PREFIX = "// Font \""
private const val FONT_COMMENT_SUFFIX = "\" — embed it as a font resource and set fontFamily = FontFamily(Font(...))."
private const val LOCAL_IMAGE_COMMENT = "// Local image — set a URL or wire up a real painter/resource here."

private fun parseComponent(
    stmt: KStatement,
    comments: List<KComment>,
    pending: PendingState?,
    ctx: ParseCtx,
    scopeParam: String?,
): Parsed? {
    val call = (stmt as? KExprStatement)?.expr?.unparen() as? KCall ?: return null
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
        "Card" -> parseCard(shape, ctx, scopeParam)
        "FloatingActionButton" -> parseFab(shape, ctx, scopeParam)
        "Dialog" -> parseDialog(shape, ctx, scopeParam)
        "ModalBottomSheet" -> parseBottomSheet(shape, ctx, scopeParam)
        "Scaffold" -> parseScaffold(shape, ctx, scopeParam)
        in TOP_BAR_VARIANTS -> parseTopAppBar(shape, ctx, scopeParam)
        "TabRow" -> parseTabRow(shape, pending, ctx, scopeParam)
        "Tab" -> parseStandaloneTab(shape, ctx, scopeParam)
        "NavigationBar" -> parseNavigationBar(shape, pending, ctx, scopeParam)
        in CHIP_VARIANTS -> parseChip(shape, pending, ctx, scopeParam)
        "BadgedBox" -> parseBadgedBox(shape, ctx, scopeParam)
        "Canvas" -> parseCanvas(shape, ctx, scopeParam)
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

/** Parse an optional `modifier =` argument; null = unrecognized chain. */
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

private fun childrenOf(lambda: KLambda?, ctx: ParseCtx): List<Node>? {
    if (lambda == null) return emptyList()
    if (lambda.params.isNotEmpty()) return null
    return parseBlock(lambda.body, ctx)
}

// ---- leaves -------------------------------------------------------------------

private fun parseText(shape: CallShape, ctx: ParseCtx, scopeParam: String?, comment: String?): Parsed? {
    if (shape.trailingLambda != null) return null
    if (!shape.named.keys.all { it in setOf("text", "modifier", "color", "fontSize", "fontWeight", "fontFamily", "lineHeight", "textAlign") }) return null
    val textExpr = when {
        shape.positional.size == 1 && "text" !in shape.named -> shape.positional.single()
        shape.positional.isEmpty() -> shape.named["text"] ?: return null
        else -> return null
    }
    val literal = stringLit(textExpr)
    val textExpression = if (literal == null) ctx.sourceOf(textExpr) else ""
    val text = literal ?: stringPreview(textExpr) ?: textExpression
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
            textExpression = textExpression,
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
    val painter = shape.named["painter"]?.unparen() as? KCall ?: return null
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
private fun painterSymbol(expr: KExpr?): String? {
    val call = expr?.unparen() as? KCall ?: return null
    if (callName(call) != "painterResource") return null
    val arg = call.singlePositionalArg()?.unparen().asDot() ?: return null
    val sel = nameOf(arg.selector) ?: return null
    if (!sel.startsWith("ic_")) return null
    val recv = arg.receiver.unparen().asDot() ?: return null
    if (nameOf(recv.receiver) != "Res" || nameOf(recv.selector) != "drawable") return null
    return sel.removePrefix("ic_").takeIf { it.isNotEmpty() }
}

private fun parseIconButton(shape: CallShape, ctx: ParseCtx, scopeParam: String?): Parsed? {
    if (shape.positional.isNotEmpty()) return null
    if (!shape.named.keys.all { it in setOf("onClick", "modifier") }) return null
    val nav = navActionOf(shape.named["onClick"] ?: return null, ctx) ?: return null
    val m = modifierOf(shape, scopeParam) ?: return null
    // Body must be exactly `Icon(Icons.Default.X, contentDescription = null)` —
    // the model has no children slot here.
    val body = shape.trailingLambda ?: return null
    if (body.params.isNotEmpty()) return null
    val only = body.body.singleExprStatement() as? KCall ?: return null
    val inner = callShape(only) ?: return null
    if (inner.name != "Icon" || inner.trailingLambda != null || inner.positional.size != 1) return null
    if (!inner.named.keys.all { it == "contentDescription" }) return null
    if (inner.named["contentDescription"]?.let { stringOrNullLit(it)?.getOrNull() } != null) return null
    painterSymbol(inner.positional[0])?.let { symbol ->
        return Parsed(Node.IconButton(ctx.newId(), IconKind.Menu, m, symbol = symbol, navAction = nav))
    }
    val icon = iconKind(inner.positional[0]) ?: return null
    return Parsed(Node.IconButton(ctx.newId(), icon, m, navAction = nav))
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
        val only = singleLambdaStatement(label) as? KCall ?: return null
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
    val nav = navActionOf(shape.named["onClick"] ?: return null, ctx) ?: return null
    val m = modifierOf(shape, scopeParam) ?: return null
    val id = ctx.newId()
    val kids = childrenOf(shape.trailingLambda, ctx) ?: return null
    return Parsed(Node.Button(id = id, modifier = m, variant = BUTTON_VARIANTS.getValue(shape.name), children = kids, navAction = nav))
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

private fun parseCard(shape: CallShape, ctx: ParseCtx, scopeParam: String?): Parsed? {
    if (shape.positional.isNotEmpty()) return null
    if (!shape.named.keys.all { it in setOf("onClick", "modifier") }) return null
    // The clickable overload carries a nav action; the plain form has no onClick.
    val nav = shape.named["onClick"]?.let { expr ->
        val a = navActionOf(expr, ctx) ?: return null
        if (a == NavAction.None) return null // canonical plain Card omits onClick entirely
        a
    } ?: NavAction.None
    val m = modifierOf(shape, scopeParam) ?: return null
    val id = ctx.newId()
    val kids = childrenOf(shape.trailingLambda, ctx) ?: return null
    return Parsed(Node.Card(id, kids, m, navAction = nav))
}

private fun parseFab(shape: CallShape, ctx: ParseCtx, scopeParam: String?): Parsed? {
    if (shape.positional.isNotEmpty()) return null
    if (!shape.named.keys.all { it in setOf("onClick", "modifier") }) return null
    val nav = navActionOf(shape.named["onClick"] ?: return null, ctx) ?: return null
    val m = modifierOf(shape, scopeParam) ?: return null
    val id = ctx.newId()
    val kids = childrenOf(shape.trailingLambda, ctx) ?: return null
    return Parsed(Node.Fab(id, kids, m, navAction = nav))
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
    val surfCall = singleLambdaStatement(shape.trailingLambda) as? KCall ?: return null
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
private fun wrapperColumnChildren(lambda: KLambda?, paddingAll: Int, ctx: ParseCtx): List<Node>? {
    val colCall = singleLambdaStatement(lambda) as? KCall ?: return null
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
    val param = content?.params?.singleOrNull()
    if (content != null && content.params.size > 1) return null
    if (param != null && param != "innerPadding") return null

    val id = ctx.newId()
    fun slot(argName: String, slotId: String, slotLabel: String): Node.Slot? {
        val lambda = shape.lambdaArg(argName) ?: return Node.Slot("$id-$slotId", slotLabel)
        if (lambda.params.isNotEmpty()) return null
        val kids = parseBlock(lambda.body, ctx)
        ctx.record("$id-$slotId", lambda.range)
        return Node.Slot("$id-$slotId", slotLabel, kids)
    }
    val topBar = slot("topBar", "topBar", "topBar") ?: return null
    val bottomBar = slot("bottomBar", "bottomBar", "bottomBar") ?: return null
    val fab = slot("floatingActionButton", "fab", "fab") ?: return null
    val kids = content?.let { parseBlock(it.body, ctx, scopeParam = param) } ?: emptyList()
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
        val lambda = expr.unparen() as? KLambda ?: return null
        if (lambda.params.isNotEmpty()) return null
        parseBlock(lambda.body, ctx)
    } ?: emptyList()
    return Parsed(Node.TopAppBar(id, title, nav, actions, m, TOP_BAR_VARIANTS.getValue(shape.name)))
}

/**
 * A single-node slot (`title = { … }`): 0 statements → empty, 1 → parsed
 * normally, 2+ → ONE RawCode spanning the lambda body (the slot holds one node).
 */
private fun singleSlotNode(expr: KExpr, ctx: ParseCtx): List<Node>? {
    val lambda = expr.unparen() as? KLambda ?: return null
    if (lambda.params.isNotEmpty()) return null
    val body = lambda.body
    if (body.statements.size >= 2) {
        // Span from the first comment/statement so leading comments aren't dropped.
        val firstComment = ctx.comments.firstOrNull {
            it.range.first >= body.bodyRange.first && it.range.last <= body.bodyRange.last
        }
        val first = minOf(
            firstComment?.range?.first ?: Int.MAX_VALUE,
            body.statements.first().range.first,
        )
        return listOf(rawCodeNode(ctx, first, body.statements.last().range.last + 1))
    }
    return parseBlock(body, ctx)
}

// ---- small expression readers --------------------------------------------------

/** `Prefix.Name` (e.g. `FontWeight.Bold`) → enum via [convert]; null on mismatch. */
private inline fun <T> enumFrom(expr: KExpr, prefix: String, convert: (String) -> T): T? {
    val (receiver, name) = dottedName(expr) ?: return null
    if (receiver != prefix) return null
    return runCatching { convert(name) }.getOrNull()
}

/** `Arrangement.spacedBy(N.dp)` → N. */
private fun spacedBy(expr: KExpr): Int? {
    val dot = expr.unparen().asDot() ?: return null
    if (nameOf(dot.receiver) != "Arrangement") return null
    val call = dot.selector?.unparen() as? KCall ?: return null
    if (callName(call) != "spacedBy") return null
    return dpInt(call.singlePositionalArg())
}

private inline fun <T> arrangementFrom(expr: KExpr, convert: (String) -> T): T? =
    enumFrom(expr, "Arrangement", convert)

private fun hAlignFrom(expr: KExpr): HAlignment? = when (dottedName(expr)) {
    "Alignment" to "Start" -> HAlignment.Start
    "Alignment" to "CenterHorizontally" -> HAlignment.Center
    "Alignment" to "End" -> HAlignment.End
    else -> null
}

private fun vAlignFrom(expr: KExpr): VAlignment? = when (dottedName(expr)) {
    "Alignment" to "Top" -> VAlignment.Top
    "Alignment" to "CenterVertically" -> VAlignment.Center
    "Alignment" to "Bottom" -> VAlignment.Bottom
    else -> null
}

// ---- tabs / navigation / chips / badge ----------------------------------------

/** `state == N` → N (the item index codegen derives selection from). */
private fun intEqValue(expr: KExpr?, v: String): Int? {
    val b = expr?.unparen() as? KBinary ?: return null
    if (b.op != "==") return null
    if (nameOf(b.left) != v) return null
    return intLit(b.right)
}

/** `{ state = N }` → N. */
private fun assignIntLambda(expr: KExpr?, v: String): Int? {
    val body = singleLambdaStatement(expr) as? KBinary ?: return null
    if (body.op != "=") return null
    if (nameOf(body.left) != v) return null
    return intLit(body.right)
}

/** `{ Text("x") }` → x. */
private fun lambdaTextLabel(expr: KExpr?): String? {
    val only = singleLambdaStatement(expr ?: return null) as? KCall ?: return null
    val ls = callShape(only) ?: return null
    if (ls.name != "Text" || ls.positional.size != 1 || ls.named.isNotEmpty() || ls.trailingLambda != null) return null
    return stringLit(ls.positional[0])
}

/**
 * An icon slot lambda: `{}` → "" (no icon), `{ Icon(painterResource(Res.drawable.ic_x),
 * contentDescription = null) }` → x (a sourcing comment inside the lambda is
 * tolerated — regeneration reproduces it from the symbol). Anything else → null.
 */
private fun lambdaIconSymbol(expr: KExpr?): String? {
    val lam = expr?.unparen() as? KLambda ?: return null
    if (lam.params.isNotEmpty()) return null
    val stmts = lam.body.statements
    if (stmts.isEmpty()) return ""
    val only = (stmts.singleOrNull() as? KExprStatement)?.expr?.unparen() as? KCall ?: return null
    val ish = callShape(only) ?: return null
    if (ish.name != "Icon" || ish.trailingLambda != null || ish.positional.size != 1) return null
    if (!ish.named.keys.all { it == "contentDescription" }) return null
    if (ish.named["contentDescription"]?.let { stringOrNullLit(it)?.getOrNull() } != null) return null
    return painterSymbol(ish.positional[0])
}

private fun parseTabRow(shape: CallShape, pending: PendingState?, ctx: ParseCtx, scopeParam: String?): Parsed? {
    val v = pending?.takeIf { it.int != null } ?: return null
    if (shape.positional.isNotEmpty()) return null
    if (!shape.named.keys.all { it in setOf("selectedTabIndex", "modifier") }) return null
    if (nameOf(shape.named["selectedTabIndex"]) != v.name) return null
    val m = modifierOf(shape, scopeParam) ?: return null
    val body = shape.trailingLambda ?: return null
    if (body.params.isNotEmpty()) return null
    val tabs = mutableListOf<Node>()
    for ((i, stmt) in body.body.statements.withIndex()) {
        val call = (stmt as? KExprStatement)?.expr?.unparen() as? KCall ?: return null
        val ts = callShape(call) ?: return null
        if (ts.name != "Tab" || ts.positional.isNotEmpty() || ts.trailingLambda != null) return null
        if (!ts.named.keys.all { it in setOf("selected", "onClick", "text", "modifier") }) return null
        if (intEqValue(ts.named["selected"], v.name) != i) return null
        if (assignIntLambda(ts.named["onClick"], v.name) != i) return null
        val label = lambdaTextLabel(ts.named["text"]) ?: return null
        val tm = modifierOf(ts, null) ?: return null
        tabs += Node.Tab(ctx.newId(), label, tm)
    }
    return Parsed(Node.TabRow(ctx.newId(), tabs, selectedIndex = v.int!!, modifier = m), usedPending = true)
}

private fun parseStandaloneTab(shape: CallShape, ctx: ParseCtx, scopeParam: String?): Parsed? {
    if (shape.positional.isNotEmpty() || shape.trailingLambda != null) return null
    if (!shape.named.keys.all { it in setOf("selected", "onClick", "text", "modifier") }) return null
    if (boolLit(shape.named["selected"]) != false) return null
    if (!isEmptyLambda(shape.named["onClick"] ?: return null)) return null
    val label = lambdaTextLabel(shape.named["text"]) ?: return null
    val m = modifierOf(shape, scopeParam) ?: return null
    return Parsed(Node.Tab(ctx.newId(), label, m))
}

private fun parseNavigationBar(shape: CallShape, pending: PendingState?, ctx: ParseCtx, scopeParam: String?): Parsed? {
    val v = pending?.takeIf { it.int != null } ?: return null
    if (shape.positional.isNotEmpty()) return null
    if (!shape.named.keys.all { it == "modifier" }) return null
    val m = modifierOf(shape, scopeParam) ?: return null
    val body = shape.trailingLambda ?: return null
    if (body.params.isNotEmpty()) return null
    val items = mutableListOf<Node>()
    for ((i, stmt) in body.body.statements.withIndex()) {
        val call = (stmt as? KExprStatement)?.expr?.unparen() as? KCall ?: return null
        val ns = callShape(call) ?: return null
        if (ns.name != "NavigationBarItem" || ns.positional.isNotEmpty() || ns.trailingLambda != null) return null
        if (!ns.named.keys.all { it in setOf("selected", "onClick", "icon", "label", "modifier") }) return null
        if (intEqValue(ns.named["selected"], v.name) != i) return null
        if (assignIntLambda(ns.named["onClick"], v.name) != i) return null
        val symbol = lambdaIconSymbol(ns.named["icon"] ?: return null) ?: return null
        val label = lambdaTextLabel(ns.named["label"]) ?: return null
        val im = modifierOf(ns, null) ?: return null
        items += Node.NavItem(ctx.newId(), label, symbol, im)
    }
    return Parsed(Node.NavigationBar(ctx.newId(), items, selectedIndex = v.int!!, modifier = m), usedPending = true)
}

private fun parseChip(shape: CallShape, pending: PendingState?, ctx: ParseCtx, scopeParam: String?): Parsed? {
    val variant = CHIP_VARIANTS[shape.name] ?: return null
    if (shape.positional.isNotEmpty() || shape.trailingLambda != null) return null
    val iconParam = if (variant == ChipVariant.Suggestion) "icon" else "leadingIcon"
    val stateful = variant == ChipVariant.Filter || variant == ChipVariant.Input
    val allowed = buildSet {
        add("onClick"); add("label"); add("modifier"); add(iconParam)
        if (stateful) add("selected")
    }
    if (!shape.named.keys.all { it in allowed }) return null
    val label = lambdaTextLabel(shape.named["label"]) ?: return null
    val symbol = shape.named[iconParam]?.let { lambdaIconSymbol(it) ?: return null } ?: ""
    val m = modifierOf(shape, scopeParam) ?: return null
    if (stateful) {
        // Toggle form: hoisted state var + toggle lambda (no nav action).
        val v = pending?.takeIf { it.bool != null }
        if (v != null && nameOf(shape.named["selected"]) == v.name && isToggleLambda(shape.named["onClick"], v.name)) {
            return Parsed(Node.Chip(ctx.newId(), label, variant, selected = v.bool!!, symbol = symbol, modifier = m), usedPending = true)
        }
        // Nav form: a chip that navigates doesn't toggle — literal `selected` + nav onClick.
        val selected = boolLit(shape.named["selected"] ?: return null) ?: return null
        val nav = navActionOf(shape.named["onClick"] ?: return null, ctx) ?: return null
        if (nav == NavAction.None) return null // canonical stateless Filter/Input always navigates
        return Parsed(Node.Chip(ctx.newId(), label, variant, selected = selected, symbol = symbol, modifier = m, navAction = nav))
    }
    val nav = navActionOf(shape.named["onClick"] ?: return null, ctx) ?: return null
    return Parsed(Node.Chip(ctx.newId(), label, variant, symbol = symbol, modifier = m, navAction = nav))
}

private fun parseBadgedBox(shape: CallShape, ctx: ParseCtx, scopeParam: String?): Parsed? {
    if (shape.positional.isNotEmpty()) return null
    if (!shape.named.keys.all { it in setOf("badge", "modifier") }) return null
    val badgeLambda = shape.named["badge"] ?: return null
    val badgeCall = singleLambdaStatement(badgeLambda) as? KCall ?: return null
    val bs = callShape(badgeCall) ?: return null
    if (bs.name != "Badge" || bs.positional.isNotEmpty() || bs.named.isNotEmpty()) return null
    val badge = when (val lam = bs.trailingLambda) {
        null -> ""
        else -> {
            if (lam.params.isNotEmpty()) return null
            val only = (lam.body.statements.singleOrNull() as? KExprStatement)?.expr?.unparen() as? KCall ?: return null
            val tsh = callShape(only) ?: return null
            if (tsh.name != "Text" || tsh.positional.size != 1 || tsh.named.isNotEmpty() || tsh.trailingLambda != null) return null
            stringLit(tsh.positional[0]) ?: return null
        }
    }
    val m = modifierOf(shape, scopeParam) ?: return null
    val kids = childrenOf(shape.trailingLambda, ctx) ?: return null
    return Parsed(Node.BadgedBox(ctx.newId(), badge, kids, m))
}

// ---- canvas shapes --------------------------------------------------------------

/** `N.dp.toPx()` → N (codegen parenthesizes negatives: `(-8).dp.toPx()`). */
private fun dpPxValue(expr: KExpr?): Int? {
    val dot = expr?.unparen().asDot() ?: return null
    val call = dot.selector?.unparen() as? KCall ?: return null
    if (callName(call) != "toPx" || call.args.isNotEmpty() || call.trailingLambdas.isNotEmpty()) return null
    return dpInt(dot.receiver)
}

/** `Offset(a.dp.toPx(), b.dp.toPx())` / `Size(...)` → the two dp ints. */
private fun dpPxPair(expr: KExpr?, fnName: String): Pair<Int, Int>? {
    val call = expr?.unparen() as? KCall ?: return null
    if (callName(call) != fnName) return null
    val cs = callShape(call) ?: return null
    if (cs.named.isNotEmpty() || cs.trailingLambda != null || cs.positional.size != 2) return null
    val a = dpPxValue(cs.positional[0]) ?: return null
    val b = dpPxValue(cs.positional[1]) ?: return null
    return a to b
}

/** `Stroke(w.dp.toPx())` → w. */
private fun strokeWidthOf(expr: KExpr?): Int? {
    val call = expr?.unparen() as? KCall ?: return null
    if (callName(call) != "Stroke") return null
    val cs = callShape(call) ?: return null
    if (cs.named.isNotEmpty() || cs.trailingLambda != null) return null
    return dpPxValue(cs.positional.singleOrNull())
}

/** `120f` float literal that is a whole number → 120 (angles are Int in the model). */
private fun intFromFloat(expr: KExpr?): Int? =
    floatLit(expr)?.takeIf { it == it.toInt().toFloat() }?.toInt()

private fun parseCanvas(shape: CallShape, ctx: ParseCtx, scopeParam: String?): Parsed? {
    if (shape.positional.isNotEmpty()) return null
    if (!shape.named.keys.all { it == "modifier" }) return null
    val m = modifierOf(shape, scopeParam) ?: return null
    val body = shape.trailingLambda ?: return null
    if (body.params.isNotEmpty()) return null
    val shapes = mutableListOf<Node>()
    for (stmt in body.body.statements) {
        val call = (stmt as? KExprStatement)?.expr?.unparen() as? KCall ?: return null
        shapes += parseShapeCall(call, ctx) ?: return null
    }
    return Parsed(Node.Canvas(ctx.newId(), shapes, m))
}

private fun parseShapeCall(call: KCall, ctx: ParseCtx): Node? {
    val cs = callShape(call) ?: return null
    if (cs.trailingLambda != null || cs.positional.size != 1) return null
    val color = colorValue(cs.positional[0]) ?: return null
    return when (cs.name) {
        "drawLine" -> {
            if (!cs.named.keys.all { it in setOf("start", "end", "strokeWidth") }) return null
            val (x1, y1) = dpPxPair(cs.named["start"], "Offset") ?: return null
            val (x2, y2) = dpPxPair(cs.named["end"], "Offset") ?: return null
            val sw = dpPxValue(cs.named["strokeWidth"]) ?: return null
            Node.Line(ctx.newId(), x1, y1, x2, y2, color, sw)
        }
        "drawRect", "drawRoundRect" -> {
            if (!cs.named.keys.all { it in setOf("topLeft", "size", "cornerRadius", "style") }) return null
            if (cs.name == "drawRect" && "cornerRadius" in cs.named.keys) return null
            val (x, y) = dpPxPair(cs.named["topLeft"], "Offset") ?: return null
            val (w, h) = dpPxPair(cs.named["size"], "Size") ?: return null
            val corner = cs.named["cornerRadius"]?.let { cr ->
                val c = cr.unparen() as? KCall ?: return null
                if (callName(c) != "CornerRadius") return null
                dpPxValue(c.singlePositionalArg()) ?: return null
            } ?: 0
            if (cs.name == "drawRoundRect" && corner <= 0) return null
            val sw = cs.named["style"]?.let { strokeWidthOf(it) ?: return null }
            Node.RectShape(ctx.newId(), x, y, w, h, color, filled = sw == null, strokeWidth = sw ?: 2, corner = corner)
        }
        "drawCircle" -> {
            if (!cs.named.keys.all { it in setOf("radius", "center", "style") }) return null
            val r = dpPxValue(cs.named["radius"]) ?: return null
            val (cx, cy) = dpPxPair(cs.named["center"], "Offset") ?: return null
            val sw = cs.named["style"]?.let { strokeWidthOf(it) ?: return null }
            Node.CircleShape(ctx.newId(), cx, cy, r, color, filled = sw == null, strokeWidth = sw ?: 2)
        }
        "drawOval" -> {
            if (!cs.named.keys.all { it in setOf("topLeft", "size", "style") }) return null
            val (x, y) = dpPxPair(cs.named["topLeft"], "Offset") ?: return null
            val (w, h) = dpPxPair(cs.named["size"], "Size") ?: return null
            val sw = cs.named["style"]?.let { strokeWidthOf(it) ?: return null }
            Node.EllipseShape(ctx.newId(), x, y, w, h, color, filled = sw == null, strokeWidth = sw ?: 2)
        }
        "drawArc" -> {
            if (!cs.named.keys.all { it in setOf("startAngle", "sweepAngle", "useCenter", "topLeft", "size", "style") }) return null
            val start = intFromFloat(cs.named["startAngle"]) ?: return null
            val sweep = intFromFloat(cs.named["sweepAngle"]) ?: return null
            val useCenter = boolLit(cs.named["useCenter"]) ?: return null
            val (x, y) = dpPxPair(cs.named["topLeft"], "Offset") ?: return null
            val (w, h) = dpPxPair(cs.named["size"], "Size") ?: return null
            val sw = cs.named["style"]?.let { strokeWidthOf(it) ?: return null }
            // Canonical: filled arcs use the center (pie) and carry no style.
            if ((sw == null) != useCenter) return null
            Node.ArcShape(ctx.newId(), x, y, w, h, start, sweep, color, filled = useCenter, strokeWidth = sw ?: 2)
        }
        else -> null
    }
}

/** `Icons.Default.X` → [IconKind.X]. */
private fun iconKind(expr: KExpr): IconKind? {
    val outer = expr.unparen().asDot() ?: return null
    val name = nameOf(outer.selector) ?: return null
    val inner = outer.receiver.unparen().asDot() ?: return null
    if (nameOf(inner.receiver) != "Icons" || nameOf(inner.selector) != "Default") return null
    return runCatching { IconKind.valueOf(name) }.getOrNull()
}
