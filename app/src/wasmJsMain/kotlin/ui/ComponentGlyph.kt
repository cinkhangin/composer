package composer.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Icon per component type for the palette, layers tree, and inspector badge — backed
 * by the bundled **Material Symbols Rounded** set (see [SymbolIcon]). Keyed by
 * [composer.model.typeName]; unknown types fall back to a generic square. [tint] flows
 * through to the icon color.
 */
@Composable
fun ComponentGlyph(type: String, modifier: Modifier = Modifier, tint: Color = Tk.accent) {
    SymbolIcon(glyphIconName(type), modifier, tint)
}

/** Maps a node type name to a Material Symbols icon name. */
private fun glyphIconName(type: String): String = when (type) {
    "Text" -> "title"
    "Button" -> "touch_app"
    "Column" -> "table_rows"       // rows stacked = vertical
    "Row" -> "view_column"         // columns side-by-side = horizontal
    "Box" -> "square"
    "Artboard" -> "space_dashboard"
    "Instance" -> "widgets"
    "Composable" -> "function"     // one generated @Composable function
    "Slot" -> "place_item"         // a Compose slot argument (drop content in)
    "Spacer" -> "expand"
    "Image" -> "image"
    "Divider" -> "horizontal_rule"
    "Icon" -> "star"
    "IconButton" -> "ads_click"
    "TextField" -> "input"
    "Card" -> "credit_card"
    "Scaffold" -> "mobile"
    "TopAppBar" -> "toolbar"
    "Fab" -> "add_circle"
    "Dialog" -> "chat_bubble"
    "BottomSheet" -> "bottom_sheets"
    "Switch" -> "toggle_on"
    "Checkbox" -> "check_box"
    "RadioButton" -> "radio_button_checked"
    "Slider" -> "tune"
    "CircularProgress" -> "progress_activity"
    "LinearProgress" -> "linear_scale"
    "RawCode" -> "code"            // opaque preserved code (IDE plugin)
    else -> "square"
}
