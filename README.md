# Composer

Composer is an Android Studio plugin for visually designing Compose Multiplatform
interfaces. It renders real Compose components in an in-process `ComposePanel`,
parses Kotlin source into a design tree, and writes visual edits back as clean,
deterministic Compose code.

> Design real Compose components on a canvas; get clean, idiomatic Compose
> Multiplatform code back.

## Features

- Infinite artboard with multiple screens, zoom, pan, selection, resize handles,
  snap guides, and keyboard nudging.
- Material 3 component palette and a modifier-first inspector.
- Reusable composables and live instances.
- Drag-and-drop layer tree with renaming and reparenting.
- Named Material themes and theme-token color references.
- Code | Split | Design editor for Kotlin files containing `@Composable`
  functions.
- Whole-app tool window with screen, ViewModel, and Navigation 3 generation.
- Two-way source synchronization with conservative `RawCode` preservation for
  syntax the design model does not understand.

Code generation is a pure function of the design tree. Composer contains no AI
or model-assisted generation.

## Android Studio plugin

The designer uses Android Studio's platform Compose runtime directly. Each tool
window or split-editor preview owns an independent in-process session, so several
designers can remain open without sharing bridge state.

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
idea-plugin/  Android Studio integration, source sync, split editor, and app tool window.
```

`model`, `codegen`, and `codeparse` have no Compose UI dependencies. Run their
tests and compile the designer/plugin with:

```bash
./gradlew :model:jvmTest :codegen:jvmTest :codeparse:jvmTest \
  :app:compileKotlinJvm :idea-plugin:compileKotlin
```

The parser is anchored by round-trip property tests and PSI-conformance tests;
code generation is covered by golden files.

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
