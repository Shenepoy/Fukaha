import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

val appVersionFile = rootProject.file("VERSION")
val appVersion = appVersionFile.readText().trim()
val testingBuild = providers.gradleProperty("testingBuild").orElse("local").get()
check(Regex("[0-9]+|local").matches(testingBuild)) { "Invalid testingBuild" }
val testingVersionCode = providers.gradleProperty("testingVersionCode").orElse("1").get().toInt()
check(Regex("""^[0-9]{2}\.[0-9]{2}\.[0-9]+$""").matches(appVersion)) {
    "VERSION must be YY.0M.MICRO, got: ${appVersion.ifBlank { "<empty>" }}"
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("key.properties")
    if (file.exists()) load(file.inputStream())
}

fun releaseSigningValue(envName: String, propertyName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }

android {
    namespace = "app.fukaha"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.fukaha"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 16
        versionName = appVersion
        buildConfigField("boolean", "IS_TESTING", "false")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    signingConfigs {
        val storePath = releaseSigningValue("RELEASE_STORE_FILE", "storeFile")
        val storePassword = releaseSigningValue("RELEASE_STORE_PASSWORD", "storePassword")
        val keyAlias = releaseSigningValue("RELEASE_KEY_ALIAS", "keyAlias")
        val keyPassword = releaseSigningValue("RELEASE_KEY_PASSWORD", "keyPassword")
        if (storePath != null && storePassword != null && keyAlias != null && keyPassword != null) {
            create("release") {
                storeFile = file(storePath)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
                ?: if (!System.getenv("RELEASE_STORE_FILE").isNullOrBlank()) {
                    error("RELEASE_STORE_FILE is set but release signing is incomplete")
                } else {
                    signingConfigs.getByName("debug")
                }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".testing"
            versionNameSuffix = "-testing.$testingBuild"
            buildConfigField("boolean", "IS_TESTING", "true")
            matchingFallbacks += "release"
        }
    }
}

androidComponents {
    onVariants(selector().withBuildType("staging")) { variant ->
        variant.outputs.forEach { it.versionCode.set(testingVersionCode) }
    }
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.ktor.client.okhttp)
    implementation(platform(libs.composeBom))
    implementation(libs.composeUi)
    implementation(libs.composeFoundation)
    implementation(libs.composeMaterial3)
    implementation(libs.composeMaterialIcons)
    testImplementation(libs.kotlin.test.junit)
    debugImplementation(libs.composeUiTooling)
}

tasks.register<Exec>("installAndLaunchDebug") {
    group = "install"
    description = "Install the debug APK and launch MainActivity"
    dependsOn("installDebug")
    val adb = androidComponents.sdkComponents.adb
    commandLine(
        adb.get().asFile.absolutePath,
        "shell",
        "am",
        "start",
        "-n",
        "app.fukaha/app.fukaha.android.MainActivity",
    )
}
