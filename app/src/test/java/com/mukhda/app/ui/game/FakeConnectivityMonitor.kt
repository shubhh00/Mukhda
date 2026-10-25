package com.mukhda.app.ui.game

import com.mukhda.domain.connectivity.ConnectivityMonitor
import kotlinx.coroutines.flow.MutableStateFlow

/** Online until a test says otherwise. */
class FakeConnectivityMonitor : ConnectivityMonitor {
    override val isOnline = MutableStateFlow(true)
}
