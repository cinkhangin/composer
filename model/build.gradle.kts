// Pure model: no Compose plugin and no Compose dependencies. The Android
// Studio plugin and its in-process designer consume the JVM target.
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvm()

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
