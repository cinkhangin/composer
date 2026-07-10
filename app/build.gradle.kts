import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("composer")
        browser {
            commonWebpackConfig {
                outputFileName = "composer.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        all {
            // The browser-interop layer (js(), JsAny, external interfaces) is
            // experimental in Kotlin 2.3 — opt in once instead of 70 warnings.
            languageSettings.optIn("kotlin.js.ExperimentalWasmJsInterop")
        }
        val commonMain by getting {
            dependencies {
                // Direct coordinates: the compose.* DSL aliases are deprecated since CMP 1.10.
                implementation("org.jetbrains.compose.runtime:runtime:1.11.1")
                implementation("org.jetbrains.compose.components:components-resources:1.11.1")
            }
        }
        val wasmJsMain by getting {
            dependencies {
                implementation(project(":model"))
                implementation(project(":codegen"))
                implementation(project(":codeparse")) // code→design for the editable code view
                implementation("org.jetbrains.compose.runtime:runtime:1.11.1")
                implementation("org.jetbrains.compose.foundation:foundation:1.11.1")
                // material3 is versioned independently of CMP since 1.8.
                implementation("org.jetbrains.compose.material3:material3:1.9.0")
                // Pinned artifact (no longer updated) — IconKind renders/generates
                // Icons.Default.* from it. Long-term migration: Material Symbols.
                implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
                implementation("org.jetbrains.compose.components:components-resources:1.11.1")
                implementation("org.jetbrains.compose.ui:ui:1.11.1")
                implementation("org.jetbrains.kotlinx:kotlinx-browser:0.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            }
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "composer.res"
}

// IDE plugin embedding: expose the production web bundle so :idea-plugin can
// package it (resolved by explicit configuration name — no variant matching,
// which keeps it clear of the KMP variant set).
val webDist by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}
artifacts {
    add(webDist.name, layout.buildDirectory.dir("dist/wasmJs/productionExecutable")) {
        builtBy(tasks.named("wasmJsBrowserDistribution"))
    }
}
