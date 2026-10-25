plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing comes from the environment (GitHub Actions secrets) so no
// key material ever lives in the repo. Without it, release builds are unsigned.
val releaseKeystore: String? = System.getenv("DROIDPOD_KEYSTORE")

android {
    namespace = "io.github.krnelpan1c.droidpod"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.krnelpan1c.droidpod"
        // Android 11: consistent media session behavior and package visibility.
        minSdk = 30
        targetSdk = 37
        versionCode = 7
        versionName = "1.2"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("DROIDPOD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("DROIDPOD_KEY_ALIAS")
                keyPassword = System.getenv("DROIDPOD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Lets a dev build sit next to the signed release on the same phone.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "DroidPod Debug"
        }
        release {
            manifestPlaceholders["appLabel"] = "DroidPod"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.media)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
