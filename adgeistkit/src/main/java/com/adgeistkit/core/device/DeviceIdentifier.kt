package com.adgeistkit.core.device

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeviceIdentifier(private val context: Context) {
    companion object {
        private const val TAG = "DeviceIdentifier"
    }

    private suspend fun getAdvertisingId(): String? {
        return try {
            val adId = withContext(Dispatchers.IO) {
                AdvertisingIdClient.getAdvertisingIdInfo(context).id
            }

            adId
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get Advertising ID: ${e.message}")
            return null
        }
    }

    // Public method
    suspend fun getDeviceIdentifier(): String? {
        return try {
            getAdvertisingId()
        } catch (e: Exception) {
            return null
        }
    }
}