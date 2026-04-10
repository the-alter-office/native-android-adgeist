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
    }

    @Volatile
    private var cachedDeviceId: String? = null

    /**
     * Initializes the device identifier by fetching the Advertising ID on a background thread.
     * This should be called during SDK initialization.
     */
    fun initialize() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val info = AdvertisingIdClient.getAdvertisingIdInfo(context)
                cachedDeviceId = info.id
                Log.d(TAG, "Device Identifier initialized: $cachedDeviceId")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch Advertising ID during initialization: ${e.message}")
            }
        }
    }

    /**
     * Returns the cached device identifier.
     * Returns null if not yet initialized or if fetching failed.
     */
    fun getDeviceIdentifier(): String? {
        return cachedDeviceId
    }
}
