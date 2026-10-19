// Implements :domain's interfaces: Room, DataStore, Firebase functions, Media3 audio.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.mukhda.data"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "USE_FIREBASE_EMULATOR", "false")
        buildConfigField("boolean", "USE_APP_CHECK_DEBUG", "false")
    }
    buildTypes {
        // Debug builds talk to the Firebase emulators on the laptop, over `adb reverse` (see
        // functions/README.md). `-Pmukhda.emulator=false` points a debug build at the real project.
        debug {
            val useEmulator = providers.gradleProperty("mukhda.emulator").orElse("true").get().toBoolean()
            buildConfigField("boolean", "USE_FIREBASE_EMULATOR", useEmulator.toString())
            // `-Pmukhda.appcheck=true` sends a debug App Check token (registered in the console) to
            // the real server; off by default until App Check is switched on at publish.
            val useAppCheck = providers.gradleProperty("mukhda.appcheck").orElse("false").get().toBoolean()
            buildConfigField("boolean", "USE_APP_CHECK_DEBUG", (useAppCheck && !useEmulator).toString())
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(projects.domain)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.serialization.json)

    // Callable functions and the anonymous sign-in they need. No Firestore SDK: the app never
    // reads the database directly.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.functions)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(platform(libs.junit5.bom))
    testImplementation(libs.junit5.jupiter)
    testRuntimeOnly(libs.junit5.platform.launcher)
    testImplementation(libs.assertk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented tests run on a device against real SQLite (FTS4 can't be faked on the JVM).
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.assertk)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
