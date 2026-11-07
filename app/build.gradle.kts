plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose) // Keep Compose compiler
    alias(libs.plugins.ksp)            // Keep KSP for Room
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
        versionCode = 1
        versionName = "0.1.0-alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    implementation(files("libs/engine.aar"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.zxing.core)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
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
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}