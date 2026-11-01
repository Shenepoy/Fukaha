import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

val appVersionFile = rootProject.file("VERSION")
val appVersion = appVersionFile.readText().trim()
check(Regex("""^[0-9]{2}\.[0-9]{2}\.[0-9]+$""").matches(appVersion)) {
    "VERSION must be YY.0M.MICRO, got: ${appVersion.ifBlank { "<empty>" }}"
}

val webAppVersionSource = layout.projectDirectory.file(
    "src/jsMain/kotlin/app/fukaha/web/WebAppVersion.kt",
)
val webAppVersion = Regex("""const val WEB_APP_VERSION = "([^"]+)"""")
    .find(webAppVersionSource.asFile.readText())
    ?.groupValues
    ?.get(1)
    ?: error("WEB_APP_VERSION is missing from ${webAppVersionSource.asFile}")

check(webAppVersion == appVersion) {
    "WEB_APP_VERSION ($webAppVersion) must match VERSION ($appVersion)"
}

version = appVersion

val testingBuild = providers.gradleProperty("testingBuild").orElse("").get()
check(testingBuild.isEmpty() || Regex("[0-9]+|local").matches(testingBuild)) {
    "Invalid testingBuild"
}
val displayVersion = if (testingBuild.isEmpty()) appVersion else "$appVersion-testing.$testingBuild"
val generatedVersionDir = layout.buildDirectory.dir("generated/version")
val generateBuildVersion by tasks.registering {
    inputs.property("displayVersion", displayVersion)
    outputs.dir(generatedVersionDir)
    doLast {
        val source = generatedVersionDir.get().file("WebBuildVersion.kt").asFile
        source.parentFile.mkdirs()
        source.writeText("package app.fukaha.web\nconst val WEB_DISPLAY_VERSION = \"$displayVersion\"\n")
    }
}

kotlin {
    js {
        browser {
            commonWebpackConfig {
                // Development keeps the stable watcher URL. A production-only webpack
                // plugin hashes the emitted bundle and rewrites index.html plus sw.js.
                outputFileName = "fukaha.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        jsMain { kotlin.srcDir(generateBuildVersion) }
        jsMain.dependencies {
            implementation(projects.shared)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        jsTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

tasks.withType<KotlinWebpack>().configureEach {
    if (name.contains("Production")) {
        // Production maps are neither deployed nor uploaded to an error tracker.
        // Keep development maps intact for continuous browser iteration.
        sourceMaps = false
    }
}

tasks.named("jsBrowserDistribution") {
    inputs.property("testingBuild", testingBuild)
    doFirst {
        // The Kotlin distribution task is a Copy, not a Sync; without cleanup, obsolete hashed
        // bundles survive and can be deployed alongside the current one.
        delete(outputs.files.singleFile)
    }
    doLast {
        // Kotlin places resources into the distribution after webpack, so rewrite the final
        // copies rather than assuming index.html and sw.js are webpack compilation assets.
        val outputDirectory = outputs.files.singleFile
        val bundle = outputDirectory.listFiles()
            .orEmpty()
            .singleOrNull { it.name.matches(Regex("""fukaha\.[0-9a-f]{12}\.js""")) }
            ?: error("Could not find exactly one content-hashed Fukaha production bundle")
        val bundlePath = "/${bundle.name}"

        val index = outputDirectory.resolve("index.html")
        check(index.isFile) { "Missing production shell asset: index.html" }
        index.writeText(index.readText().replace("/fukaha.js", bundlePath))
        check(bundlePath in index.readText() && "/fukaha.js" !in index.readText()) {
            "Production index did not receive the content-hashed bundle"
        }

        if (testingBuild.isNotEmpty()) {
            index.writeText(index.readText()
                .replace("<title>Fukaha</title>", "<title>Fukaha Testing</title>")
                .replace("</head>", "<meta name=\"robots\" content=\"noindex,nofollow\"></head>")
                .replace("<body>", "<body><aside dir=\"ltr\" style=\"text-align:center;padding:8px;background:#ffd166;color:#171717;font:600 14px system-ui\">Fukaha Testing · $displayVersion</aside>"))
            val manifest = outputDirectory.resolve("manifest.webmanifest")
            manifest.writeText(manifest.readText().replace("\"Fukaha\"", "\"Fukaha Testing\""))
        }

        // CSS, HTML, icons and worker changes also need a fresh immutable shell.
        val digest = MessageDigest.getInstance("SHA-256")
        outputDirectory.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(outputDirectory).path }
            .forEach { asset ->
                digest.update(asset.relativeTo(outputDirectory).path.toByteArray())
                digest.update(asset.readBytes())
            }
        val buildHash = digest.digest().joinToString("") { "%02x".format(it) }.take(12)
        val serviceWorker = outputDirectory.resolve("sw.js")
        check(serviceWorker.isFile) { "Missing production shell asset: sw.js" }
        serviceWorker.writeText(
            serviceWorker.readText()
                .replace("/fukaha.js", bundlePath)
                .replace("fukaha-shell-v2", "fukaha-shell-$buildHash"),
        )
        check(bundlePath in serviceWorker.readText() && "fukaha-shell-$buildHash" in serviceWorker.readText()) {
            "Production service worker did not receive the content-hashed shell"
        }
    }
}

val pwaStaticTest by tasks.registering(Exec::class) {
    group = "verification"
    description = "Validates PWA assets, localization parity, CSS invariants, and hosting headers."
    commandLine(
        "node", "--test",
        layout.projectDirectory.file("src/staticTest/pwa-static.test.mjs"),
        layout.projectDirectory.file("src/staticTest/service-worker.test.mjs"),
    )
}

tasks.named("check") {
    dependsOn(pwaStaticTest)
}
