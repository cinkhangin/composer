package composer.codeparse

import composer.codegen.AppCodeGen
import composer.codegen.CodeGen
import composer.model.DesignTheme
import composer.model.NamedTheme
import composer.model.Node
import composer.model.migrateToArtboard
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The app-mode correctness anchor, mirroring RoundTripTest: for the whole
 * supported subset, AppParser is the exact inverse of AppCodeGen — canon
 * equality on the tree, byte-identical per-file regeneration, and every
 * derived (wiring/VM/MainActivity) file recognized as canonical.
 */
class AppRoundTripTest {

    @Test
    fun adoptable_main_activity_requires_a_real_nav_display_call() {
        assertTrue(
            AppParser.isAdoptableMainActivity(
                """
                import androidx.activity.ComponentActivity
                class MainActivity : ComponentActivity() {
                    fun Content() { NavDisplay(backStack = stack, entryProvider = provider) }
                }
                """.trimIndent(),
            ),
        )
        assertTrue(
            !AppParser.isAdoptableMainActivity(
                """
                import androidx.activity.ComponentActivity
                import androidx.navigation3.ui.NavDisplay
                class MainActivity : ComponentActivity() {
                    // NavDisplay(fake)
                    val note = "NavDisplay(fake)"
                }
                """.trimIndent(),
            ),
        )
        assertTrue(!AppParser.isAdoptableMainActivity("fun NavDisplay() = Unit"))
    }

    /** Roughly half the seeds get customized themes (names may need sanitizing). */
    private fun Node.Artboard.withAppThemes(seed: Int): Node.Artboard {
        val rnd = Random(seed + 1000)
        if (rnd.nextBoolean()) return this
        val names = listOf("Brand", "Dark Mode", "high contrast!", "Ocean")
        val themes = (1..rnd.nextInt(1, 3)).map {
            var theme = DesignTheme(dark = rnd.nextBoolean())
            repeat(rnd.nextInt(0, 3)) {
                val token = DesignTheme.TOKENS[rnd.nextInt(DesignTheme.TOKENS.size)]
                theme = theme.set(token, rnd.nextLong(0, 0x1_0000_0000L))
            }
            NamedTheme(names[rnd.nextInt(names.size)], theme)
        }
        return copy(themes = themes, activeTheme = rnd.nextInt(themes.size))
    }

    /** Theme comparison key: the lossy-normalized form both sides can reproduce. */
    private fun themeKey(root: Node): Pair<List<Triple<String, Boolean, List<Pair<String, Long>>>>, Int> {
        val a = root.migrateToArtboard()
        val themed = a.themes.size > 1 || a.themes.any { it.theme.isCustomized() }
        if (!themed) return emptyList<Triple<String, Boolean, List<Pair<String, Long>>>>() to 0
        // Names normalize through the emitted scheme-val names (dedupe included).
        val vals = AppCodeGen.appNamePlan(a).schemeVals
        val list = a.themes.mapIndexed { i, named ->
            Triple(vals[i], named.theme.dark, named.theme.changedTokens())
        }
        return list to a.activeTheme.coerceIn(0, (a.themes.size - 1).coerceAtLeast(0))
    }

    @Test
    fun app_generation_round_trips_across_random_trees() {
        for (seed in 1..150) {
            val tree = TreeGen(seed).design().withAppThemes(seed)
            val files = AppCodeGen.generate(tree, "com.example.app").map { SourceFile(it.path, it.text) }
            val parsed = AppParser.parse(files)
            assertNotNull(parsed, "seed $seed produced an unparseable app")
            assertEquals(canon(tree), canon(parsed.artboard), "seed $seed tree mismatch")
            assertEquals(themeKey(tree), themeKey(parsed.artboard), "seed $seed theme mismatch")
            assertEquals("com.example.app", parsed.packageName, "seed $seed package")

            // Byte-identical regeneration of the whole file set.
            val regen = AppCodeGen.generate(parsed.artboard, parsed.packageName)
            assertEquals(files.map { it.path }, regen.map { it.path }, "seed $seed file set drift")
            files.zip(regen).forEach { (orig, re) ->
                assertEquals(orig.text, re.text, "seed $seed drift in ${orig.path}")
            }

            // Every derived file must recognize itself as canonical (the no-op anchor).
            for (f in parsed.files) {
                when (f.role) {
                    AppFileRole.ScreenUi -> assertNotNull(f.design, "seed $seed ${f.path} bookkeeping")
                    else -> assertTrue(f.canonical, "seed $seed ${f.path} not canonical")
                }
            }
        }
    }

    @Test
    fun reparse_of_unchanged_files_is_stable() {
        val tree = TreeGen(7).design().withAppThemes(7)
        val files = AppCodeGen.generate(tree, "com.example.app").map { SourceFile(it.path, it.text) }
        val a = AppParser.parse(files)!!
        val b = AppParser.parse(files)!!
        assertEquals(a.artboard, b.artboard)
    }
}
