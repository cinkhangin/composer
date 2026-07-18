package composer.codeparse

import composer.model.Node
import composer.model.replaceById
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModuleDesignParserTest {
    private val first = SourceFile(
        "/module/src/main/kotlin/demo/Home.kt",
        """
        package demo
        import androidx.compose.material3.Text
        import androidx.compose.runtime.Composable

        @Composable
        fun Home() {
            Text("Home")
            SharedCard()
        }

        @Preview
        @Composable
        fun HomePreview() { Home() }

        class Holder {
            @Composable fun MemberOnly() { Text("member") }
        }
        """.trimIndent(),
    )
    private val second = SourceFile(
        "/module/src/main/kotlin/demo/Shared.kt",
        """
        package demo
        import androidx.compose.material3.Text
        import androidx.compose.runtime.Composable

        @Composable
        fun SharedCard() { Text("Card") }

        @Composable
        fun WithParameter(label: String) { Text(label) }
        """.trimIndent(),
    )

    @Test
    fun renders_every_parseable_top_level_composable_across_files() {
        val parsed = assertNotNull(ModuleDesignParser.parse(listOf(second, first)))
        assertEquals(listOf("Home", "SharedCard", "WithParameter"), parsed.artboard.composables.map {
            parsed.artboard.layerNames[it.id]
        })
        assertTrue(parsed.artboard.layerNames.values.none { it == "HomePreview" })
        assertTrue(parsed.artboard.layerNames.values.none { it == "MemberOnly" })
        val home = parsed.artboard.composables.first() as Node.Composable
        assertTrue(home.children.any { it is Node.Instance })
        assertEquals(listOf(0, 470, 940), parsed.artboard.composables.map { (it as Node.Composable).x })
    }

    @Test
    fun parameterized_cross_file_calls_render_as_source_preserving_instances() {
        val caller = SourceFile(
            "/module/Caller.kt",
            """
            @Composable
            fun Home() {
                SharedCard(
                    title = "Welcome",
                    onClick = { println("clicked") },
                )
            }
            """.trimIndent(),
        )
        val component = SourceFile(
            "/module/Component.kt",
            """
            @Composable
            fun SharedCard(title: String, onClick: () -> Unit) {
                Text(title)
            }
            """.trimIndent(),
        )

        val parsed = assertNotNull(ModuleDesignParser.parse(listOf(caller, component)))
        val home = parsed.artboard.composables.first {
            parsed.artboard.layerNames[it.id] == "Home"
        } as Node.Composable
        val instance = home.children.single() as Node.Instance
        assertTrue("title = \"Welcome\"" in instance.sourceArguments)
        assertTrue("onClick = { println(\"clicked\") }" in instance.sourceArguments)
        assertEquals(
            "SharedCard",
            parsed.functionNamesById.getValue(instance.refId),
        )
    }

    @Test
    fun edits_only_the_owning_file_and_preserves_parameterized_raw_code() {
        val parsed = assertNotNull(ModuleDesignParser.parse(listOf(first, second)))
        val card = parsed.artboard.composables.first { parsed.artboard.layerNames[it.id] == "SharedCard" } as Node.Composable
        val text = card.children.single() as Node.Text
        val edited = parsed.artboard.replaceById(text.id) { (it as Node.Text).copy(text = "Changed") } as Node.Artboard
        val files = listOf(first, second).associate { it.path to it.text }
        val plan = ModuleWriteBackPlanner.plan(parsed, files, edited)
        assertEquals(listOf(second.path), plan.files.map { it.path })
        val result = WriteBackPlanner.apply(second.text, WriteBackPlan(plan.files.single().edits))
        assertTrue("Text(\"Changed\")" in result)
        assertTrue("fun WithParameter(label: String) { Text(label) }" in result)
    }

    @Test
    fun structural_changes_are_blocked_until_annotations_exist() {
        val parsed = assertNotNull(ModuleDesignParser.parse(listOf(first, second)))
        val removed = parsed.artboard.copy(composables = parsed.artboard.composables.dropLast(1))
        val plan = ModuleWriteBackPlanner.plan(parsed, mapOf(first.path to first.text, second.path to second.text), removed)
        assertNotNull(plan.blockedReason)
        assertTrue(plan.files.isEmpty())
    }

    @Test
    fun overloads_render_with_distinct_ids() {
        val file = SourceFile(
            "/module/Overloads.kt",
            """
            @Composable fun Chip() { Text("one") }
            @Composable fun Chip(label: String) { Text(label) }
            """.trimIndent(),
        )
        val parsed = assertNotNull(ModuleDesignParser.parse(listOf(file)))
        assertEquals(2, parsed.artboard.composables.size)
        assertEquals(2, parsed.artboard.composables.map { it.id }.toSet().size)
        assertTrue(parsed.warnings.single().contains("Chip"))
    }

    @Test
    fun renders_android_studio_greeting_and_excludes_its_preview() {
        val file = SourceFile(
            "/module/src/main/kotlin/demo/MainActivity.kt",
            """
            import androidx.compose.material3.Text
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.tooling.preview.Preview

            @Composable
            fun Greeting(name: String, modifier: Modifier = Modifier) {
                Text(
                    text = "Hello ${'$'}name!",
                    modifier = modifier
                )
            }

            @Preview(showBackground = true)
            @Composable
            fun GreetingPreview() {
                Greeting("Android")
            }
            """.trimIndent(),
        )

        val parsed = assertNotNull(ModuleDesignParser.parse(listOf(file)))
        assertEquals(listOf("Greeting"), parsed.artboard.composables.map {
            parsed.artboard.layerNames[it.id]
        })
        val greeting = parsed.artboard.composables.single() as Node.Composable
        val text = greeting.children.single() as Node.Text
        assertEquals("\"Hello ${'$'}name!\"", text.textExpression)
    }

    @Test
    fun skips_rawcode_only_screens_but_preserves_rawcode_in_mixed_screens() {
        val ui = SourceFile(
            "/module/src/main/kotlin/demo/Screens.kt",
            """
            @Composable
            fun Mixed() {
                CustomSideEffect()
                Text("Visible")
            }
            """.trimIndent(),
        )
        val theme = SourceFile(
            "/module/src/main/kotlin/demo/Theme.kt",
            """
            @Composable
            fun ComposeTheme(content: @Composable () -> Unit) {
                MaterialTheme(content = content)
            }
            """.trimIndent(),
        )

        val parsed = assertNotNull(ModuleDesignParser.parse(listOf(theme, ui)))
        assertEquals(listOf("Mixed"), parsed.artboard.composables.map { parsed.artboard.layerNames[it.id] })
        val mixed = parsed.artboard.composables.single() as Node.Composable
        assertTrue(mixed.children.first() is Node.RawCode)
        assertTrue(mixed.children.last() is Node.Text)
        assertEquals(listOf(ui.path), parsed.files.map { it.path })
        assertEquals(setOf(mixed.id), parsed.functionNamesById.keys)
    }

    @Test
    fun applies_theme_extracted_from_standard_module_files() {
        val screen = SourceFile(
            "/module/src/main/kotlin/demo/Screen.kt",
            """
            @Composable
            fun Greeting() { Text("Hello") }
            """.trimIndent(),
        )
        val colors = SourceFile(
            "/module/src/main/kotlin/demo/ui/theme/Color.kt",
            """
            val LightPrimary = Color(0xFF112233)
            val DarkPrimary = Color(0xFFCCDDEE)
            """.trimIndent(),
        )
        val theme = SourceFile(
            "/module/src/main/kotlin/demo/ui/theme/Theme.kt",
            """
            private val DarkColorScheme = darkColorScheme(primary = DarkPrimary)
            private val LightColorScheme = lightColorScheme(primary = LightPrimary)

            @Composable
            fun DemoTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
                val colors = if (darkTheme) DarkColorScheme else LightColorScheme
                MaterialTheme(colorScheme = colors, content = content)
            }
            """.trimIndent(),
        )

        val parsed = assertNotNull(ModuleDesignParser.parse(listOf(theme, screen, colors)))
        assertEquals(listOf("Greeting"), parsed.artboard.composables.map { parsed.artboard.layerNames[it.id] })
        assertEquals(listOf("Dark", "Light"), parsed.artboard.themes.map { it.name })
        assertEquals(0xFF112233, parsed.artboard.themes[1].theme.primary)
        assertEquals(1, parsed.artboard.activeTheme)
    }
}
