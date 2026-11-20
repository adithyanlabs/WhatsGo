import java.io.FileInputStream
import java.util.Properties

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}
val androidKeystoreFile = System.getenv("ANDROID_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
val androidStorePassword = System.getenv("ANDROID_STORE_PASSWORD")?.takeIf { it.isNotBlank() }
val androidKeyAlias = System.getenv("ANDROID_KEY_ALIAS")?.takeIf { it.isNotBlank() }
val androidKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
val androidSigningEnvironment = listOf(
    androidKeystoreFile,
    androidStorePassword,
    androidKeyAlias,
    androidKeyPassword
)
val hasAndroidSigningEnvironment = androidSigningEnvironment.any { it != null }
val completeAndroidSigningEnvironment = androidSigningEnvironment.all { it != null }
check(!hasAndroidSigningEnvironment || completeAndroidSigningEnvironment) {
    "Set all four ANDROID_* signing environment variables together."
}
val releaseSigningAvailable = keystorePropertiesFile.exists() || completeAndroidSigningEnvironment
val appVersionName = "0.4.2-alpha"
val nightlyBuildNumber = providers.gradleProperty("nightlyBuildNumber").orNull

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.whatsgo.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.whatsgo.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 5
        versionName = nightlyBuildNumber?.let { "$appVersionName.nightly.$it" } ?: appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            } else if (completeAndroidSigningEnvironment) {
                storeFile = file(requireNotNull(androidKeystoreFile))
                storePassword = requireNotNull(androidStorePassword)
                keyAlias = requireNotNull(androidKeyAlias)
                keyPassword = requireNotNull(androidKeyPassword)
            }
        }
    }

    sourceSets {
        getByName("main") {
            java.directories.add("build/generated/ksp/main/kotlin")
        }
        getByName("debug") {
            java.directories.add("build/generated/ksp/debug/kotlin")
        }
        getByName("release") {
            java.directories.add("build/generated/ksp/release/kotlin")
        }
    }

    buildTypes {
        release {
            if (releaseSigningAvailable) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }

            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.compose.runtime.annotation)
    implementation(files("libs/engine.aar"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.zxing.core)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("io.michaelrocks:libphonenumber-android:9.0.40")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
