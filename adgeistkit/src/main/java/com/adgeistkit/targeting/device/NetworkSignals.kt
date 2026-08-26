package com.adgeistkit.targeting.device

import android.content.Context
import android.net.wifi.WifiManager
import java.net.NetworkInterface
import java.util.*

class NetworkSignals(private val context: Context) {

    /**
     * Get the device's local IP address
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress) {
                        val hostAddress = address.hostAddress
                        val isIPv4 = hostAddress?.indexOf(':') ?: -1 < 0
                        if (isIPv4) {
                            return hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Get WiFi IP address
     */
    fun getWifiIpAddress(): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val ipAddress = wifiInfo.ipAddress

        return if (ipAddress != 0) {
            String.format(
                Locale.getDefault(),
                "%d.%d.%d.%d",
                ipAddress and 0xff,
                ipAddress shr 8 and 0xff,
                ipAddress shr 16 and 0xff,
                ipAddress shr 24 and 0xff
            )
        } else null
    }
}
