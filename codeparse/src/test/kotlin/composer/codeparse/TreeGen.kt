package composer.codeparse

import composer.model.BoxAlignment
import composer.model.ButtonVariant
import composer.model.CornerUnit
import composer.model.GradientDirection
import composer.model.HAlignment
import composer.model.HArrangement
import composer.model.IconKind
import composer.model.ModifierSpec
import composer.model.Node
import composer.model.PaddingMode
import composer.model.TextAlignment
import composer.model.TextFontFamily
import composer.model.TextWeight
import composer.model.ThemeColorRef
import composer.model.TopAppBarVariant
import composer.model.VAlignment
import composer.model.VArrangement
import kotlin.random.Random

/**
 * Seeded random design trees over the FULL supported subset — every Node and
 * ModifierSpec variant, adversarial strings, nesting, slots, instances, RawCode.
 * Constrained to what codegen can emit faithfully (the documented emission rules):
 * weight only in Row/Column-scope children, spacing>0 suppresses the arrangement
 * enum, percent corners ≤ 50, registered components only, single-statement
 * RawCode with unrecognizable call names.
 */
internal class TreeGen(seed: Int) {
    private val rnd = Random(seed)
    private var id = 0
    private fun nid() = "g${++id}"

    private val strings = listOf(
        "Hello", "Price: \$5", "quote \" backslash \\", "line\nbreak", "tab\there",
        "template \${x}", "", "ünïcødé ✓",
    )

    private fun str() = strings.random(rnd)

    private fun color(): Long =
        if (rnd.nextInt(4) == 0) ThemeColorRef.token(listOf("primary", "onPrimary", "secondary", "background").random(rnd))!!
        else rnd.nextLong(0, 0x1_0000_0000)

    private fun corner(): Pair<Int, CornerUnit> =
        if (rnd.nextBoolean()) rnd.nextInt(0, 33) to CornerUnit.Dp else rnd.nextInt(0, 51) to CornerUnit.Percent

    fun modifiers(weightScope: Boolean): List<ModifierSpec> {
        val all = buildList<ModifierSpec> {
            if (rnd.nextBoolean()) add(
                when (rnd.nextInt(3)) {
                    0 -> ModifierSpec.Padding(all = rnd.nextInt(0, 33), mode = PaddingMode.All)
                    1 -> ModifierSpec.Padding(horizontal = rnd.nextInt(0, 33), vertical = rnd.nextInt(0, 33), mode = PaddingMode.Symmetric)
                    else -> ModifierSpec.Padding(start = rnd.nextInt(0, 9), top = rnd.nextInt(0, 9), end = rnd.nextInt(0, 9), bottom = rnd.nextInt(0, 9), mode = PaddingMode.Sides)
                },
            )
            if (rnd.nextInt(3) == 0) add(ModifierSpec.Size(rnd.nextInt(1, 200), rnd.nextInt(1, 200)))
            if (rnd.nextInt(4) == 0) add(ModifierSpec.Width(rnd.nextInt(1, 200)))
            if (rnd.nextInt(4) == 0) add(ModifierSpec.Height(rnd.nextInt(1, 200)))
            if (rnd.nextInt(4) == 0) add(ModifierSpec.Offset(rnd.nextInt(-40, 41), rnd.nextInt(-40, 41)))
            if (rnd.nextInt(3) == 0) {
                val (c, u) = corner()
                if (rnd.nextInt(3) == 0) {
                    val stops = List(rnd.nextInt(2, 5)) { color() }
                    add(
                        ModifierSpec.Background(
                            color = stops.first(), corner = c, colors = stops,
                            direction = GradientDirection.entries.random(rnd), cornerUnit = u,
                        ),
                    )
                } else {
                    add(ModifierSpec.Background(color = color(), corner = c, cornerUnit = u))
                }
            }
            if (weightScope && rnd.nextInt(3) == 0) add(ModifierSpec.Weight(rnd.nextInt(1, 9) / 2f))
            if (rnd.nextInt(5) == 0) add(ModifierSpec.AspectRatio(rnd.nextInt(1, 22), rnd.nextInt(1, 22)))
            if (rnd.nextInt(4) == 0) { val (c, u) = corner(); add(ModifierSpec.Clip(c, u)) }
            if (rnd.nextInt(5) == 0) add(ModifierSpec.Alpha(rnd.nextInt(0, 11) / 10f))
            if (rnd.nextInt(4) == 0) { val (c, u) = corner(); add(ModifierSpec.Border(rnd.nextInt(1, 5), color(), c, u)) }
            if (rnd.nextInt(5) == 0) {
                val (c, u) = corner()
                add(ModifierSpec.DropShadow(rnd.nextInt(1, 20), color(), rnd.nextInt(-9, 10), rnd.nextInt(-9, 10), rnd.nextInt(0, 5), c, u))
            }
            if (rnd.nextInt(6) == 0) {
                val (c, u) = corner()
                add(ModifierSpec.InnerShadow(rnd.nextInt(1, 20), color(), rnd.nextInt(-9, 10), rnd.nextInt(-9, 10), rnd.nextInt(0, 5), c, u))
            }
            if (rnd.nextInt(4) == 0) add(listOf(ModifierSpec.FillMaxWidth, ModifierSpec.FillMaxHeight, ModifierSpec.FillMaxSize).random(rnd))
        }
        return all
    }

    fun leaf(weightScope: Boolean): Node = when (rnd.nextInt(14)) {
        0 -> Node.Text(
            nid(), str(), modifiers(weightScope),
            fontSize = if (rnd.nextBoolean()) rnd.nextInt(8, 40) else 0,
            fontWeight = TextWeight.entries.random(rnd),
            fontFamily = TextFontFamily.entries.random(rnd),
            color = if (rnd.nextBoolean()) color() else null,
            lineHeight = if (rnd.nextInt(3) == 0) rnd.nextInt(10, 60) else 0,
            customFont = if (rnd.nextInt(6) == 0) "Comic Custom" else "",
            textAlign = TextAlignment.entries.random(rnd),
        )
        1 -> Node.Spacer(nid(), modifiers(weightScope))
        2 -> Node.Image(nid(), contentDescription = str(), modifier = modifiers(weightScope), url = "https://example.com/a.png?q=\"x\"&b=\$c")
        3 -> Node.Image(nid(), contentDescription = str(), placeholderColor = rnd.nextLong(0, 0x1_0000_0000), modifier = modifiers(weightScope))
        4 -> Node.Divider(nid(), modifiers(weightScope))
        5 -> Node.Icon(nid(), IconKind.entries.random(rnd), str(), modifiers(weightScope))
        6 -> Node.IconButton(nid(), IconKind.entries.random(rnd), modifiers(weightScope))
        7 -> Node.TextField(nid(), value = str(), placeholder = str(), modifier = modifiers(weightScope))
        8 -> Node.Switch(nid(), rnd.nextBoolean(), modifiers(weightScope))
        9 -> Node.Checkbox(nid(), rnd.nextBoolean(), modifiers(weightScope))
        10 -> Node.RadioButton(nid(), rnd.nextBoolean(), modifiers(weightScope))
        11 -> Node.Slider(nid(), rnd.nextInt(0, 101) / 100f, modifiers(weightScope))
        12 -> Node.CircularProgress(nid(), modifiers(weightScope))
        else -> Node.RawCode(nid(), rawStatement())
    }

    private fun rawStatement(): String = listOf(
        "if (loading) {\n    SpinnerWidget()\n}",
        "val cached = repository.load(idx)",
        "items.forEach { render(it) }",
        "when (state) {\n    is Ready -> ShowIt(state.value)\n    else -> Placeholder()\n}",
        "LaunchedEffect(Unit) {\n    viewModel.refresh()\n}",
    ).random(rnd)

    fun node(depth: Int, weightScope: Boolean): Node {
        if (depth <= 0 || rnd.nextInt(3) > 0) return leaf(weightScope)
        return when (rnd.nextInt(8)) {
            0 -> Node.Column(
                nid(), children(depth, weightScope = true),
                verticalArrangement = VArrangement.entries.random(rnd),
                horizontalAlignment = HAlignment.entries.random(rnd),
                modifier = modifiers(weightScope),
                spacing = 0,
            )
            1 -> Node.Column(nid(), children(depth, weightScope = true), modifier = modifiers(weightScope), spacing = rnd.nextInt(1, 25))
            2 -> Node.Row(
                nid(), children(depth, weightScope = true),
                horizontalArrangement = HArrangement.entries.random(rnd),
                verticalAlignment = VAlignment.entries.random(rnd),
                modifier = modifiers(weightScope),
            )
            3 -> Node.Box(nid(), children(depth, weightScope = false), BoxAlignment.entries.random(rnd), modifiers(weightScope))
            4 -> Node.Card(nid(), children(depth, weightScope = false), modifiers(weightScope))
            5 -> Node.Button(nid(), modifier = modifiers(weightScope), variant = ButtonVariant.entries.random(rnd), children = children(depth, weightScope = true))
            6 -> Node.Fab(nid(), children(depth, weightScope = false), modifiers(weightScope))
            else -> if (rnd.nextBoolean()) {
                Node.Dialog(nid(), children(depth, weightScope = true), modifiers(weightScope))
            } else {
                Node.BottomSheet(nid(), children(depth, weightScope = true), modifiers(weightScope))
            }
        }
    }

    private fun children(depth: Int, weightScope: Boolean): List<Node> =
        List(rnd.nextInt(1, 4)) { node(depth - 1, weightScope) }

    fun scaffold(depth: Int): Node.Scaffold {
        val id = nid()
        return Node.Scaffold(
            id = id,
            children = children(depth, weightScope = false),
            topBar = Node.Slot(
                "$id-topBar", "topBar",
                if (rnd.nextBoolean()) {
                    listOf(
                        Node.TopAppBar(
                            nid(),
                            title = Node.Text(nid(), str()),
                            navigationIcon = if (rnd.nextBoolean()) Node.IconButton(nid(), IconKind.Menu) else null,
                            actions = if (rnd.nextBoolean()) listOf(Node.IconButton(nid(), IconKind.Search)) else emptyList(),
                            variant = TopAppBarVariant.entries.random(rnd),
                        ),
                    )
                } else emptyList(),
            ),
            bottomBar = Node.Slot("$id-bottomBar", "bottomBar", if (rnd.nextInt(3) == 0) listOf(leaf(false)) else emptyList()),
            fab = Node.Slot("$id-fab", "fab", if (rnd.nextInt(3) == 0) listOf(Node.Fab(nid(), listOf(Node.Icon(nid(), IconKind.Add)))) else emptyList()),
            modifier = if (rnd.nextBoolean()) listOf(ModifierSpec.FillMaxSize) else emptyList(),
        )
    }

    /** A whole design: 1–3 screens; screen 1 may be instantiated by later screens. */
    fun design(): Node.Artboard {
        val screens = mutableListOf<Node.Composable>()
        val names = mutableMapOf<String, String>()
        val referenced = mutableSetOf<String>()
        val first = Node.Composable(id = nid(), children = children(2, weightScope = false), x = 0, y = 0)
        screens += first
        names[first.id] = "CardWidget"
        repeat(rnd.nextInt(0, 3)) { i ->
            val kids = children(2, weightScope = false).toMutableList()
            if (rnd.nextBoolean()) {
                kids += Node.Instance(nid(), first.id)
                referenced += first.id
            }
            if (rnd.nextBoolean()) {
                kids += Node.Instance(nid(), first.id, modifiers(false).ifEmpty { listOf(ModifierSpec.Padding(all = 4, mode = PaddingMode.All)) })
                referenced += first.id
            }
            if (rnd.nextInt(3) == 0) kids += scaffold(2)
            val s = Node.Composable(id = nid(), children = kids, x = (i + 1) * 470, y = 0)
            screens += s
            names[s.id] = "Screen${i + 2}"
        }
        return Node.Artboard(
            id = "artboard",
            composables = screens,
            layerNames = names,
            componentIds = referenced.toList(),
        )
    }
}
