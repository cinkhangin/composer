import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// Pure model: no Compose plugin and no Compose dependencies. The Android
// Studio plugin consumes JVM; the standalone website consumes Wasm.
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

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
