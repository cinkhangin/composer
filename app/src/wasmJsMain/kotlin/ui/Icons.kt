package composer.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import composer.res.Res
import composer.res.ic_arrow_down
import composer.res.ic_arrow_up
import composer.res.ic_chevron_right
import composer.res.ic_close
import composer.res.ic_code
import composer.res.ic_design
import composer.res.ic_duplicate
import composer.res.ic_file
import composer.res.ic_fit
import composer.res.ic_grip
import composer.res.ic_home
import composer.res.ic_layers
import composer.res.ic_menu
import composer.res.ic_moon
import composer.res.ic_redo
import composer.res.ic_share
import composer.res.ic_undo
import composer.res.ic_plus
import composer.res.ic_sliders
import composer.res.ic_sun
import composer.res.ic_trash
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.vectorResource

/**
 * Editor-chrome icon set, backed by **Google Material Symbols Rounded** (Apache 2.0;
 * github.com/google/material-design-icons). Each [AppIconKind] maps to a bundled vector drawable
 * (`composeResources/drawable/ic_*.xml`, fill vector XML generated from the rounded SVGs by gen_symbols.py).
 * [Icon] applies [tint], so the single-color stroke artwork picks up the theme color.
 */
enum class AppIconKind {
    Grip, Plus, Close, Trash, ArrowUp, ArrowDown, Duplicate,
    Sliders, Layers, ChevronRight, ChevronDown, Sun, Moon, Code, Design, File, Home, Fit,
    Undo, Redo, Share, Menu,
}

@Composable
fun AppIcon(kind: AppIconKind, modifier: Modifier = Modifier.size(16.dp), tint: Color = Tk.textSecondary) {
    Icon(
        imageVector = vectorResource(kind.resource()),
        contentDescription = null,
        modifier = modifier,
        tint = tint,
    )
}

private fun AppIconKind.resource(): DrawableResource = when (this) {
    AppIconKind.Grip -> Res.drawable.ic_grip
    AppIconKind.Plus -> Res.drawable.ic_plus
    AppIconKind.Close -> Res.drawable.ic_close
    AppIconKind.Trash -> Res.drawable.ic_trash
    AppIconKind.ArrowUp -> Res.drawable.ic_arrow_up
    AppIconKind.ArrowDown -> Res.drawable.ic_arrow_down
    AppIconKind.Duplicate -> Res.drawable.ic_duplicate
    AppIconKind.Sliders -> Res.drawable.ic_sliders
    AppIconKind.Layers -> Res.drawable.ic_layers
    AppIconKind.ChevronRight -> Res.drawable.ic_chevron_right
    AppIconKind.ChevronDown -> Res.drawable.ic_arrow_down // a downward chevron
    AppIconKind.Sun -> Res.drawable.ic_sun
    AppIconKind.Moon -> Res.drawable.ic_moon
    AppIconKind.Code -> Res.drawable.ic_code
    AppIconKind.Design -> Res.drawable.ic_design
    AppIconKind.File -> Res.drawable.ic_file
    AppIconKind.Home -> Res.drawable.ic_home
    AppIconKind.Fit -> Res.drawable.ic_fit
    AppIconKind.Undo -> Res.drawable.ic_undo
    AppIconKind.Redo -> Res.drawable.ic_redo
    AppIconKind.Share -> Res.drawable.ic_share
    AppIconKind.Menu -> Res.drawable.ic_menu
}
