package com.adgeistkit.logging

import android.app.ActivityManager
import android.content.Context
import android.content.res.Resources
import java.util.Locale
import java.util.TimeZone

class AdditionalTemporaryDeviceInfo(private val context: Context) {

    private val activityManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    fun getAvailableMemoryBytes(): Long {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.availMem
    }

    fun getTotalMemoryBytes(): Long {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.totalMem
    }

    fun isLowMemory(): Boolean {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.lowMemory
    }

    fun getLocale(): String {
        return Locale.getDefault().toString()
    }

    fun getTimezone(): String {
        return TimeZone.getDefault().id
    }

    fun getAll(): Map<String, Any> {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        return mapOf(
            "availableMemoryBytes" to memInfo.availMem,
            "totalMemoryBytes" to memInfo.totalMem,
            "isLowMemory" to memInfo.lowMemory,
            "locale" to Locale.getDefault().toString(),
            "timezone" to TimeZone.getDefault().id
        )
    }
}
