package composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import composer.model.DesignTheme
import composer.ui.ColorField
import composer.ui.AppIconKind
import composer.ui.Field
import composer.ui.SectionHeader
import composer.ui.SquareIconButton
import composer.ui.Tk
import composer.ui.ToolButton

/**
 * Per-design Material theme editor (shown when the artboard is selected). A dark/light
 * base toggle plus a swatch grid of the editable [DesignTheme] color tokens; tapping a
 * swatch focuses it in the [ColorPicker] below. Edits flow to [EditorState.setTheme],
 * so the preview and generated `MaterialTheme { }` update live.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ThemeEditor(state: EditorState) {
    val theme = state.theme
    var active by remember { mutableStateOf(DesignTheme.TOKENS.first()) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Themes")
        // Named theme list: switch by clicking a chip; "+" duplicates the active
        // theme as a starting point. The active theme drives the preview and the
        // generated AppTheme default.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            state.themes.forEachIndexed { i, named ->
                ToolButton(named.name, primary = i == state.activeTheme) { state.setActiveTheme(i) }
            }
            ToolButton("", icon = AppIconKind.Plus) { state.addTheme() }
        }
        // Rename + delete for the active theme.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(
                value = state.themes.getOrNull(state.activeTheme)?.name ?: "",
                onValueChange = { state.renameTheme(state.activeTheme, it) },
                label = "Theme name (used in generated code)",
                modifier = Modifier.weight(1f),
            )
            if (state.themes.size > 1) {
                SquareIconButton(AppIconKind.Trash, danger = true, tip = "Delete theme") { state.deleteTheme(state.activeTheme) }
            }
        }
        BoolField("Dark base scheme", theme.dark) { state.setTheme(theme.copy(dark = it), coalesceKey = null) }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            for (token in DesignTheme.TOKENS) {
                // effective() shows what actually renders (dark builder defaults for
                // untouched tokens of a dark theme) — matches the canvas.
                ThemeSwatch(token, theme.effective(token), selected = token == active) { active = token }
            }
        }
        BasicText("Editing: $active", style = TextStyle(color = Tk.textMuted, fontSize = 11.sp))
        ColorField(theme.effective(active), showThemeSwatches = false) { c -> state.setTheme(theme.set(active, c), coalesceKey = "theme:$active") }
    }
}
