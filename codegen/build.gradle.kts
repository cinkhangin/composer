// Pure codegen: depends only on :model. No Compose. Golden tests run on the JVM.
plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":model"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
