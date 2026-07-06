// Code ↔ design-tree mapping for the IntelliJ plugin: DesignParser (Kotlin PSI →
// Node tree, unknowns preserved as RawCode) and, later, WriteBackPlanner.
//
// PSI comes from org.jetbrains.kotlin:kotlin-compiler — the NON-embeddable
// artifact, deliberately: the embeddable jar shades IntelliJ core classes
// (com.intellij.psi → org.jetbrains.kotlin.com.intellij.psi), and bytecode
// compiled against it would not link inside the IDE, where the bundled Kotlin
// plugin exposes the UNSHADED names. compileOnly keeps the jar out of the
// plugin zip — at IDE runtime the platform + Kotlin plugin provide the classes.
//
// API-surface discipline: touch only org.jetbrains.kotlin.psi(.psiUtil) plus
// com.intellij.psi.{PsiElement,PsiComment} basics — the decade-stable subset.
// Public API exposes only model types / strings / ints, never PSI.
plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":model"))
    implementation(project(":codegen"))
    compileOnly("org.jetbrains.kotlin:kotlin-compiler:2.4.0")

    // Tests run standalone PSI via KotlinCoreEnvironment — no IDE needed.
    testImplementation("org.jetbrains.kotlin:kotlin-compiler:2.4.0")
    testImplementation(kotlin("test"))
}
