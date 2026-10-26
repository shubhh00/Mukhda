package com.mukhda.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.mukhda.domain.connectivity.ConnectivityMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Online means the default network has internet and Android has checked that it really reaches it
 * (validated), so a café Wi-Fi behind a sign-in page counts as offline.
 */
@Singleton
class AndroidConnectivityMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ConnectivityMonitor {

    override val isOnline: Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        fun reaches(capabilities: NetworkCapabilities?) =
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(reaches(capabilities))
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        trySend(reaches(manager.getNetworkCapabilities(manager.activeNetwork)))
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged().conflate()
}
