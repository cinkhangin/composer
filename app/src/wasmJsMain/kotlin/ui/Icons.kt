package composer.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Editor-chrome icon set, backed by **Google Material Symbols Rounded** (Apache 2.0;
 * github.com/google/material-design-icons). Each [AppIconKind] maps to a symbol name in the
 * bundled set ([Symbols]/[SymbolIcon]) — same async bundle as every other icon in the editor.
 */
enum class AppIconKind {
    Grip, Plus, Close, Trash, ArrowUp, ArrowDown, Duplicate,
    Sliders, Layers, ChevronRight, ChevronDown, Sun, Moon, Code, Design, File, Home, Fit,
    Undo, Redo, Share, Menu,
}

@Composable
fun AppIcon(kind: AppIconKind, modifier: Modifier = Modifier.size(16.dp), tint: Color = Tk.textSecondary) {
    SymbolIcon(kind.symbolName(), modifier = modifier, tint = tint)
}

private fun AppIconKind.symbolName(): String = when (this) {
    AppIconKind.Grip -> "drag_indicator"
    AppIconKind.Plus -> "add"
    AppIconKind.Close -> "close"
    AppIconKind.Trash -> "delete"
    AppIconKind.ArrowUp -> "keyboard_arrow_up"
    AppIconKind.ArrowDown -> "keyboard_arrow_down"
    AppIconKind.Duplicate -> "content_copy"
    AppIconKind.Sliders -> "tune"
    AppIconKind.Layers -> "layers"
    AppIconKind.ChevronRight -> "chevron_right"
    AppIconKind.ChevronDown -> "keyboard_arrow_down"
    AppIconKind.Sun -> "light_mode"
    AppIconKind.Moon -> "dark_mode"
    AppIconKind.Code -> "code"
    AppIconKind.Design -> "design_services"
    AppIconKind.File -> "description"
    AppIconKind.Home -> "home"
    AppIconKind.Fit -> "fit_screen"
    AppIconKind.Undo -> "undo"
    AppIconKind.Redo -> "redo"
    AppIconKind.Share -> "share"
    AppIconKind.Menu -> "menu"
}
