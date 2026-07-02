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
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.components.resources)
            }
        }
        val wasmJsMain by getting {
            dependencies {
                implementation(project(":model"))
                implementation(project(":codegen"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.components.resources)
                implementation(compose.ui)
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
