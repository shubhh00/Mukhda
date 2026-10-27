package com.mukhda.app.ui.game

import com.mukhda.domain.AppError
import com.mukhda.domain.telemetry.AppGate
import com.mukhda.domain.telemetry.FeatureFlags
import com.mukhda.domain.telemetry.Telemetry
import com.mukhda.domain.telemetry.TelemetryEvent
import kotlinx.coroutines.flow.MutableStateFlow

/** Keeps what the app reported, for tests to check. */
class FakeTelemetry : Telemetry {
    val events = mutableListOf<TelemetryEvent>()
    val context = mutableMapOf<String, String>()
    val errors = mutableListOf<Pair<String, AppError>>()

    override fun log(event: TelemetryEvent) {
        events += event
    }

    override fun setContext(key: String, value: String) {
        context[key] = value
    }

    override fun recordError(where: String, error: AppError) {
        errors += where to error
    }
}

/** Switches a test sets directly. */
class FakeFeatureFlags : FeatureFlags {
    override val showTopPercent = MutableStateFlow(false)
    override val gate = MutableStateFlow(AppGate.OPEN)
    var refreshes = 0
        private set

    override suspend fun refresh() {
        refreshes++
    }
}
