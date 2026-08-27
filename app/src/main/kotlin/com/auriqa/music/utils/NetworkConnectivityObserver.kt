package com.auriqo.music.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Broadcast connectivity state plus the identity of Android's active default route.
 *
 * A StateFlow is intentional: Channel.receiveAsFlow() load-balances values between collectors,
 * which made independent consumers miss network changes.
 */
class NetworkConnectivityObserver(context: Context) {
    data class RouteIdentity(
        val networkHandle: Long,
        val transportMask: Int,
    )

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkStatus = MutableStateFlow(isCurrentlyConnected())
    val networkStatus: StateFlow<Boolean> = _networkStatus.asStateFlow()

    private val _defaultRoute = MutableStateFlow(currentRouteIdentity())
    val defaultRoute: StateFlow<RouteIdentity?> = _defaultRoute.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            publishCurrentState()
        }

        override fun onLost(network: Network) {
            publishCurrentState()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            publishCurrentState()
        }
    }

    init {
        try {
            // minSdk is 26, so the default-network callback is available everywhere Auriqo runs.
            // Unlike a generic INTERNET request this follows the route actually used by playback.
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        } catch (_: Exception) {
            publishCurrentState()
        }
        publishCurrentState()
    }

    fun unregister() {
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
    }

    fun isCurrentlyConnected(): Boolean {
        return try {
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) {
            false
        }
    }

    private fun publishCurrentState() {
        _networkStatus.value = isCurrentlyConnected()
        _defaultRoute.value = currentRouteIdentity()
    }

    private fun currentRouteIdentity(): RouteIdentity? {
        val network = connectivityManager.activeNetwork ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null
        var mask = 0
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) mask = mask or 1
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) mask = mask or 2
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) mask = mask or 4
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) mask = mask or 8
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) mask = mask or 16
        return RouteIdentity(
            networkHandle = network.networkHandle,
            transportMask = mask,
        )
    }
}
