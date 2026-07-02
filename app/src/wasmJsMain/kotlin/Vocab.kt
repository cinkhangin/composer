package composer

import composer.model.BoxAlignment
import composer.model.ButtonVariant
import composer.model.HAlignment
import composer.model.HArrangement
import composer.model.TextFontFamily
import composer.model.TextWeight
import composer.model.TopAppBarVariant
import composer.model.VAlignment
import composer.model.VArrangement

/**
 * Plain-language labels for the designer-facing UI. The model keeps Compose/Material
 * enum names (per the redesign decision to keep component names), but the inspector
 * renders these friendlier strings so a designer never has to read `SpaceBetween` or
 * `contentAlignment`. This is presentation only — values are unchanged.
 */
object Vocab {
    /** Friendly label for an enum value shown in a dropdown. Falls back to the raw name. */
    fun label(value: Any?): String = when (value) {
        is TextWeight -> when (value) {
            TextWeight.Normal -> "Regular"
            TextWeight.Light -> "Light"
            TextWeight.Medium -> "Medium"
            TextWeight.SemiBold -> "Semibold"
            TextWeight.Bold -> "Bold"
            TextWeight.ExtraBold -> "Extra bold"
            TextWeight.Black -> "Black"
        }
        is TextFontFamily -> when (value) {
            TextFontFamily.Default -> "Default"
            TextFontFamily.SansSerif -> "Sans serif"
            TextFontFamily.Serif -> "Serif"
            TextFontFamily.Monospace -> "Monospace"
            TextFontFamily.Cursive -> "Cursive"
        }
        is ButtonVariant -> when (value) {
            ButtonVariant.Filled -> "Filled"
            ButtonVariant.Elevated -> "Elevated"
            ButtonVariant.FilledTonal -> "Tonal"
            ButtonVariant.Outlined -> "Outlined"
            ButtonVariant.Text -> "Text"
        }
        is VArrangement -> when (value) {
            VArrangement.Top -> "Top"
            VArrangement.Bottom -> "Bottom"
            VArrangement.Center -> "Center"
            VArrangement.SpaceBetween -> "Space between"
            VArrangement.SpaceAround -> "Space around"
            VArrangement.SpaceEvenly -> "Space evenly"
        }
        is HArrangement -> when (value) {
            HArrangement.Start -> "Left"
            HArrangement.End -> "Right"
            HArrangement.Center -> "Center"
            HArrangement.SpaceBetween -> "Space between"
            HArrangement.SpaceAround -> "Space around"
            HArrangement.SpaceEvenly -> "Space evenly"
        }
        is HAlignment -> when (value) {
            HAlignment.Start -> "Left"
            HAlignment.Center -> "Center"
            HAlignment.End -> "Right"
        }
        is VAlignment -> when (value) {
            VAlignment.Top -> "Top"
            VAlignment.Center -> "Center"
            VAlignment.Bottom -> "Bottom"
        }
        is BoxAlignment -> when (value) {
            BoxAlignment.TopStart -> "Top left"
            BoxAlignment.TopCenter -> "Top center"
            BoxAlignment.TopEnd -> "Top right"
            BoxAlignment.CenterStart -> "Center left"
            BoxAlignment.Center -> "Center"
            BoxAlignment.CenterEnd -> "Center right"
            BoxAlignment.BottomStart -> "Bottom left"
            BoxAlignment.BottomCenter -> "Bottom center"
            BoxAlignment.BottomEnd -> "Bottom right"
        }
        is TopAppBarVariant -> when (value) {
            TopAppBarVariant.Small -> "Small"
            TopAppBarVariant.CenterAligned -> "Center"
            TopAppBarVariant.Medium -> "Medium"
            TopAppBarVariant.Large -> "Large"
        }
        else -> value.toString()
    }
}
