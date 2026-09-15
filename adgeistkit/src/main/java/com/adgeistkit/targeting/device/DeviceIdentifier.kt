package com.adgeistkit.targeting.device

import android.content.Context
import android.util.Log
import com.adgeistkit.data.local.Preferences
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

class DeviceIdentifier internal constructor(
    private val context: Context,
    private val preferences: Preferences,
) {
    companion object {
        private const val TAG = "DeviceIdentifier"
        private const val ZEROED_AD_ID = "00000000-0000-0000-0000-000000000000"

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
        } catch (e: IOException) {
            Log.w(TAG, "Failed to get Advertising ID: ${e.message}")
            null
        } catch (e: GooglePlayServicesNotAvailableException) {
            Log.w(TAG, "Failed to get Advertising ID: ${e.message}")
            null
        } catch (e: GooglePlayServicesRepairableException) {
            Log.w(TAG, "Failed to get Advertising ID: ${e.message}")
            null
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Failed to get Advertising ID: ${e.message}")
            null
        }
    }

    private suspend fun getOrCreateFallbackId(): String? {
        return try {
            withContext(Dispatchers.IO) {
                // Concurrent ad fetches on a fresh install would otherwise persist two IDs.
                synchronized(fallbackLock) {
                    val existing = preferences.fallbackDeviceId()
                    if (isWellFormedId(existing)) {
                        existing
                    } else {
                        UUID.randomUUID().toString().also {
                            preferences.setFallbackDeviceId(it)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Failed to read or create fallback device ID: ${e.message}")
            null
        }
    }

    suspend fun getDeviceIdentifier(): String? {
        cachedId?.let { return it }
        return try {
            resolveMutex.withLock {
                // Re-check: a concurrent caller may have resolved while we waited for the lock.
                cachedId?.let { return@withLock it }
                val adId = getAdvertisingId()
                if (adId != null) {
                    cachedId = adId
                    adId
                } else {
                    // Not cached: the ad ID may only be transiently unavailable
                    // (e.g. Play Services still binding at cold start), so it is
                    // re-attempted on the next call. The fallback stays stable
                    // across calls because it is persisted in prefs.
                    getOrCreateFallbackId()
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Failed to resolve device identifier: ${e.message}")
            null
        }
    }

    fun invalidate() {
        cachedId = null
    }
}