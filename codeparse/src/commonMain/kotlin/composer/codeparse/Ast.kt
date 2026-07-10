package composer.codeparse

/**
 * Neutral syntax tree for the PSI-free parser front-end. Shapes deliberately
 * mirror the Kotlin PSI nodes the mapping layer used to consume (a `KDot`'s
 * selector is a name or call, exactly like `KtDotQualifiedExpression`), so the
 * component/modifier mapping tables transcribe 1:1. Every node carries a
 * source `range` with PSI `textRange` semantics (start until end, exclusive).
 *
 * The tree is a lossy subset by design: anything outside the recognized
 * grammar parses to [KUnknownStatement] with a correct extent, which the
 * mapping layer turns into a verbatim RawCode node — never a hard failure.
 */
internal sealed interface KExpr {
    val range: IntRange
}

internal class KName(val name: String, override val range: IntRange) : KExpr

/** `receiver.selector` / `receiver?.selector`; selector is a [KName] or [KCall]. */
internal class KDot(
    val receiver: KExpr,
    val selector: KExpr?,
    val safe: Boolean,
    override val range: IntRange,
) : KExpr

internal class KArg(val name: String?, val isSpread: Boolean, val expr: KExpr)

/** A call: parenthesized [args] plus [trailingLambdas] after the arg list. */
internal class KCall(
    val callee: KExpr?,
    val hasTypeArgs: Boolean,
    val args: List<KArg>,
    val trailingLambdas: List<KLambda>,
    override val range: IntRange,
) : KExpr

/** `{ a, b -> … }`; a destructuring parameter contributes a null name. */
internal class KLambda(
    val params: List<String?>,
    val body: KBlock,
    override val range: IntRange,
) : KExpr

internal class KParen(val inner: KExpr?, override val range: IntRange) : KExpr

internal class KPrefix(val op: String, val base: KExpr?, override val range: IntRange) : KExpr

internal class KBinary(
    val left: KExpr?,
    val op: String,
    val right: KExpr?,
    override val range: IntRange,
) : KExpr

/** Numbers (raw text, suffixes/underscores preserved), `true`/`false`/`null`, chars. */
internal class KConst(val text: String, override val range: IntRange) : KExpr

internal sealed interface KStringEntry {
    class Literal(val text: String) : KStringEntry

    /** [unescaped] is null for an invalid escape (mirrors a PSI error element). */
    class Escape(val unescaped: String?) : KStringEntry

    /** `$name` or `${…}` — makes the string a non-literal. */
    object Interpolation : KStringEntry
}

internal class KString(
    val isRaw: Boolean,
    val entries: List<KStringEntry>,
    override val range: IntRange,
) : KExpr

// ---- statements ------------------------------------------------------------

internal sealed interface KStatement {
    /** Trimmed to the first/last significant token (PSI statement semantics). */
    val range: IntRange

    /** The statement's tokens, nested constructs included (identifier-use scans). */
    val tokens: List<Token>
}

internal class KExprStatement(
    val expr: KExpr,
    override val range: IntRange,
    override val tokens: List<Token>,
) : KStatement

/** `val`/`var` declaration; `by` delegate and `=` initializer are mutually exclusive. */
internal class KPropertyStatement(
    val isVar: Boolean,
    val name: String?,
    val hasReceiver: Boolean,
    val hasType: Boolean,
    val delegate: KExpr?,
    val initializer: KExpr?,
    override val range: IntRange,
    override val tokens: List<Token>,
) : KStatement

/** Anything the subset grammar can't model — verbatim capture with a correct extent. */
internal class KUnknownStatement(
    override val range: IntRange,
    override val tokens: List<Token>,
) : KStatement

/** [bodyRange] is the region inside the braces (after `->` for lambdas), braces excluded. */
internal class KBlock(val statements: List<KStatement>, val bodyRange: IntRange)

// ---- file level --------------------------------------------------------------

/** A comment with its verbatim text (`//…` / `/*…*/`), line terminator excluded. */
internal class KComment(val text: String, val range: IntRange)

internal class KImport(
    val fqName: String,
    val alias: String?,
    val isAllUnder: Boolean,
    /** End offset of the directive (last path/alias token) — import insertion point. */
    val endOffset: Int,
) {
    /** PSI `ImportPath.pathStr` compatible: star imports as `"pkg.*"`. */
    val pathStr: String get() = if (isAllUnder) "$fqName.*" else fqName
}

internal sealed interface KDeclaration {
    val range: IntRange
}

internal class KFunctionDecl(
    val name: String?,
    /** Annotation short names (last segment of the dotted name), targets stripped. */
    val annotationNames: List<String>,
    val hasReceiver: Boolean,
    val hasTypeParams: Boolean,
    val hasReturnType: Boolean,
    /** Verbatim parameter-list text, parens included. */
    val paramListText: String,
    /** Null for expression-body / bodyless functions. */
    val bodyBlock: KBlock?,
    /** Whole declaration: KDoc/annotations through the closing brace (PSI compatible). */
    override val range: IntRange,
) : KDeclaration

/** A skipped top-level declaration (class/object/property/…) — extent only. */
internal class KOtherDecl(
    override val range: IntRange,
    /** `val X = light|darkColorScheme(…)` — codegen's own theme block, not user code. */
    val themeArtifact: Boolean = false,
) : KDeclaration

internal class KSourceFile(
    val text: String,
    /** End offset of the package directive's dotted name, or null when absent. */
    val packageEndOffset: Int?,
    val imports: List<KImport>,
    val declarations: List<KDeclaration>,
    /** All comments in the file, sorted by offset. */
    val comments: List<KComment>,
)
