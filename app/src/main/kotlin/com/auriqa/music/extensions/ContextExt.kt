

package com.auriqo.music.extensions

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.music.innertube.utils.parseCookieString
import com.auriqo.music.constants.InnerTubeCookieKey
import com.auriqo.music.constants.YtmSyncKey
import com.auriqo.music.utils.dataStore
import com.auriqo.music.utils.read

suspend fun Context.isSyncEnabled(): Boolean =
    dataStore.read(YtmSyncKey, true) && isUserLoggedIn()

suspend fun Context.isUserLoggedIn(): Boolean {
    val cookie = dataStore.read(InnerTubeCookieKey, "")
    return "SAPISID" in parseCookieString(cookie) && isInternetConnected()
}

fun Context.isInternetConnected(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
    return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
}
