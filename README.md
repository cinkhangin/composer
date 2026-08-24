# Composer

Composer is a Figma-like visual app designer for Compose Multiplatform. It runs
as an in-process Android Studio plugin and as a standalone Kotlin/Wasm website;
both hosts share the same deterministic parser, model, renderer, and code
generator.

> Design visually while Kotlin remains the source of truth.

Composer is built for source safety. Supported Compose code becomes an editable
design tree, while code the designer cannot represent is preserved verbatim as
`RawCode`. Neither host requires a hidden backend or embeds a JCEF-based IDE
frontend.

## Project status

Composer is under active development and is not yet a stable release.

Available today:

- Android Studio tool window backed by an in-process `ComposePanel`.
- Module-wide discovery pairs real top-level `@Composable` functions with their
  `@Preview` invocations. Functions without a preview stay out of the designer.
- Two-way synchronization between Kotlin source and the visual design for the
  supported syntax subset.
- Standalone, local-first Kotlin/Wasm editor with browser persistence and
  Kotlin/JSON import and export.
- Deterministic Compose Multiplatform code generation and conservative
  declaration-level write-back.

In progress:

- Broader coverage of real-world Compose syntax and source-backed containers.
- Whole-app modeling for screen identity, state, events, ViewModels, and
  Navigation 3 relationships.
- Release hardening and expanded Android Studio and browser testing.

## Current capabilities

### Visual designer

- Infinite multi-composable canvas with selection, resize handles, snap guides,
  keyboard nudging, undo/redo, and layer reparenting.
- Trackpad two-finger pan and pinch-to-zoom behavior.
- Designer-wide screen-size selector; composables hug their rendered content,
  expand when their content fills the screen, and measure `0x0` when they have
  no renderable content.
- Material 3 component palette, modifier editor, inspector, component tree, and
  named light/dark theme preview.
- Cross-file composable calls rendered as component instances when the target
  can be resolved unambiguously.

### Kotlin source support

- Common Compose and Material 3 primitives, layout containers, Scaffold slots,
  alignment, common modifiers, theme tokens, typography expressions, and
  parameter-backed text previews.
- Source-backed wrappers keep renderable descendants visible when the outer
  Kotlin call is not directly editable.
- Annotation-free extraction of unambiguous `Color(...)` constants and
  light/dark Material color schemes.
- Unsupported statements, callbacks, arguments, and runtime-dependent modifier
  expressions are retained conservatively instead of being discarded.
- Preview arguments are editable from the inspector and drive parameter-backed
  canvas content while edits continue to update the real composable body.
- Preview-backed composables containing only non-renderable source remain as
  empty `0x0` canvas items; hidden `RawCode` is preserved for write-back.

### Android Studio integration

- Automatic refresh after relevant editor and project-file changes.
- Selection synchronization between the Composer tree and Kotlin source.
- One undoable IDE command for designer edits, including multi-file writes.
- `New Composable` flow that always creates a dedicated Kotlin file, keeping at
  most one non-preview composable in each generated file.
- Website Kotlin export downloads one file per screen and keeps the theme
  wrapper in its own file.
- Optional app scaffolding for a first screen, ViewModel, and Navigation 3
  source layout. Composer reports required Gradle dependencies but does not edit
  build files automatically.

Android Studio's native Compose Preview continues to own per-file split
previews. The Composer plugin does not register a custom split editor or embed
the website.

## Known limits

- Production parsing is intentionally conservative and supports top-level
  block-body composables without receivers, type parameters, or explicit return
  types.
- Unsupported Kotlin is preserved but not visually editable.
- Creating composables is supported; structural rename, deletion, and ownership
  changes remain source-controlled until stable screen identities are added.
- Visual state machines, ViewModel logic, and full navigation editing are not
  complete yet.
- The plugin targets Android Studio 2026.1+ / IntelliJ Platform build 261+ and
  relies on the IDE's bundled Compose runtime.

## Run the Android Studio plugin

Use JDK 17:

```bash
JAVA_HOME=/path/to/jdk-17 ./gradlew :idea-plugin:runAndroidStudio
```

By default, the task uses `~/Applications/Android Studio.app`. Override it when
needed:

```bash
JAVA_HOME=/path/to/jdk-17 ./gradlew :idea-plugin:runAndroidStudio \
  -Pcomposer.androidStudio.path="/path/to/Android Studio.app"
```

Build the installable plugin archive with:

```bash
JAVA_HOME=/path/to/jdk-17 ./gradlew :idea-plugin:buildPlugin
```

The archive is written under `idea-plugin/build/distributions`.

## Run the website

Start the Kotlin/Wasm development server:

```bash
JAVA_HOME=/path/to/jdk-17 ./gradlew \
  :app:wasmJsBrowserDevelopmentRun --no-configuration-cache
```

Then open [http://localhost:8080](http://localhost:8080). Build the production
distribution with:

```bash
JAVA_HOME=/path/to/jdk-17 ./gradlew :app:wasmJsBrowserDistribution
```

The generated site is under
`app/build/dist/wasmJs/productionExecutable`. Designs stay in browser
`localStorage`; Chromium-based browsers additionally support the eyedropper and
installed-font access APIs.

## Architecture

```text
model/        Serializable design tree, modifiers, themes, and tree operations.
codegen/      Deterministic design tree -> Compose Multiplatform Kotlin source.
codeparse/    Kotlin source -> design tree and conservative write-back plans.
app/          Shared Compose editor plus JVM and standalone Wasm hosts.
idea-plugin/  Android Studio tool window, project discovery, and source sync.
```

`model`, `codegen`, and `codeparse` do not depend on Compose UI. The plugin uses
Android Studio's platform Compose, Skiko, Kotlin, and coroutine modules instead
of bundling duplicate runtimes.

## Verify the project

Run the core tests and both host compilation checks with JDK 17:

```bash
JAVA_HOME=/path/to/jdk-17 ./gradlew \
  :model:jvmTest \
  :codegen:jvmTest \
  :codeparse:jvmTest \
  :app:jvmTest \
  :app:compileKotlinWasmJs \
  :idea-plugin:buildPlugin
```

Parser round trips, source write-back, serialization, and code-generation
goldens are covered by the test suites.

## Stack

- Kotlin and Compose compiler 2.4.0
- Compose Multiplatform 1.11.1
- Material 3 1.9.0
- Gradle 8.14.5 and JDK 17
- IntelliJ Platform Gradle Plugin 2.11.0
- kotlinx.serialization 1.9.0

## License

[Apache License 2.0](LICENSE). Editor icons are Google Material Symbols Rounded,
also licensed under Apache 2.0.
