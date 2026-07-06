# Composer

A **visual UI designer in the browser** - Figma-like - whose output is **Compose Multiplatform code**. It also runs **inside IntelliJ IDEA / Android Studio** as a plugin, with two-way sync between the designer and your Kotlin source.

> Design real Compose components on a canvas; get clean, idiomatic Compose Multiplatform code back.

**✨ Try it live: [composer.ckgin.com](https://composer.ckgin.com)** - no install, runs right in your browser (Chromium-based browsers get the full experience: eyedropper, local font picker).

The editor itself is built with **Compose Multiplatform on Kotlin/Wasm** (canvas/Skia backend), so the design surface uses the *same* `androidx.compose.*` primitives (`Box`, `Column`, `Row`, `Modifier`) that the generated code targets. WYSIWYG is real, not approximated. Code generation is a **pure, deterministic function** of the design tree.

## Features

- **Infinite canvas with multiple screens** - an artboard holds any number of screens (each becomes one generated `@Composable` function). Figma-style zoom (cursor-anchored, up to 500×), pan, move/resize handles, drill-down selection, arrow-key nudge, and snap guides (edge/center alignment + equal-gap spacing) when arranging screens.
- **Component palette** - Material 3 components: Text, TextField, Button (all variants), Icon, IconButton, Image, Switch, Checkbox, RadioButton, Slider, progress indicators, Column/Row/Box, Card, FAB, Scaffold (with real slots), TopAppBar, Dialog, BottomSheet, and more.
- **Reusable components** - promote any composable (screen) to a component and insert instances of it anywhere; instances follow the source live and can be detached back into editable copies. Generated as plain function calls.
- **Modifier-first inspector** - edit the modifier chain directly (padding, size, offset, background with solid or multi-stop gradient fills, border, clip, drop & inner shadows, alpha, weight, aspect ratio, fill…). Corner radii in dp or percent. Rows are drag-reorderable; order is significant and preserved end-to-end in both preview and code.
- **Layers panel** - full tree view with drag & drop reorder/reparent, rename (a screen's name becomes its function name), expand/collapse.
- **Theming** - named Material color themes per design with a full HSV color picker (hex, alpha, eyedropper on Chromium). Any color can reference a theme token - it follows theme switches and exports as `MaterialTheme.colorScheme.primary`. Generated as `lightColorScheme`/`darkColorScheme` + an `AppTheme` wrapper.
- **Live code panel** - syntax-highlighted Kotlin with line numbers, regenerated on every edit. Output is dependency-free, compiles standalone, and hoists real `remember` state so interactive components work out of the box.
- **Files & persistence** - multi-file workspace in localStorage with auto-save, starter templates, URL-based routing (deep links survive refresh), JSON import/export, and `.kt` export.

## IntelliJ IDEA / Android Studio plugin

The `:idea-plugin` module embeds the designer beside the Kotlin editor, Compose-Preview-style: any `.kt` file with top-level `@Composable` functions gets a Code | Split | Design editor. The plugin **parses your code into the design tree** (`:codeparse`, pure syntactic PSI - the exact inverse of codegen) and **writes visual edits back as regenerated code**, touching only the functions that actually changed.

Code the model can't represent (conditionals, loops, state logic, custom calls) becomes a locked **RawCode** node and is re-emitted **verbatim** - the plugin never destroys code it doesn't understand.

```bash
./gradlew :idea-plugin:runIde       # sandbox IDE with the plugin
./gradlew :idea-plugin:buildPlugin  # distributable zip
```

Requires IntelliJ IDEA 2024.2+ / Android Studio Ladybug+ (the designer runs on Kotlin/Wasm inside JCEF, which needs that platform's Chromium).

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
model/        Pure Kotlin (KMP jvm + wasmJs): Node (sealed tree), ModifierSpec (ordered
              chain), tree ops, serialization. No Compose dependencies.
codegen/      Design tree -> Kotlin source. Pure, deterministic, golden-file tested.
              Depends on :model only.
codeparse/    Kotlin PSI -> design tree (the inverse of codegen). Plain JVM, used by
              the IDE plugin's read direction.
app/          The editor (Compose Multiplatform, Kotlin/Wasm): canvas renderer, layers
              tree, inspector, palette, code panel, undo/redo, persistence.
idea-plugin/  IntelliJ/Android Studio plugin: JCEF-embedded designer + two-way sync.
```

Keeping `model`, `codegen`, and `codeparse` free of Compose UI dependencies means the core logic is plain JVM-unit-testable:

```bash
./gradlew :model:jvmTest :codegen:jvmTest :codeparse:test
```

`:codeparse` is anchored by round-trip property tests: `parse(generate(tree)) == tree` across the whole component/modifier space.

## Generated code

One `@Composable fun` per screen, plus a shared `AppTheme` when the theme is customized. Output follows the official Kotlin style (4-space indent, sorted imports, trailing commas in multiline calls, wrapped modifier chains) and uses only the common `androidx.compose.*` API - paste it into any Compose Multiplatform or Android project.

## Stack

- Kotlin 2.4.0 · Compose Multiplatform 1.11.1 (`wasmJs { browser() }`, Skia renderer)
- Gradle 8.14.5 · JDK 17 · IntelliJ Platform Gradle Plugin 2.11.0
- kotlinx.serialization for design persistence
- Editor icons: Google Material Symbols Rounded (Apache 2.0)

## License

[Apache License 2.0](LICENSE) - same license as the Kotlin and Compose ecosystem it builds on. Editor icons are Google Material Symbols Rounded, also Apache 2.0.
