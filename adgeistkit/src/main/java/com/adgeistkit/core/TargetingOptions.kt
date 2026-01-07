package com.adgeistkit.core

import android.content.Context
import com.adgeistkit.core.device.DeviceIdentifier
import com.adgeistkit.core.device.DeviceMeta
import com.adgeistkit.core.device.NetworkUtils

class TargetingOptions(private val context: Context) {
    fun getTargetingInfo(): Map<String, Any?> {
        val deviceMeta = DeviceMeta(context)

        val deviceTargetingMetrics = deviceMeta.getAllDeviceInfo()

        return mapOf(
            "deviceTargetingMetrics" to deviceTargetingMetrics,
        )
    }
}
