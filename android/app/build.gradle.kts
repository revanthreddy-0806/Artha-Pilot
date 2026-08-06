plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

import java.io.FileInputStream
import java.util.Properties

// Release signing + production URL are loaded from operator-supplied,
// gitignored configuration. No secrets live in version control.
val signingProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) load(FileInputStream(file))
}
val releaseKeystore = signingProps.getProperty("storeFile")?.takeIf { it.isNotBlank() }
val hasReleaseKeystore = releaseKeystore?.let { rootProject.file(it).exists() } == true
if (hasReleaseKeystore == false) {
    logger.warn("Release signing disabled: android/keystore.properties is missing or has no valid storeFile. Release APKs will be UNSIGNED (not shippable).")
}

fun releaseApiBaseUrl(): String {
    val fromProperty = (project.findProperty("ARTHA_PILOT_API_BASE_URL") as String?)?.takeIf { it.isNotBlank() }
    val fromEnv = System.getenv("ARTHA_PILOT_API_BASE_URL")?.takeIf { it.isNotBlank() }
    return fromProperty ?: fromEnv ?: "https://api.arthapilot.example.com/api/v1/"
}

val resolvedReleaseApiUrl = releaseApiBaseUrl()

android {
    namespace = "com.arthapilot.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.arthapilot.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "3.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(releaseKeystore!!)
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Emulator reaches the host machine via 10.0.2.2. Override for a real device.
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8000/api/v1/\"")
        }

        release {
            // Production HTTPS endpoint; override via ARTHA_PILOT_API_BASE_URL.
            buildConfigField("String", "API_BASE_URL", "\"${resolvedReleaseApiUrl}\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    doLast {
        if (resolvedReleaseApiUrl.contains("example.com")) {
            logger.warn("Release build points at the PLACEHOLDER API URL (${resolvedReleaseApiUrl}). Set ARTHA_PILOT_API_BASE_URL before shipping.")
        }
        if (!resolvedReleaseApiUrl.startsWith("https://")) {
            logger.warn("Release build API URL is not HTTPS: ${resolvedReleaseApiUrl}")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher.android)

    implementation(libs.datastore.preferences)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
