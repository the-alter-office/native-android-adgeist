package com.adgeistkit.core.device

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class DeviceIdentifier(private val context: Context) {
    companion object {
        private const val TAG = "DeviceIdentifier"
    }

    // Priority 1: Advertising ID (for ads tracking)
    private suspend fun getAdvertisingId(): String {
        return try {
            val adId = withContext(Dispatchers.IO) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                    AdvertisingIdClient.getAdvertisingIdInfo(context).id
                } else {
                    null
                }
            }
            if (adId.isNullOrEmpty()) {
                getAndroidId()
            } else {
                adId
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get Advertising ID: ${e.message}")
            getAndroidId()
        }
    }

    // Priority 2: Android Device ID (persistent but not for ads)
    private fun getAndroidId(): String {
        return try {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            if (androidId.isNullOrEmpty()) {
                getOrCreateAppInstallId()
            } else {
                androidId
            }
        } catch (e: Exception) {
            getOrCreateAppInstallId()
        }
    }

    // Priority 3: Generated fingerprint (fallback)
    private fun getOrCreateAppInstallId(): String {
        return try {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.getString("install_id", null) ?: run {
                val newId = UUID.randomUUID().toString()
                prefs.edit().putString("install_id", newId).apply()
                newId
            }.also { id ->
                if (id != null) Log.i(TAG, "Using existing install ID: $id")
            }
        } catch (e: Exception) {
            val fallbackId = "fallback_${System.currentTimeMillis()}"
            fallbackId
        }
    }

    // Public method
    suspend fun getDeviceIdentifier(): String {
        return try {
            getAdvertisingId()
        } catch (e: Exception) {
            getOrCreateAppInstallId()
        }
    }
}