# Composer

A **visual UI designer in the browser** - Figma-like - whose output is **Compose Multiplatform code**.

> Design real Compose components on a canvas; get clean, idiomatic Compose Multiplatform code back.

**✨ Try it live: [composer.ckgin.com](https://composer.ckgin.com)** - no install, runs right in your browser (Chromium-based browsers get the full experience: eyedropper, local font picker).

The editor itself is built with **Compose Multiplatform on Kotlin/Wasm** (canvas/Skia backend), so the design surface uses the *same* `androidx.compose.*` primitives (`Box`, `Column`, `Row`, `Modifier`) that the generated code targets. WYSIWYG is real, not approximated. Code generation is a **pure, deterministic function** of the design tree.

## Features

- **Infinite canvas with multiple screens** - an artboard holds any number of screens (each becomes one generated `@Composable` function). Figma-style zoom (cursor-anchored, up to 500×), pan, move/resize handles, drill-down selection, arrow-key nudge.
- **Component palette** - Material 3 components: Text, TextField, Button (all variants), Icon, IconButton, Image, Switch, Checkbox, RadioButton, Slider, progress indicators, Column/Row/Box, Card, FAB, Scaffold (with real slots), TopAppBar, Dialog, BottomSheet, and more.
- **Modifier-first inspector** - edit the modifier chain directly (padding, size, offset, background with solid or multi-stop gradient fills, border, clip, drop & inner shadows, alpha, weight, aspect ratio, fill…). Corner radii in dp or percent. Rows are drag-reorderable; order is significant and preserved end-to-end in both preview and code.
- **Layers panel** - full tree view with drag & drop reorder/reparent, rename (a screen's name becomes its function name), expand/collapse.
- **Theming** - named Material color themes per design with a full HSV color picker (hex, alpha, eyedropper on Chromium). Any color can reference a theme token - it follows theme switches and exports as `MaterialTheme.colorScheme.primary`. Generated as `lightColorScheme`/`darkColorScheme` + an `AppTheme` wrapper.
- **Live code panel** - syntax-highlighted Kotlin with line numbers, regenerated on every edit. Output is dependency-free, compiles standalone, and hoists real `remember` state so interactive components work out of the box.
- **Files & persistence** - multi-file workspace in localStorage with auto-save, URL-based routing (deep links survive refresh), JSON import/export, and `.kt` export.

## Try it

The hosted app is at **[composer.ckgin.com](https://composer.ckgin.com)**. To run it locally instead:

```bash
./gradlew :app:wasmJsBrowserDevelopmentRun --continuous --no-configuration-cache
```

Then open http://localhost:8080. Requires **JDK 17**; a Chromium-based browser gets the extra goodies (eyedropper, local font picker).

Production bundle:

```bash
./gradlew :app:wasmJsBrowserDistribution
```

## Architecture

The design tree is the **single source of truth**. The canvas and the generated code are both projections of it.

```
model/     Pure Kotlin (KMP jvm + wasmJs): Node (sealed tree), ModifierSpec (ordered
           chain), tree ops, serialization. No Compose dependencies.
codegen/   Design tree -> Kotlin source. Pure, deterministic, golden-file tested.
           Depends on :model only.
app/       The editor (Compose Multiplatform, Kotlin/Wasm): canvas renderer, layers
           tree, inspector, palette, code panel, undo/redo, persistence.
```

Keeping `model` and `codegen` free of Compose UI dependencies means the core logic is plain JVM-unit-testable:

```bash
./gradlew :model:jvmTest :codegen:jvmTest
```

## Generated code

One `@Composable fun` per screen, plus a shared `AppTheme` when the theme is customized. Output follows the official Kotlin style (4-space indent, sorted imports, trailing commas in multiline calls, wrapped modifier chains) and uses only the common `androidx.compose.*` API - paste it into any Compose Multiplatform or Android project.

## Stack

- Kotlin 2.4.0 · Compose Multiplatform 1.11.1 (`wasmJs { browser() }`, Skia renderer)
- Gradle 8.11.1 · JDK 17
- kotlinx.serialization for design persistence
- Editor icons: Google Material Symbols Rounded (Apache 2.0)

## License

[Apache License 2.0](LICENSE) - same license as the Kotlin and Compose ecosystem it builds on. Editor icons are Google Material Symbols Rounded, also Apache 2.0.
