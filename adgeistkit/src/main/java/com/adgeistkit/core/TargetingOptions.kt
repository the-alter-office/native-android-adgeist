package com.adgeistkit.core

import android.content.Context
import com.adgeistkit.core.device.DeviceIdentifier
import com.adgeistkit.core.device.DeviceMeta
import com.adgeistkit.core.device.NetworkUtils

class TargetingOptions(private val context: Context) {
    fun getTargetingInfo(): Map<String, Any?> {
        val deviceMeta = DeviceMeta(context)
        val networkUtils = NetworkUtils(context)

        val meta = deviceMeta.getAllDeviceInfo()
        val ipAddress = networkUtils.getLocalIpAddress() ?: networkUtils.getWifiIpAddress()

        return mapOf(
            "meta" to meta,
            "ipAddress" to ipAddress
        )
    }
}
