// Root project declares plugin versions only; modules apply what they need.
// This keeps `model` and `codegen` free of the Compose plugin entirely.
plugins {
    kotlin("multiplatform") version "2.4.0" apply false
    kotlin("jvm") version "2.4.0" apply false
    kotlin("plugin.serialization") version "2.4.0" apply false
    id("org.jetbrains.compose") version "1.11.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
    // org.jetbrains.intellij.platform (:idea-plugin) rides in via the settings
    // plugin (settings.gradle.kts), which already puts it on the classpath —
    // declaring a version here would conflict.
}
