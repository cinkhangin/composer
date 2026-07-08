package composer.codeparse

import composer.model.BoxAlignment
import composer.model.ButtonVariant
import composer.model.ChipVariant
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

    private val SYMBOLS = listOf("shopping_cart", "favorite", "home", "arrow_back", "person", "rocket_launch")

    private fun color(): Long =
        if (rnd.nextInt(4) == 0) ThemeColorRef.token(listOf("primary", "onPrimary", "secondary", "background").random(rnd))!!
        else rnd.nextLong(0, 0x1_0000_0000)

    private fun corner(): Pair<Int, CornerUnit> =
        if (rnd.nextBoolean()) rnd.nextInt(0, 33) to CornerUnit.Dp else rnd.nextInt(0, 51) to CornerUnit.Percent

    private val vAligns = VAlignment.entries.map { ModifierSpec.Align(vertical = it) }
    private val hAligns = HAlignment.entries.map { ModifierSpec.Align(horizontal = it) }
    private val boxAligns = BoxAlignment.entries.map { ModifierSpec.Align(box = it) }

    fun modifiers(weightScope: Boolean, aligns: List<ModifierSpec.Align> = emptyList()): List<ModifierSpec> {
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
            // duplicate modifier in one chain (e.g. stacked shadows) — must round-trip
            if (rnd.nextInt(8) == 0) {
                val (c, u) = corner()
                add(ModifierSpec.DropShadow(rnd.nextInt(1, 20), color(), rnd.nextInt(-9, 10), rnd.nextInt(-9, 10), rnd.nextInt(0, 5), c, u))
            }
            if (rnd.nextInt(6) == 0) add(ModifierSpec.Rotate(rnd.nextInt(-720, 721) / 2f))
            if (rnd.nextInt(6) == 0) {
                val x = rnd.nextInt(1, 31) / 10f
                add(if (rnd.nextBoolean()) ModifierSpec.Scale(x, x) else ModifierSpec.Scale(x, rnd.nextInt(1, 31) / 10f))
            }
            if (rnd.nextInt(6) == 0) add(ModifierSpec.ZIndex(rnd.nextInt(0, 9) / 2f))
            if (rnd.nextInt(7) == 0) add(ModifierSpec.Blur(rnd.nextInt(1, 17)))
            if (aligns.isNotEmpty() && rnd.nextInt(4) == 0) add(aligns.random(rnd))
            if (rnd.nextInt(4) == 0) add(
                listOf(
                    ModifierSpec.FillMaxWidth(), ModifierSpec.FillMaxHeight(), ModifierSpec.FillMaxSize(),
                    ModifierSpec.FillMaxWidth(0.5f), ModifierSpec.FillMaxHeight(0.75f), ModifierSpec.FillMaxSize(0.25f),
                ).random(rnd),
            )
        }
        return all
    }

    fun leaf(weightScope: Boolean, aligns: List<ModifierSpec.Align> = emptyList()): Node = when (rnd.nextInt(15)) {
        0 -> Node.Text(
            nid(), str(), modifiers(weightScope, aligns),
            fontSize = if (rnd.nextBoolean()) rnd.nextInt(8, 40) else 0,
            fontWeight = TextWeight.entries.random(rnd),
            fontFamily = TextFontFamily.entries.random(rnd),
            color = if (rnd.nextBoolean()) color() else null,
            lineHeight = if (rnd.nextInt(3) == 0) rnd.nextInt(10, 60) else 0,
            customFont = if (rnd.nextInt(6) == 0) "Comic Custom" else "",
            textAlign = TextAlignment.entries.random(rnd),
        )
        1 -> Node.Spacer(nid(), modifiers(weightScope, aligns))
        2 -> Node.Image(nid(), contentDescription = str(), modifier = modifiers(weightScope, aligns), url = "https://example.com/a.png?q=\"x\"&b=\$c")
        3 -> Node.Image(nid(), contentDescription = str(), placeholderColor = rnd.nextLong(0, 0x1_0000_0000), modifier = modifiers(weightScope, aligns))
        4 -> Node.Divider(nid(), modifiers(weightScope, aligns))
        // A non-empty symbol hides the IconKind in the emitted code, so it must sit
        // at the parse-side default (Favorite/Menu) to round-trip.
        5 -> if (rnd.nextBoolean()) {
            Node.Icon(nid(), IconKind.Favorite, str(), modifiers(weightScope, aligns), symbol = SYMBOLS.random(rnd))
        } else {
            Node.Icon(nid(), IconKind.entries.random(rnd), str(), modifiers(weightScope, aligns))
        }
        6 -> if (rnd.nextBoolean()) {
            Node.IconButton(nid(), IconKind.Menu, modifiers(weightScope, aligns), symbol = SYMBOLS.random(rnd))
        } else {
            Node.IconButton(nid(), IconKind.entries.random(rnd), modifiers(weightScope, aligns))
        }
        7 -> Node.TextField(nid(), value = str(), placeholder = str(), modifier = modifiers(weightScope, aligns))
        8 -> Node.Switch(nid(), rnd.nextBoolean(), modifiers(weightScope, aligns))
        9 -> Node.Checkbox(nid(), rnd.nextBoolean(), modifiers(weightScope, aligns))
        10 -> Node.RadioButton(nid(), rnd.nextBoolean(), modifiers(weightScope, aligns))
        11 -> Node.Slider(nid(), rnd.nextInt(0, 101) / 100f, modifiers(weightScope, aligns))
        12 -> Node.CircularProgress(nid(), modifiers(weightScope, aligns))
        13 -> {
            // selected is only visible in code for stateful variants — keep it false elsewhere.
            val variant = ChipVariant.entries.random(rnd)
            val stateful = variant == ChipVariant.Filter || variant == ChipVariant.Input
            Node.Chip(
                nid(), str(), variant,
                selected = stateful && rnd.nextBoolean(),
                symbol = if (rnd.nextBoolean()) SYMBOLS.random(rnd) else "",
                modifier = modifiers(weightScope, aligns),
            )
        }
        else -> Node.RawCode(nid(), rawStatement())
    }

    /**
     * Shape colors are LITERAL only (theme refs can't be referenced in a draw lambda),
     * and a FILLED shape's strokeWidth is invisible in code — keep it at the parse
     * default (2) so it round-trips.
     */
    private fun shape(): Node {
        val filled = rnd.nextBoolean()
        val sw = if (filled) 2 else rnd.nextInt(1, 9)
        return when (rnd.nextInt(5)) {
            0 -> Node.Line(nid(), rnd.nextInt(-20, 200), rnd.nextInt(-20, 200), rnd.nextInt(0, 220), rnd.nextInt(0, 220), rnd.nextLong(0, 0x1_0000_0000), rnd.nextInt(1, 9))
            1 -> Node.RectShape(nid(), rnd.nextInt(0, 100), rnd.nextInt(0, 100), rnd.nextInt(1, 160), rnd.nextInt(1, 160), rnd.nextLong(0, 0x1_0000_0000), filled, sw, rnd.nextInt(0, 25))
            2 -> Node.CircleShape(nid(), rnd.nextInt(0, 160), rnd.nextInt(0, 160), rnd.nextInt(1, 90), rnd.nextLong(0, 0x1_0000_0000), filled, sw)
            3 -> Node.EllipseShape(nid(), rnd.nextInt(0, 100), rnd.nextInt(0, 100), rnd.nextInt(1, 160), rnd.nextInt(1, 100), rnd.nextLong(0, 0x1_0000_0000), filled, sw)
            else -> Node.ArcShape(nid(), rnd.nextInt(0, 100), rnd.nextInt(0, 100), rnd.nextInt(1, 160), rnd.nextInt(1, 160), rnd.nextInt(-360, 361), rnd.nextInt(-360, 361), rnd.nextLong(0, 0x1_0000_0000), filled, sw)
        }
    }

    private fun rawStatement(): String = listOf(
        "if (loading) {\n    SpinnerWidget()\n}",
        "val cached = repository.load(idx)",
        "items.forEach { render(it) }",
        "when (state) {\n    is Ready -> ShowIt(state.value)\n    else -> Placeholder()\n}",
        "LaunchedEffect(Unit) {\n    viewModel.refresh()\n}",
    ).random(rnd)

    fun node(depth: Int, weightScope: Boolean, aligns: List<ModifierSpec.Align> = emptyList()): Node {
        if (depth <= 0 || rnd.nextInt(3) > 0) return leaf(weightScope, aligns)
        return when (rnd.nextInt(12)) {
            0 -> Node.Column(
                nid(), children(depth, weightScope = true, aligns = hAligns),
                verticalArrangement = VArrangement.entries.random(rnd),
                horizontalAlignment = HAlignment.entries.random(rnd),
                modifier = modifiers(weightScope, aligns),
                spacing = 0,
            )
            1 -> Node.Column(nid(), children(depth, weightScope = true, aligns = hAligns), modifier = modifiers(weightScope, aligns), spacing = rnd.nextInt(1, 25))
            2 -> Node.Row(
                nid(), children(depth, weightScope = true, aligns = vAligns),
                horizontalArrangement = HArrangement.entries.random(rnd),
                verticalAlignment = VAlignment.entries.random(rnd),
                modifier = modifiers(weightScope, aligns),
            )
            3 -> Node.Box(nid(), children(depth, weightScope = false, aligns = boxAligns), BoxAlignment.entries.random(rnd), modifiers(weightScope, aligns))
            4 -> Node.Card(nid(), children(depth, weightScope = false), modifiers(weightScope, aligns))
            5 -> Node.Button(nid(), modifier = modifiers(weightScope, aligns), variant = ButtonVariant.entries.random(rnd), children = children(depth, weightScope = true, aligns = vAligns))
            6 -> Node.Fab(nid(), children(depth, weightScope = false), modifiers(weightScope, aligns))
            7 -> {
                val tabs = List(rnd.nextInt(1, 4)) { Node.Tab(nid(), str(), modifiers(false)) }
                Node.TabRow(nid(), tabs, selectedIndex = rnd.nextInt(tabs.size), modifier = modifiers(weightScope, aligns))
            }
            8 -> {
                val items = List(rnd.nextInt(2, 5)) {
                    Node.NavItem(nid(), str(), if (rnd.nextBoolean()) SYMBOLS.random(rnd) else "", modifiers(false))
                }
                Node.NavigationBar(nid(), items, selectedIndex = rnd.nextInt(items.size), modifier = modifiers(weightScope, aligns))
            }
            9 -> Node.BadgedBox(nid(), if (rnd.nextBoolean()) str() else "", children(depth, weightScope = false, aligns = boxAligns), modifiers(weightScope, aligns))
            10 -> Node.Canvas(nid(), List(rnd.nextInt(1, 5)) { shape() }, modifiers(weightScope, aligns))
            else -> if (rnd.nextBoolean()) {
                Node.Dialog(nid(), children(depth, weightScope = true, aligns = hAligns), modifiers(weightScope, aligns))
            } else {
                Node.BottomSheet(nid(), children(depth, weightScope = true, aligns = hAligns), modifiers(weightScope, aligns))
            }
        }
    }

    private fun children(depth: Int, weightScope: Boolean, aligns: List<ModifierSpec.Align> = emptyList()): List<Node> =
        List(rnd.nextInt(1, 4)) { node(depth - 1, weightScope, aligns) }

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
            modifier = if (rnd.nextBoolean()) listOf(ModifierSpec.FillMaxSize()) else emptyList(),
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
