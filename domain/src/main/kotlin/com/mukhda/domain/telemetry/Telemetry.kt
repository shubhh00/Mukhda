package com.mukhda.domain.telemetry

import com.mukhda.domain.AppError
import com.mukhda.domain.model.Tier
import com.mukhda.domain.model.Verdict
import kotlinx.coroutines.flow.Flow

/**
 * What the app reports about itself: a few events for usage numbers, context that crash reports
 * carry, and errors it handled. Never a song, film or anything that could give away an answer
 * (AGENTS.md §15). Game results themselves come from the server's `songStats`.
 */
interface Telemetry {
    fun log(event: TelemetryEvent)

    /** Kept with any crash report from here on: say, the tier being played. */
    fun setContext(key: String, value: String)

    /** An error the app handled, reported without crashing: [where] says what was being done. */
    fun recordError(where: String, error: AppError)
}

sealed interface TelemetryEvent {
    data class ClipPlayed(val tier: Tier, val step: Int) : TelemetryEvent
    data class RoundFinished(val tier: Tier, val verdict: Verdict) : TelemetryEvent
    data class TicketOpened(val tier: Tier, val verdict: Verdict) : TelemetryEvent
    data class TicketShared(val tier: Tier, val verdict: Verdict) : TelemetryEvent
    data class HousefullOpened(val streak: Int) : TelemetryEvent
    data class AppleMusicOpened(val tier: Tier) : TelemetryEvent
}

/** Switches the app reads from the server, so a feature can be turned on without an update. */
interface FeatureFlags {
    /** "Top X% today" on the result screen and the ticket. Off until it's turned on. */
    val showTopPercent: Flow<Boolean>
}
