package com.adgeistkit

import android.util.Log
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import com.adgeistkit.core.TargetingOptions
import com.adgeistkit.core.device.DeviceIdentifier
import com.adgeistkit.core.device.DeviceMeta
import com.adgeistkit.core.device.NetworkUtils
import com.adgeistkit.data.models.Event
import com.adgeistkit.data.models.UserDetails
import com.adgeistkit.data.network.CreativeAnalytics
import com.adgeistkit.data.network.FetchCreative
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AdgeistCore private constructor(
    private val context: Context,
    val bidRequestBackendDomain: String,
    private val customPackageOrBundleID: String? = null,
    private val customAdgeistAppID: String? = null,
    private val customVersioning: String? = null,
) {
    companion object {
        private const val TAG = "AdgeistCore"
        private const val BidRequestBackendDomain = com.adgeistkit.BuildConfig.BASE_API_URL
        @Volatile private var instance: AdgeistCore? = null
        private val lock = Any()

        @JvmStatic
        @JvmOverloads
        fun initialize(context: Context,
                       customBidRequestBackendDomain: String? = null,
                       customPackageOrBundleID : String? = null,
                       customAdgeistAppID : String? = null,
                       customVersioning: String? = null): AdgeistCore?
        {
            return instance ?: synchronized(this) {
                instance ?: try {
                    AdgeistCore(
                        context.applicationContext,
                        customBidRequestBackendDomain ?: BidRequestBackendDomain,
                        customPackageOrBundleID,
                        customAdgeistAppID,
                        customVersioning,
                    ).also {
                        instance = it
                        Log.i(TAG, "AdgeistCore initialized successfully")

                        // Validate critical configuration after successful initialization
                        if (it.adgeistAppID.isEmpty()) {
                            Log.w(TAG, "WARNING: adgeistAppID is empty. Set com.adgeistkit.ads.ADGEIST_APP_ID in AndroidManifest.xml")
                        }
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "CRITICAL: AdgeistCore initialization failed", e)
                    null
                }
            }
        }

        @JvmStatic
        fun destroy() {
            synchronized(lock) {
                instance?.ioScope?.cancel()
                instance = null
            }
        }

        @JvmStatic
        fun getInstance(): AdgeistCore {
            return instance ?: run {
                Log.e(TAG, "ERROR: AdgeistCore not initialized")
                throw IllegalStateException("AdgeistCore is not initialized. Call AdgeistCore.initialize() first.")
            }
        }
        
        /**
         * Check if AdgeistCore has been initialized
         */
        @JvmStatic
        fun isInitialized(): Boolean {
            return instance != null
        }
    }

    val packageOrBundleID = customPackageOrBundleID ?: context.packageName
    val adgeistAppID = customAdgeistAppID ?: getMetaValue("com.adgeistkit.ads.ADGEIST_APP_ID") ?: ""
    val version = customVersioning ?: "ANDROID-${com.adgeistkit.BuildConfig.VERSION_NAME}"

    private val PREFS_NAME = "AdgeistPrefs"
    private var prefs: SharedPreferences? = null

    private val KEY_CONSENT = "adgeist_consent"
    private var consentGiven: Boolean = false

    /**
     * Single scope for all SDK background work, cancelled in [destroy]. SupervisorJob keeps one
     * failed coroutine from cancelling the rest; the handler stops uncaught exceptions from
     * propagating to the host app's uncaught-exception handler.
     */
    internal val ioScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "Uncaught exception in SDK coroutine", e)
        }
    )

    val deviceMeta = DeviceMeta(context)
    val deviceIdentifier = DeviceIdentifier(context)
    val networkUtils = NetworkUtils(context)
    var targetingInfo: Map<String, Any?>? = null

    private var userDetails: UserDetails? = null

    init {
        try {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            consentGiven = prefs?.getBoolean(KEY_CONSENT, false) ?: false
        } catch (e: Throwable) {
            Log.e(TAG, "Non-fatal: failed to read AdGeist preferences", e)
        }

        try {
            targetingInfo = TargetingOptions(context).getTargetingInfo()
        } catch (e: Throwable) {
            Log.e(TAG, "Non-fatal: failed to collect device targeting info", e)
        }
    }

    private fun getMetaValue(key: String): String? {
        try {
            val context = context

            val ai = context.packageManager
                .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)

            val bundle = ai.metaData
            return bundle?.getString(key)
        } catch (e: Exception) {
            return null
        }
    }

    @Synchronized
    fun setUserDetails(details: UserDetails) {
        userDetails = details
    }

    fun updateConsentStatus(consentGiven: Boolean) {
        this.consentGiven = consentGiven
        try {
            prefs?.edit()?.putBoolean(KEY_CONSENT, consentGiven)?.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist consent status", e)
        }
    }

    fun getConsentStatus(): Boolean {
        return consentGiven
    }

    fun getCreative(): FetchCreative {
        return FetchCreative(AdgeistCore.getInstance())
    }

    fun postCreativeAnalytics(): CreativeAnalytics {
        return CreativeAnalytics(AdgeistCore.getInstance())
    }

    fun logEvent(event: Event) {
        ioScope.launch {
            val localUserDetails = userDetails
            val parameters = mutableMapOf<String, Any>()
            event.eventProperties?.forEach { (key, value) -> if (value != null) parameters[key] = value }
            
            if (localUserDetails != null) {
                parameters["userDetails"] = localUserDetails
            }
            val fullEvent = event.copy(eventProperties = parameters)
        }
    }

    fun hasPhoneStatePermission(): Boolean {
        return DeviceMeta.hasPhoneStatePermission(context)
    }

}