package com.adgeistkit.core.device

import android.content.Context
import android.util.Log
import com.adgeistkit.AdgeistCore
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

class DeviceIdentifier(private val context: Context) {
    companion object {
        private const val TAG = "DeviceIdentifier"
        private const val ZEROED_AD_ID = "00000000-0000-0000-0000-000000000000"

        private const val KEY_FALLBACK_ID = "adgeist_fallback_device_id"

        private val fallbackLock = Any()

        internal fun isUsableAdId(id: String?, limitAdTracking: Boolean): Boolean =
            !limitAdTracking && !id.isNullOrBlank() && id != ZEROED_AD_ID

        internal fun isWellFormedId(id: String?): Boolean {
            if (id.isNullOrBlank()) return false
            return try {
                UUID.fromString(id).toString() == id
            } catch (_: IllegalArgumentException) {
                false
            }
        }
    }

    @Volatile
    private var cachedId: String? = null

    private val resolveMutex = Mutex()

    private suspend fun getAdvertisingId(): String? {
        return try {
            withContext(Dispatchers.IO) {
                val info = AdvertisingIdClient.getAdvertisingIdInfo(context)
                info.id.takeIf { isUsableAdId(it, info.isLimitAdTrackingEnabled) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get Advertising ID: ${e.message}")
            null
        }
    }

    private suspend fun getOrCreateFallbackId(): String? {
        return try {
            withContext(Dispatchers.IO) {
                val prefs = context.getSharedPreferences(
                    AdgeistCore.PREFS_NAME,
                    Context.MODE_PRIVATE
                )
                // Concurrent ad fetches on a fresh install would otherwise persist two IDs.
                synchronized(fallbackLock) {
                    val existing = prefs.getString(KEY_FALLBACK_ID, null)
                    if (isWellFormedId(existing)) {
                        existing
                    } else {
                        UUID.randomUUID().toString().also {
                            prefs.edit().putString(KEY_FALLBACK_ID, it).apply()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read or create fallback device ID: ${e.message}")
            null
        }
    }

    suspend fun getDeviceIdentifier(): String? {
        cachedId?.let { return it }
        return try {
            resolveMutex.withLock {
                // Re-check: a concurrent caller may have resolved while we waited for the lock.
                cachedId ?: (getAdvertisingId() ?: getOrCreateFallbackId())?.also { cachedId = it }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve device identifier: ${e.message}")
            null
        }
    }

    fun invalidate() {
        cachedId = null
    }
}