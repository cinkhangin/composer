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
    // NEVER bundle compose/skiko: on Compose-shipping IDEs (AS 261+/IDEA 251+)
    // the platform's copies are used via the plugin.xml module dependency on
    // intellij.platform.compose (two skiko dylibs in one process fail with objc
    // class collisions + UnsatisfiedLinkError), and on older IDEs a bundled
    // runtime dies anyway: the plugin classloader force-loads signature classes
    // like kotlin.time.Duration from the PLATFORM's older stdlib, splitting the
    // stdlib under skiko (NoSuchMethodError in the Metal redrawer). Older IDEs
    // use the JCEF web designer instead. We bundle ONLY what compose-shipping
    // platforms lack: material3 + material (ripple/icons) + components-resources.
    exclude(group = "org.jetbrains.compose.desktop")
    exclude(group = "org.jetbrains.compose.runtime")
    exclude(group = "org.jetbrains.compose.foundation")
    exclude(group = "org.jetbrains.compose.ui")
    exclude(group = "org.jetbrains.compose.animation")
    exclude(group = "org.jetbrains.skiko")
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

// Platform-compose builds declare the intellij.platform.compose module in
// plugin.xml (the classes come from the IDE, not the plugin zip — see the
// runtimeClasspath excludes above). Default builds leave the placeholder empty.
tasks.processResources {
    val platformCompose = providers.gradleProperty("composer.platformCompose").orNull == "true"
    filesMatching("META-INF/plugin.xml") {
        filter { line ->
            if (line.trim() == "<!--PLATFORM_COMPOSE-->") {
                if (platformCompose) {
                    "    <dependencies><module name=\"intellij.platform.compose\"/></dependencies>"
                } else ""
            } else line
        }
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
    // The platform plugin auto-attaches the Compose Hot Reload agent (alpha,
    // class-redefinition) when it sees compose on the classpath — keep that
    // instrumentation out of the designer host.
    composeHotReload = false
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
            composeHotReload = false
        }
    }
}
