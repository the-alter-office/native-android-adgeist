package com.adgeistkit.data.local

import android.content.Context
import android.content.SharedPreferences

internal class Preferences(context: Context) {

    companion object {
        private const val PREFS_NAME = "AdgeistPrefs"

        private const val KEY_CONSENT = "adgeist_consent"
        private const val KEY_PERFORMANCE_TELEMETRY = "adgeist_performance_telemetry"
        private const val KEY_FALLBACK_DEVICE_ID = "adgeist_fallback_device_id"
    }

    private val prefs: SharedPreferences? = try {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    } catch (_: Throwable) {
        null
    }

    fun consentGiven(): Boolean = getBoolean(KEY_CONSENT, false)

    fun setConsentGiven(value: Boolean) = putBoolean(KEY_CONSENT, value)

    fun performanceTelemetryEnabled(): Boolean = getBoolean(KEY_PERFORMANCE_TELEMETRY, true)

    fun setPerformanceTelemetryEnabled(value: Boolean) =
        putBoolean(KEY_PERFORMANCE_TELEMETRY, value)

    fun fallbackDeviceId(): String? = try {
        prefs?.getString(KEY_FALLBACK_DEVICE_ID, null)
    } catch (_: Exception) {
        null
    }

    private fun getBoolean(key: String, fallback: Boolean): Boolean = try {
        prefs?.getBoolean(key, fallback) ?: fallback
    } catch (_: Exception) {
        fallback
    }

    fun setFallbackDeviceId(value: String) {
        try {
            prefs?.edit()?.putString(KEY_FALLBACK_DEVICE_ID, value)?.apply()
        } catch (_: Exception) {
        }
    }

    private fun putBoolean(key: String, value: Boolean) {
        try {
            prefs?.edit()?.putBoolean(key, value)?.apply()
        } catch (_: Exception) {
        }
    }
}
