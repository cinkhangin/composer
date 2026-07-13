import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvmToolchain(17)

    // The in-process Android Studio designer (ComposePanel host).
    jvm()

    // Standalone website. This is a separate host for the same common editor;
    // it is never embedded into or packaged with the Android Studio plugin.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("composer")
        browser {
            commonWebpackConfig { outputFileName = "composer.js" }
        }
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":model"))
                implementation(project(":codegen"))
                implementation(project(":codeparse"))
                // Direct coordinates: the compose.* DSL aliases are deprecated since CMP 1.10.
                implementation("org.jetbrains.compose.runtime:runtime:1.11.1")
                implementation("org.jetbrains.compose.foundation:foundation:1.11.1")
                // material3 is versioned independently of CMP since 1.8.
                implementation("org.jetbrains.compose.material3:material3:1.9.0")
                // Pinned artifact (no longer updated) — IconKind renders/generates
                // Icons.Default.* from it. Long-term migration: Material Symbols.
                implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
                implementation("org.jetbrains.compose.components:components-resources:1.11.1")
                implementation("org.jetbrains.compose.ui:ui:1.11.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            }
        }
        val wasmJsMain by getting {
            languageSettings.optIn("kotlin.js.ExperimentalWasmJsInterop")
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-browser:0.3")
            }
        }
        val jvmMain by getting {
            dependencies {
                // Compose Desktop (macOS arm64 for now; other hosts when needed) —
                // carries the skiko-awt runtime natives the jvm target renders with.
                implementation("org.jetbrains.compose.desktop:desktop-jvm-macos-arm64:1.11.1")
            }
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "composer.res"
}
