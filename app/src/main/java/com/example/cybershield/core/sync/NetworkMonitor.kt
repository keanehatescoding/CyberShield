package com.example.cybershield.core.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMonitor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        /** True when the device has an active internet-capable network. */
        val isOnline: Flow<Boolean> =
            callbackFlow {
                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            trySend(true)
                        }

                        override fun onLost(network: Network) {
                            trySend(false)
                        }

                        override fun onUnavailable() {
                            trySend(false)
                        }
                    }

                val request =
                    NetworkRequest
                        .Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        .build()

                connectivityManager.registerNetworkCallback(request, callback)

                // Emit current state immediately on collection
                trySend(isCurrentlyOnline())

                awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
            }.distinctUntilChanged()

        /** One-shot check — use in WorkManager tasks before attempting sync. */
        fun isCurrentlyOnline(): Boolean {
            val network = connectivityManager.activeNetwork ?: return false
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    }
