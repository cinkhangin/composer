import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask

// IntelliJ IDEA / Android Studio plugin: hosts the web designer (bundled Wasm
// build of :app) in a JCEF preview beside the Kotlin editor, and reuses the
// pure :model/:codegen JVM variants for design JSON + code generation.
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.intellij.platform")
}

kotlin {
    jvmToolchain(17)
}

// The production web bundle from :app, packaged into the plugin's resources
// under composer-web/ (served by ComposerWebServer). Resolved by explicit
// configuration name — no variant matching against the KMP variant set.
val webDist: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    intellijPlatform {
        // 242 = 2024.2: first baseline whose JCEF Chromium has WasmGC (the
        // Kotlin/Wasm hard floor) and runs on JBR 21. Covers AS Ladybug+.
        intellijIdeaCommunity("2024.2.5")
        bundledPlugin("org.jetbrains.kotlin") // Kotlin PSI for the code parser
    }
    implementation(project(":model"))
    implementation(project(":codegen"))
    implementation(project(":codeparse")) // pure common-Kotlin parser — no compiler/PSI dependency
    webDist(project(mapOf("path" to ":app", "configuration" to "webDist")))
    // The in-process (no-JCEF) designer: :app's jvm variant carries the
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
    // Platform-compose mode (-Pcomposer.platformCompose=true): don't bundle
    // compose/skiko — the IDE's own copies are used via the plugin.xml module
    // dependency on intellij.platform.compose. Two skiko dylibs in one process
    // fail (objc class collisions + UnsatisfiedLinkError on Metal init), so on
    // Compose-shipping IDEs we may ONLY bundle what the platform lacks:
    // material3 + material (ripple/icons) + components-resources.
    if (providers.gradleProperty("composer.platformCompose").orNull == "true") {
        exclude(group = "org.jetbrains.compose.desktop")
        exclude(group = "org.jetbrains.compose.runtime")
        exclude(group = "org.jetbrains.compose.foundation")
        exclude(group = "org.jetbrains.compose.ui")
        exclude(group = "org.jetbrains.compose.animation")
        exclude(group = "org.jetbrains.skiko")
    }
}

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        id = "com.ckgin.composer"
        name = "Composer Designer"
        version = "0.1.0"
        vendor {
            name = "cinkhangin"
        }
        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }
        }
    }
}

// Building the wasm dist takes ~1 min; skip bundling for plugin-code-only
// iteration with -Pcomposer.bundleWeb=false (runIde serves the dev dist dir
// via the system property below anyway; a distributable zip MUST bundle it).
if (providers.gradleProperty("composer.bundleWeb").orNull != "false") {
    tasks.processResources {
        from(webDist) { into("composer-web") }
    }
}

tasks.named<RunIdeTask>("runIde") {
    // Dev loop: serve the web app straight from :app's dist dir so web-side
    // changes need only :app:wasmJsBrowserDistribution + a preview reload,
    // not a plugin rebuild.
    systemProperty(
        "composer.web.dist.dir",
        rootProject.layout.projectDirectory.dir("app/build/dist/wasmJs/productionExecutable").asFile.absolutePath,
    )
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
            systemProperty(
                "composer.web.dist.dir",
                rootProject.layout.projectDirectory.dir("app/build/dist/wasmJs/productionExecutable").asFile.absolutePath,
            )
        }
    }
}
