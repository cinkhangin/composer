package composer.codeparse

import composer.codegen.AppCodeGen
import composer.model.ComposablePreview
import composer.model.DesignTheme
import composer.model.NamedTheme
import composer.model.NavAction
import composer.model.Node
import composer.model.childNodes
import composer.model.replaceById
import composer.model.withNavAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppWriteBackTest {

    private fun fixtureTree(): Node.Artboard = Node.Artboard(
        id = "a",
        composables = listOf(
            Node.Composable(
                "s1",
                children = listOf(
                    Node.Text("t1", "Welcome"),
                    Node.Button("b1", children = listOf(Node.Text("bt", "Go")), navAction = NavAction.Navigate("s2")),
                ),
                preview = ComposablePreview(),
            ),
            Node.Composable(
                "s2",
                children = listOf(Node.Text("t2", "Home sweet home")),
                preview = ComposablePreview(),
            ),
        ),
        layerNames = mapOf("s1" to "Login", "s2" to "Home"),
    )

    private class Fixture(val parsed: ParsedApp, val files: MutableMap<String, String>)

    private fun fixture(): Fixture {
        val files = AppCodeGen.generate(fixtureTree(), "com.example.app").map { SourceFile(it.path, it.text) }
        val parsed = AppParser.parse(files)!!
        return Fixture(parsed, files.associate { it.path to it.text }.toMutableMap())
    }

    private fun Node.findFirst(predicate: (Node) -> Boolean): Node? {
        if (predicate(this)) return this
        for (c in childNodes()) c.findFirst(predicate)?.let { return it }
        return null
    }

    @Test
    fun unchanged_design_plans_nothing() {
        val f = fixture()
        val plan = AppWriteBackPlanner.plan(f.parsed, f.files, f.parsed.artboard)
        assertEquals(emptyList(), plan.files)
        assertEquals(emptyList(), plan.warnings)
    }

    @Test
    fun adding_a_custom_theme_creates_a_dedicated_theme_file() {
        val f = fixture()
        val edited = f.parsed.artboard.copy(
            themes = listOf(NamedTheme("Brand", DesignTheme(primary = 0xFF123456))),
        )

        val plan = AppWriteBackPlanner.plan(f.parsed, f.files, edited)

        val theme = plan.files.single { it.path == "AppTheme.kt" }
        assertTrue(theme.createText?.contains("fun AppTheme(") == true)
        assertEquals(1, Regex("(?m)^@Composable\\s*$").findAll(theme.createText.orEmpty()).count())
        assertTrue(plan.files.any { it.path == "MainActivity.kt" && it.edits.isNotEmpty() })
    }

    @Test
    fun editing_one_node_touches_only_its_ui_file() {
        val f = fixture()
        val text = f.parsed.artboard.findFirst { it is Node.Text && (it as Node.Text).text == "Home sweet home" }!!
        val edited = f.parsed.artboard.replaceById(text.id) { (it as Node.Text).copy(text = "Changed") } as Node.Artboard
        val plan = AppWriteBackPlanner.plan(f.parsed, f.files, edited)
        assertEquals(listOf("HomeScreenUI.kt"), plan.files.map { it.path })
        assertTrue(plan.files.single().edits.isNotEmpty())
        val applied = WriteBackPlanner.apply(f.files.getValue("HomeScreenUI.kt"), WriteBackPlan(plan.files.single().edits))
        assertTrue("Changed" in applied)
    }

    @Test
    fun adding_a_nav_action_updates_ui_wiring_and_main_activity() {
        val f = fixture()
        val homeText = f.parsed.artboard.findFirst { it is Node.Text && (it as Node.Text).text == "Home sweet home" }!!
        // Wrap: give Home's screen a Fab with Back by replacing the screen's children.
        val home = f.parsed.artboard.composables[1] as Node.Composable
        val edited = f.parsed.artboard.replaceById(home.id) {
            (it as Node.Composable).copy(children = it.children + Node.Fab("newfab", navAction = NavAction.Back))
        } as Node.Artboard
        val plan = AppWriteBackPlanner.plan(f.parsed, f.files, edited)
        assertEquals(
            setOf("HomeScreenUI.kt", "HomeScreen.kt", "MainActivity.kt"),
            plan.files.map { it.path }.toSet(),
        )
        // The wiring file is canonical → whole-file replace including onBack.
        val wiring = plan.files.first { it.path == "HomeScreen.kt" }
        assertTrue(wiring.edits.single().replacement.contains("onBack: () -> Unit = {}"))
        val main = plan.files.first { it.path == "MainActivity.kt" }
        assertTrue(main.edits.single().replacement.contains("onBack = { backStack.removeLastOrNull() }"))
        assertTrue(plan.files.none { it.path == "HomeViewModel.kt" })
    }

    @Test
    fun hand_edited_view_model_is_preserved_forever() {
        val f = fixture()
        // User customizes the ViewModel.
        f.files["HomeViewModel.kt"] = f.files.getValue("HomeViewModel.kt") + "\n// my note\n"
        val reparsed = AppParser.parse(f.files.map { SourceFile(it.key, it.value) })!!
        // An unrelated edit: never touches the ViewModel, no warning.
        val text = reparsed.artboard.findFirst { it is Node.Text && (it as Node.Text).text == "Home sweet home" }!!
        val edited = reparsed.artboard.replaceById(text.id) { (it as Node.Text).copy(text = "X") } as Node.Artboard
        val plan = AppWriteBackPlanner.plan(reparsed, f.files, edited)
        assertTrue(plan.files.none { it.path == "HomeViewModel.kt" })
        assertEquals(emptyList(), plan.warnings)
    }

    @Test
    fun hand_edited_main_activity_warns_when_nav_changes() {
        val f = fixture()
        f.files["MainActivity.kt"] = f.files.getValue("MainActivity.kt").replace("fun App()", "fun App() // customized")
        val reparsed = AppParser.parse(f.files.map { SourceFile(it.key, it.value) })!!
        val home = reparsed.artboard.composables[1] as Node.Composable
        val edited = reparsed.artboard.replaceById(home.id) {
            (it as Node.Composable).copy(children = it.children + Node.Fab("newfab", navAction = NavAction.Back))
        } as Node.Artboard
        val plan = AppWriteBackPlanner.plan(reparsed, f.files, edited)
        assertTrue(plan.files.none { it.path == "MainActivity.kt" })
        assertTrue(plan.warnings.any { "MainActivity.kt" in it }, plan.warnings.toString())
    }

    @Test
    fun rename_is_blocked_when_cross_file_symbols_are_hand_edited() {
        val f = fixture()
        f.files["MainActivity.kt"] = f.files.getValue("MainActivity.kt") + "\n// custom routing\n"
        val reparsed = AppParser.parse(f.files.map { SourceFile(it.key, it.value) })!!
        val home = reparsed.artboard.composables[1] as Node.Composable
        val edited = reparsed.artboard.copy(
            layerNames = reparsed.artboard.layerNames + (home.id to "Dashboard"),
        )
        val plan = AppWriteBackPlanner.plan(reparsed, f.files, edited)
        assertTrue(plan.files.isEmpty())
        assertTrue(plan.blockedReason?.contains("MainActivity.kt") == true)
    }

    @Test
    fun renaming_a_screen_moves_its_trio_and_updates_main() {
        val f = fixture()
        val home = f.parsed.artboard.composables[1] as Node.Composable
        val edited = f.parsed.artboard.copy(
            layerNames = f.parsed.artboard.layerNames + (home.id to "Dashboard"),
        )
        val plan = AppWriteBackPlanner.plan(f.parsed, f.files, edited)
        val moves = plan.files.filter { it.moveFrom != null }.associate { it.moveFrom!! to it.path }
        assertEquals(
            mapOf(
                "HomeScreenUI.kt" to "DashboardScreenUI.kt",
                "HomeScreen.kt" to "DashboardScreen.kt",
                "HomeViewModel.kt" to "DashboardViewModel.kt",
            ),
            moves,
        )
        assertTrue(plan.files.none { it.delete })
        // Login navigates to the renamed screen — its UI regenerates, and MainActivity re-routes.
        assertTrue(plan.files.any { it.path == "LoginScreenUI.kt" && it.edits.isNotEmpty() })
        val main = plan.files.first { it.path == "MainActivity.kt" }
        assertTrue(main.edits.single().replacement.contains("data object Dashboard : NavKey"))
        assertEquals(listOf(ScreenRename("Home", "Dashboard")), plan.renames)

        // Simulate the host's moves and edits. The old Home UI must not remain
        // beside Dashboard or AppParser would append it as an orphan screen.
        for (filePlan in plan.files) {
            when {
                filePlan.moveFrom != null -> {
                    f.files.remove(filePlan.moveFrom)
                    f.files[filePlan.path] = filePlan.createText!!
                }
                filePlan.createText != null -> f.files[filePlan.path] = filePlan.createText
                filePlan.edits.isNotEmpty() -> {
                    f.files[filePlan.path] = WriteBackPlanner.apply(
                        f.files.getValue(filePlan.path),
                        WriteBackPlan(filePlan.edits),
                    )
                }
            }
        }
        val reparsed = AppParser.parse(f.files.map { SourceFile(it.key, it.value) })!!
        assertEquals(listOf("Login", "Dashboard"), reparsed.artboard.composables.map { reparsed.artboard.layerNames[it.id] })
    }

    @Test
    fun adding_and_deleting_screens() {
        val f = fixture()
        // Add a screen.
        val added = f.parsed.artboard.copy(
            composables = f.parsed.artboard.composables + Node.Composable("new1", children = listOf(Node.Text("nt", "hi"))),
            layerNames = f.parsed.artboard.layerNames + ("new1" to "Settings"),
        )
        val addPlan = AppWriteBackPlanner.plan(f.parsed, f.files, added)
        val creates = addPlan.files.filter { it.createText != null }.map { it.path }.toSet()
        assertEquals(setOf("SettingsScreenUI.kt", "SettingsScreen.kt", "SettingsViewModel.kt"), creates)
        assertTrue(addPlan.files.any { it.path == "MainActivity.kt" && it.edits.isNotEmpty() })

        // Delete a screen.
        val removed = f.parsed.artboard.copy(
            composables = f.parsed.artboard.composables.filterNot { it.id == "s2" },
        )
        val delPlan = AppWriteBackPlanner.plan(f.parsed, f.files, removed)
        val deletes = delPlan.files.filter { it.delete }.map { it.path }.toSet()
        assertEquals(setOf("HomeScreenUI.kt", "HomeScreen.kt", "HomeViewModel.kt"), deletes)
        assertTrue(delPlan.files.any { it.path == "MainActivity.kt" && it.edits.isNotEmpty() })
        // Login's nav target vanished — its UI regenerates without the callback.
        assertTrue(delPlan.files.any { it.path == "LoginScreenUI.kt" && it.edits.isNotEmpty() })
    }
}
