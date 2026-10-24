// Generates the app's Baseline Profile and measures its startup. Runs on a connected phone:
//   ./gradlew :app:generateReleaseBaselineProfile        (writes the profile into :app)
//   ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest  (startup, with and without it)
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.mukhda.baselineprofile"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        // Collecting a profile needs Android 9 or later.
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    targetProjectPath = ":app"
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
