package com.mukhda.data.telemetry

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.mukhda.domain.AppError
import com.mukhda.domain.show.TryError
import com.mukhda.domain.telemetry.FeatureFlags
import com.mukhda.domain.telemetry.Telemetry
import com.mukhda.domain.telemetry.TelemetryEvent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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
 * Switches from Firebase Remote Config, off until fetched and turned on in the console. Fetched
 * once a launch; a value changed in the console reaches players on their next launch.
 */
@Singleton
class RemoteConfigFeatureFlags @Inject constructor(config: FirebaseRemoteConfig) : FeatureFlags {
    private val topPercent = MutableStateFlow(false)
    override val showTopPercent: Flow<Boolean> = topPercent.asStateFlow()

    init {
        config.setConfigSettingsAsync(remoteConfigSettings { minimumFetchIntervalInSeconds = FETCH_INTERVAL_S })
        config.setDefaultsAsync(mapOf(TOP_PERCENT to false))
        config.fetchAndActivate().addOnCompleteListener { topPercent.value = config.getBoolean(TOP_PERCENT) }
    }

    private companion object {
        const val TOP_PERCENT = "show_top_percent"
        /** Remote Config's own throttling allows this; a launch a day needs no more. */
        const val FETCH_INTERVAL_S = 3_600L
    }
}
