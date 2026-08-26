package com.adgeistkit.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

internal object Connectivity {
    fun connectivityManager(context: Context): ConnectivityManager? =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager

    fun hasValidatedInternet(context: Context): Boolean {
        val manager = connectivityManager(context) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
