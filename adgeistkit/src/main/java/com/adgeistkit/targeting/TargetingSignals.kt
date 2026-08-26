package com.adgeistkit.targeting

import com.adgeistkit.targeting.device.DeviceSignals

class TargetingSignals(private val deviceSignals: DeviceSignals) {
    fun getTargetingInfo(): Map<String, Any?> {
        val deviceTargetingMetrics = deviceSignals.getAllDeviceInfo()

        return mapOf(
            "deviceTargetingMetrics" to deviceTargetingMetrics,
        )
    }
}
