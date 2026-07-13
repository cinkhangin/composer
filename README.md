# Composer

Composer is an Android Studio visual app designer for Compose. It discovers
composable functions across an Android module, renders them together on a
Figma-like canvas, and writes visual edits back to Kotlin source.

> Design the app visually while Kotlin remains the source of truth.

Composer runs entirely in-process through a `ComposePanel`. Source parsing and
generation are deterministic; the product contains no AI or model-assisted
code generation.

## Features

- Module-wide discovery of supported top-level `@Composable` functions in
  production Kotlin source roots. Functions annotated with `@Preview` are
  intentionally excluded.
- Infinite artboard with multiple composables, selection, resize handles, snap
  guides, keyboard nudging, trackpad pinch-to-zoom, and two-finger pan.
- Material 3 component palette and a modifier-first inspector.
- Cross-file composable calls rendered as live instances when the callable name
  is unambiguous.
- Layer tree editing and reparenting inside composable function bodies.
- Named Material themes and theme-token color references.
- Two-way source synchronization that edits only the owning function and
  conservatively preserves unsupported syntax as `RawCode`.
- One global undoable Android Studio write command, including edits that span
  multiple source files.

The current parser renders top-level block-body composables without receivers,
type parameters, or explicit return types. Function creation, deletion,
renaming, and ownership changes remain source-controlled until stable Composer
annotations are introduced.

## Android Studio plugin

The designer uses Android Studio's platform Compose runtime directly. Each
project tool window owns an independent in-process session, so several projects
can remain open without sharing bridge state. Android Studio's native Compose
Preview remains responsible for preview-only functions and split previews.

The current plugin target is Android Studio 2026.1+ (`261`, platform Compose).
Use JDK 17 for Gradle:

```bash
./gradlew :idea-plugin:runAndroidStudio
./gradlew :idea-plugin:buildPlugin
```

Override the Android Studio installation when needed:

```bash
./gradlew :idea-plugin:runAndroidStudio \
  -Pcomposer.androidStudio.path="/path/to/Android Studio.app"
```

## Architecture

The immutable design tree is the single source of truth.

```text
model/        Pure Kotlin model, modifiers, tree operations, and serialization.
codegen/      Deterministic design tree -> Compose Multiplatform source.
codeparse/    Kotlin source -> design tree plus conservative write-back planning.
app/          JVM Compose designer UI and per-panel in-process host session.
idea-plugin/  Android Studio app tool window, project source sync, and generation.
```

`model`, `codegen`, and `codeparse` have no Compose UI dependencies. Run their
tests and compile the designer/plugin with:

```bash
./gradlew :model:jvmTest :codegen:jvmTest :codeparse:jvmTest \
  :app:compileKotlinJvm :idea-plugin:compileKotlin
```

The parser is anchored by round-trip property tests and PSI-conformance tests;
code generation is covered by golden files.

## Direction

Composer's goal is whole-app visual design: UI, logic, state, and navigation in
one model while preserving idiomatic Kotlin. The next identity layer will group
related screen, UI, and ViewModel declarations with stable Composer annotations;
until then, module discovery uses deterministic source-based identities and
keeps structural function operations locked.

## Stack

- Kotlin 2.4.0
- Compose Multiplatform 1.11.1
- Material 3 1.9.0
- Gradle 8.14.5 and JDK 17
- IntelliJ Platform Gradle Plugin 2.11.0
- kotlinx.serialization 1.9.0

## License

[Apache License 2.0](LICENSE). Editor icons are Google Material Symbols Rounded,
also licensed under Apache 2.0.
