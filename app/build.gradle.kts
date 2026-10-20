// Screens, ViewModels, theme, navigation and Hilt setup.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// google-services.json comes from the Firebase console and is not committed.
// Without it the app still builds; Firebase calls fail at runtime until it's added.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

/**
 * `-Pmukhda.appcheck=true` gives debug builds App Check with a debug token, for testing the real
 * server once it requires App Check. Otherwise debug builds carry no App Check at all.
 */
val debugAppCheck = providers.gradleProperty("mukhda.appcheck").orElse("false").get().toBoolean()

android {
    namespace = "com.mukhda.app"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.mukhda.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        // Release code, debug key: installs on a dev phone to judge real performance.
        // Debug builds of Compose are far slower, so never judge smoothness from them.
        create("profile") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
    sourceSets {
        getByName("debug") {
            kotlin.srcDir(if (debugAppCheck) "src/debugAppCheck/java" else "src/debugNoAppCheck/java")
        }
    }
}

dependencies {
    implementation(projects.domain)
    implementation(projects.data)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Album art (soundtrack covers) on the reveal.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.viewmodel.compose)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    // App Check: Play Integrity proves a release build is the genuine app from Play; debug builds
    // use a debug token registered in the Firebase console instead. Only the builds that use App
    // Check carry it: without a provider the SDK still fetches (and fails) a token before every call.
    releaseImplementation(libs.firebase.appcheck.playintegrity)
    "profileImplementation"(libs.firebase.appcheck.playintegrity)
    if (debugAppCheck) debugImplementation(libs.firebase.appcheck.debug)

    testImplementation(platform(libs.junit5.bom))
    testImplementation(libs.junit5.jupiter)
    testRuntimeOnly(libs.junit5.platform.launcher)
    testImplementation(libs.assertk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
