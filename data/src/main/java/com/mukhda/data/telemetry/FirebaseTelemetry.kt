package com.mukhda.data.telemetry

import android.content.Context
import android.os.Build
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.mukhda.domain.AppError
import com.mukhda.domain.show.TryError
import com.mukhda.domain.telemetry.AppGate
import com.mukhda.domain.telemetry.FeatureFlags
import com.mukhda.domain.telemetry.Telemetry
import com.mukhda.domain.telemetry.TelemetryEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Events to Firebase Analytics, crash context and handled errors to Crashlytics. Both are off in
 * debug builds (see the app's manifest), so testing doesn't mix with players' numbers.
 */
@Singleton
class FirebaseTelemetry @Inject constructor(
    private val analytics: FirebaseAnalytics,
    private val crashlytics: FirebaseCrashlytics,
) : Telemetry {

    override fun log(event: TelemetryEvent) {
        val (name, params) = when (event) {
            is TelemetryEvent.ClipPlayed -> "clip_played" to Bundle().apply {
                putString("tier", event.tier.name)
                putLong("clip_step", event.step.toLong())
            }
            is TelemetryEvent.RoundFinished -> "round_finished" to verdictParams(event.tier.name, event.verdict.name)
            is TelemetryEvent.TicketOpened -> "ticket_opened" to verdictParams(event.tier.name, event.verdict.name)
            is TelemetryEvent.TicketShared -> "ticket_shared" to verdictParams(event.tier.name, event.verdict.name)
            is TelemetryEvent.HousefullOpened -> "housefull_opened" to Bundle().apply { putLong("streak", event.streak.toLong()) }
            is TelemetryEvent.AppleMusicOpened -> "apple_music_opened" to Bundle().apply { putString("tier", event.tier.name) }
        }
        analytics.logEvent(name, params)
    }

    override fun setContext(key: String, value: String) = crashlytics.setCustomKey(key, value)

    override fun recordError(where: String, error: AppError) {
        crashlytics.recordException(HandledError(where, error))
    }

    private fun verdictParams(tier: String, verdict: String) = Bundle().apply {
        putString("tier", tier)
        putString("verdict", verdict)
    }
}

/** A handled error, as Crashlytics records it: grouped by where it happened and what it was. */
class HandledError(where: String, error: AppError) : Exception("$where: ${error.describe()}")

private fun AppError.describe(): String = when (this) {
    is TryError.Data -> "TryError.Data(${error.describe()})"
    is TryError.Rule -> "TryError.Rule($error)"
    is Enum<*> -> "${javaClass.simpleName}.$name"
    else -> toString()
}

/**
 * Switches from Firebase Remote Config, off until turned on in the console. The values fetched
 * last time apply from launch, so a paused game stays paused through a relaunch; they're fetched
 * again each launch, and a change made in the console reaches open apps within moments (Remote
 * Config's real-time updates), which is what a kill switch and a forced update need.
 */
@Singleton
class RemoteConfigFeatureFlags @Inject constructor(
    private val config: FirebaseRemoteConfig,
    @param:ApplicationContext context: Context,
) : FeatureFlags {
    private val versionCode: Long = context.packageManager.getPackageInfo(context.packageName, 0).let { info ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }
    private val topPercent = MutableStateFlow(false)
    override val showTopPercent: Flow<Boolean> = topPercent.asStateFlow()
    private val currentGate = MutableStateFlow(AppGate.OPEN)
    override val gate: Flow<AppGate> = currentGate.asStateFlow()

    init {
        config.setConfigSettingsAsync(remoteConfigSettings { minimumFetchIntervalInSeconds = FETCH_INTERVAL_S })
        config.setDefaultsAsync(mapOf(TOP_PERCENT to false, MIN_VERSION_CODE to 0L, PAUSED to false))
        publish()
        config.fetchAndActivate().addOnCompleteListener { publish() }
        config.addOnConfigUpdateListener(
            object : ConfigUpdateListener {
                override fun onUpdate(configUpdate: ConfigUpdate) {
                    config.activate().addOnCompleteListener { publish() }
                }

                // The SDK keeps trying to listen; the values from launch still apply meanwhile.
                override fun onError(error: FirebaseRemoteConfigException) = Unit
            },
        )
    }

    override suspend fun refresh() {
        // Asked for by the player ("Try again"), so past the usual interval; a refused or failed
        // fetch leaves the values as they were.
        runCatching {
            config.fetch(0).await()
            config.activate().await()
        }
        publish()
    }

    private fun publish() {
        topPercent.value = config.getBoolean(TOP_PERCENT)
        currentGate.value = when {
            versionCode < config.getLong(MIN_VERSION_CODE) -> AppGate.UPDATE_REQUIRED
            config.getBoolean(PAUSED) -> AppGate.PAUSED
            else -> AppGate.OPEN
        }
    }

    private companion object {
        const val TOP_PERCENT = "show_top_percent"
        /** Builds with a lower version code must update to play. */
        const val MIN_VERSION_CODE = "min_version_code"
        /** The kill switch: true closes the game for everyone. */
        const val PAUSED = "paused"
        /** Remote Config's own throttling allows this; a launch a day needs no more. */
        const val FETCH_INTERVAL_S = 3_600L
    }
}
