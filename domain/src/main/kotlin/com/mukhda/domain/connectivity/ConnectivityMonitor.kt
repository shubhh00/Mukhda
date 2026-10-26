package com.mukhda.domain.connectivity

import kotlinx.coroutines.flow.Flow

/** Whether the phone can reach the internet, as it changes. */
interface ConnectivityMonitor {
    /** True while a network that reaches the internet is up. Emits the current state first. */
    val isOnline: Flow<Boolean>
}
