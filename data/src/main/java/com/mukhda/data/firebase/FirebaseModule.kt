package com.mukhda.data.firebase

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.functions.FirebaseFunctions
import com.mukhda.data.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    /** Same region as the functions (AGENTS.md §2). */
    private const val REGION = "asia-south1"

    /**
     * The laptop, as the phone sees it through `adb reverse tcp:9099 tcp:9099` and
     * `adb reverse tcp:5001 tcp:5001`. Only debug builds ever look there.
     */
    private const val EMULATOR_HOST = "127.0.0.1"

    @Provides
    @Singleton
    fun auth(): FirebaseAuth = FirebaseAuth.getInstance().apply {
        if (BuildConfig.USE_FIREBASE_EMULATOR) useEmulator(EMULATOR_HOST, 9099)
    }

    @Provides
    @Singleton
    fun analytics(@ApplicationContext context: Context): FirebaseAnalytics = FirebaseAnalytics.getInstance(context)

    @Provides
    @Singleton
    fun crashlytics(): FirebaseCrashlytics = FirebaseCrashlytics.getInstance()

    @Provides
    @Singleton
    fun remoteConfig(): FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    @Provides
    @Singleton
    fun functions(): FirebaseFunctions = FirebaseFunctions.getInstance(REGION).apply {
        if (BuildConfig.USE_FIREBASE_EMULATOR) useEmulator(EMULATOR_HOST, 5001)
    }
}
