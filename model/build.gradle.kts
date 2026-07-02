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
                // api (not implementation): @Serializable types put the serialization
                // runtime in the model's ABI — consumers (:codegen) must see it too.
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
