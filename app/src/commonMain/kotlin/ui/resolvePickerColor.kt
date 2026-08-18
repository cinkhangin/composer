package composer.ui

import androidx.compose.runtime.Composable

/** [value]'s displayable ARGB: theme-token references resolve via [LocalThemeSwatches]. */
@Composable
fun resolvePickerColor(value: Long): Long =
    LocalThemeSwatches.current.firstOrNull { it.value == value }?.resolved ?: value
