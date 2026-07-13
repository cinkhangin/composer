import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// Code ↔ design-tree mapping: DesignParser (Kotlin source → Node tree, unknowns
// preserved as RawCode) and WriteBackPlanner. The parsing front-end is a
// hand-rolled lexer/scanner (Lexer/FileScanner/StatementParser) — pure Kotlin,
// no production PSI — shared by the Android Studio plugin and website.
//
// kotlin-compiler remains a TEST-ONLY dependency: PsiConformanceTest asserts the
// scanner agrees byte-for-byte with real PSI on every offset write-back relies on.
plugins {
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain(17)

    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":model"))
                implementation(project(":codegen"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                // Standalone PSI via KotlinCoreEnvironment — the conformance oracle.
                implementation("org.jetbrains.kotlin:kotlin-compiler:2.4.0")
            }
        }
    }
}
