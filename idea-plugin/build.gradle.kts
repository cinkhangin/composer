// Android Studio plugin: hosts the Compose designer in-process and reuses the
// pure :model/:codegen/:codeparse JVM variants.
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.intellij.platform")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    intellijPlatform {
        // Compile against the stable IntelliJ API baseline; runtime support is
        // Android Studio versions that expose intellij.platform.compose.
        intellijIdeaCommunity("2024.2.5")
        bundledPlugin("org.jetbrains.kotlin") // Kotlin PSI for the code parser
    }
    implementation(project(":model"))
    implementation(project(":codegen"))
    implementation(project(":codeparse")) // pure common-Kotlin parser — no compiler/PSI dependency
    // The in-process designer: :app's jvm variant carries the
    // Compose Desktop runtime; all @Composable code stays in :app — this
    // module only consumes plain JComponents.
    implementation(project(":app"))
    // NEVER add kotlinx-coroutines here — the platform bundles a patched build.
}

configurations.runtimeClasspath {
    // The platform's patched kotlinx-coroutines must win at runtime.
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-jdk8")
    // NEVER bundle compose/skiko: on Compose-shipping IDEs (AS 261+/IDEA 251+)
    // the platform's copies are used via the plugin.xml module dependency on
    // intellij.platform.compose (two skiko dylibs in one process fail with objc
    // class collisions + UnsatisfiedLinkError), and on older IDEs a bundled
    // runtime would die: the plugin classloader force-loads signature classes
    // like kotlin.time.Duration from the PLATFORM's older stdlib, splitting the
    // stdlib under skiko (NoSuchMethodError in the Metal redrawer). Older IDEs
    // The plugin therefore targets Android Studio's platform Compose runtime
    // and bundles only what that runtime lacks: material3 + material
    // (ripple/icons) + components-resources.
    exclude(group = "org.jetbrains.compose.desktop")
    exclude(group = "org.jetbrains.compose.runtime")
    exclude(group = "org.jetbrains.compose.foundation")
    exclude(group = "org.jetbrains.compose.ui")
    exclude(group = "org.jetbrains.compose.animation")
    exclude(group = "org.jetbrains.skiko")
    // Nor kotlin-stdlib: two stdlib copies across the plugin and the platform's
    // compose module loader hit JVM loader-constraint violations on any call
    // that crosses the boundary (LinkageError on kotlin.jvm.internal.* during
    // recomposition — froze whole compose subtrees).
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
}

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        id = "com.ckgin.composer"
        name = "Composer"
        version = "0.1.0"
        vendor {
            name = "cinkhangin"
        }
        ideaVersion {
            sinceBuild = "261"
            untilBuild = provider { null }
        }
    }
}

// Run the plugin in a locally installed Android Studio instead of the IDEA
// sandbox: ./gradlew :idea-plugin:runAndroidStudio
// (override the install path with -Pcomposer.androidStudio.path=/path/to/Android Studio.app)
val androidStudioPath = providers.gradleProperty("composer.androidStudio.path").orNull
    ?: "${System.getProperty("user.home")}/Applications/Android Studio.app"
if (file(androidStudioPath).exists()) {
    intellijPlatformTesting.runIde.register("runAndroidStudio") {
        localPath = file(androidStudioPath)
        task {
            composeHotReload = false
        }
    }
}
