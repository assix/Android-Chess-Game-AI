import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Optional fixed signing key: if a (git-ignored) keystore.properties file
// exists at the repo root, debug and release builds are signed with that
// key, so every build installs OVER the previous one instead of failing
// with a signature mismatch whenever the build machine changes. Without
// the file, builds fall back to the standard Android debug key.
val keystoreProps = Properties()
val keystorePropsFile = rootProject.file("keystore.properties")
if (keystorePropsFile.exists()) {
    keystorePropsFile.inputStream().use { keystoreProps.load(it) }
}

android {
    namespace = "com.example.myapkchess"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.myapkchess"
        minSdk = 24
        targetSdk = 36
        versionCode = 6
        versionName = "0.8.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("pinned") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("pinned")
            }
        }
        release {
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("pinned")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // Stockfish ships as a "native library" (libstockfish.so) so Android
    // extracts it to the executable nativeLibraryDir; legacy packaging is
    // what guarantees it lands on disk as a runnable file.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}