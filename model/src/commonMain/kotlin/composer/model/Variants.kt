package composer.model

import kotlinx.serialization.Serializable

/** Material3 button styles → `Button` / `ElevatedButton` / `FilledTonalButton` / `OutlinedButton` / `TextButton`. */
@Serializable
enum class ButtonVariant { Filled, Elevated, FilledTonal, Outlined, Text }

/** Material3 top-app-bar styles → `TopAppBar` / `CenterAlignedTopAppBar` / `MediumTopAppBar` / `LargeTopAppBar`. */
@Serializable
enum class TopAppBarVariant { Small, CenterAligned, Medium, Large }
