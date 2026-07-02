import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// Pure model: no Compose plugin, no Compose deps. Targets JVM (for tests) and
// Wasm (for the app). Must stay common-safe Kotlin only.
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
