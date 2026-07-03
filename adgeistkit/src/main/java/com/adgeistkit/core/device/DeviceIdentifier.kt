package com.adgeistkit.core.device

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DeviceIdentifier(private val context: Context) {
    companion object {
        private const val TAG = "DeviceIdentifier"
        const val UNAVAILABLE_DEVICE_ID = "00000000-0000-0000-0000-000000000000"
    }

    @Volatile
    private var cachedDeviceId: String? = null

    /**
     * Initializes the device identifier by fetching the Advertising ID on a background thread.
     * This should be called during SDK initialization.
     */
    fun initialize() {
        CoroutineScope(Dispatchers.IO).launch {
            cachedDeviceId = try {
                val info = AdvertisingIdClient.getAdvertisingIdInfo(context)
                info.id ?: UNAVAILABLE_DEVICE_ID
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch Advertising ID during initialization: ${e.message}")
                UNAVAILABLE_DEVICE_ID
            }
            Log.d(TAG, "Device Identifier initialized: $cachedDeviceId")
        }
    }

    /**
     * Returns the cached device identifier.
     */
    fun getDeviceIdentifier(): String? {
        return cachedDeviceId
    }
}
