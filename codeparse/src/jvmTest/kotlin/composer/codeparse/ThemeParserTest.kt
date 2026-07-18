package composer.codeparse

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeParserTest {
    private val colors = SourceFile(
        "/app/src/main/kotlin/demo/ui/theme/Color.kt",
        """
        package demo.ui.theme
        import androidx.compose.ui.graphics.Color

        val Purple80 = Color(0xFFD0BCFF)
        val PurpleGrey80 = Color(0xFFCCC2DC)
        val Pink80 = Color(0xFFEFB8C8)
        val Purple40 = Color(0xFF6650A4)
        val PurpleGrey40 = Color(0xFF625B71)
        val Pink40 = Color(0xFF7D5260)
        """.trimIndent(),
    )

    private val theme = SourceFile(
        "/app/src/main/kotlin/demo/ui/theme/Theme.kt",
        """
        package demo.ui.theme

        private val DarkColorScheme = darkColorScheme(
            primary = Purple80,
            secondary = PurpleGrey80,
            tertiary = Pink80,
        )

        private val LightColorScheme = lightColorScheme(
            primary = Purple40,
            secondary = PurpleGrey40,
            tertiary = Pink40,
        )

        @Composable
        fun DemoTheme(
            darkTheme: Boolean = isSystemInDarkTheme(),
            dynamicColor: Boolean = true,
            content: @Composable () -> Unit,
        ) {
            val colorScheme = when {
                dynamicColor -> if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                darkTheme -> DarkColorScheme
                else -> LightColorScheme
            }
            MaterialTheme(colorScheme = colorScheme, content = content)
        }
        """.trimIndent(),
    )

    @Test
    fun extracts_standard_android_studio_theme_without_annotations() {
        val parsed = ThemeParser.parse(listOf(theme, colors))
        assertEquals(listOf("Dark", "Light"), parsed.themes.map { it.name })
        assertEquals(true, parsed.themes[0].theme.dark)
        assertEquals(0xFFD0BCFF, parsed.themes[0].theme.primary)
        assertEquals(0xFFCCC2DC, parsed.themes[0].theme.secondary)
        assertEquals(0xFFEFB8C8, parsed.themes[0].theme.tertiary)
        assertEquals(false, parsed.themes[1].theme.dark)
        assertEquals(0xFF6650A4, parsed.themes[1].theme.primary)
        assertEquals(1, parsed.active, "system-driven themes preview the static light fallback")
    }

    @Test
    fun resolves_aliases_and_builtin_compose_colors() {
        val file = SourceFile(
            "/Theme.kt",
            """
            val Brand = Color(0xFF123456)
            val Primary = Brand
            val AppColors = lightColorScheme(primary = Primary, onPrimary = Color.White)

            @Composable
            fun AppTheme(content: @Composable () -> Unit) {
                MaterialTheme(colorScheme = AppColors, content = content)
            }
            """.trimIndent(),
        )
        val parsed = ThemeParser.parse(listOf(file))
        assertEquals(0xFF123456, parsed.themes.single().theme.primary)
        assertEquals(0xFFFFFFFF, parsed.themes.single().theme.onPrimary)
    }
}
