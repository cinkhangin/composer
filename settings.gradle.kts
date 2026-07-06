import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "composer"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
    // Adds the intellijPlatform repository helpers below (for :idea-plugin).
    id("org.jetbrains.intellij.platform.settings") version "2.11.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        // IntelliJ Platform artifacts (IDE dists, bundled plugins) for :idea-plugin.
        intellijPlatform {
            defaultRepositories()
        }
    }
}

include(":app", ":model", ":codegen", ":idea-plugin")
