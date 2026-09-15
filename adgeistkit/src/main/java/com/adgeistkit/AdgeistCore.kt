package com.adgeistkit

import android.util.Log
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import com.adgeistkit.ads.render.AdCardHtml
import com.adgeistkit.ads.render.AdWebViewFactory
import com.adgeistkit.targeting.TargetingSignals
import com.adgeistkit.targeting.device.DeviceIdentifier
import com.adgeistkit.targeting.device.DeviceSignals
import com.adgeistkit.data.models.Event
import com.adgeistkit.data.models.UserDetails
import com.adgeistkit.data.local.Preferences
import com.adgeistkit.data.network.AnalyticsRetryQueue
import com.adgeistkit.data.network.ConnectionWarmer
import com.adgeistkit.data.network.CreativeAnalytics
import com.adgeistkit.data.network.FetchCreative
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

public class AdgeistCore private constructor(
    internal val context: Context,
    internal val bidRequestBackendDomain: String,
    private val customPackageOrBundleID: String? = null,
    private val customAdgeistAppID: String? = null,
    private val customVersioning: String? = null,
) {
    public companion object {
        private const val TAG = "AdgeistCore"
        private const val BidRequestBackendDomain = com.adgeistkit.BuildConfig.BASE_API_URL

        @Volatile private var instance: AdgeistCore? = null

        @JvmStatic
        @JvmOverloads
        public fun initialize(context: Context,
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
                        logI(TAG) { "AdgeistCore initialized successfully" }

                        if (it.adgeistAppID.isEmpty()) {
                            Log.w(TAG, "WARNING: adgeistAppID is empty. Set com.adgeistkit.ads.ADGEIST_APP_ID in AndroidManifest.xml")
                        }

                        AnalyticsRetryQueue.start(it.context, it.bidRequestBackendDomain)
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "CRITICAL: AdgeistCore initialization failed", e)
                    null
                }
            }
        }

        @JvmStatic
        public fun destroy() {
            synchronized(this) {
                instance?.let {
                    it.ioScope.cancel()
                    AnalyticsRetryQueue.shutdown(it.context)
                }
                instance = null
            }
        }

        @JvmStatic
        public fun getInstance(): AdgeistCore {
            return instance ?: run {
                Log.e(TAG, "ERROR: AdgeistCore not initialized")
                throw IllegalStateException("AdgeistCore is not initialized. Call AdgeistCore.initialize() first.")
            }
        }

        @JvmStatic
        public fun isInitialized(): Boolean {
            return instance != null
        }
    }

    public val packageOrBundleID: String = customPackageOrBundleID ?: context.packageName
    public val adgeistAppID: String = customAdgeistAppID ?: getMetaValue("com.adgeistkit.ads.ADGEIST_APP_ID") ?: ""
    public val version: String = customVersioning ?: "ANDROID-${com.adgeistkit.BuildConfig.VERSION_NAME}"

    internal val isHostAppDebuggable: Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private val preferences = Preferences(context)

    @Volatile
    private var consentGiven: Boolean = false

    @Volatile
    private var performanceTelemetryEnabled: Boolean = true

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

    internal val deviceSignals = DeviceSignals(context)
    internal val deviceIdentifier = DeviceIdentifier(context, preferences)
    internal var targetingInfo: Map<String, Any?>? = null

    @Volatile
    private var userDetails: UserDetails? = null

    init {
        AdgeistLog.enabled = isHostAppDebuggable

        consentGiven = preferences.consentGiven()
        performanceTelemetryEnabled = preferences.performanceTelemetryEnabled()

        try {
            targetingInfo = TargetingSignals(deviceSignals).getTargetingInfo()
        } catch (_: Throwable) {
        }

        Handler(Looper.getMainLooper()).post {
            AdWebViewFactory.warmup(context)
        }

        ioScope.launch { deviceIdentifier.getDeviceIdentifier() }
        ioScope.launch { ConnectionWarmer.warm(bidRequestBackendDomain) }
        ioScope.launch { AdCardHtml.build(context.assets) }
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

    public fun setUserDetails(details: UserDetails) {
        userDetails = details
    }

    @ExperimentalAdgeistApi
    public fun updateConsentStatus(consentGiven: Boolean) {
        this.consentGiven = consentGiven
        preferences.setConsentGiven(consentGiven)
    }

    @ExperimentalAdgeistApi
    public fun getConsentStatus(): Boolean {
        return consentGiven
    }

    @ExperimentalAdgeistApi
    public fun setPerformanceTelemetryEnabled(enabled: Boolean) {
        performanceTelemetryEnabled = enabled
        preferences.setPerformanceTelemetryEnabled(enabled)
    }

    public fun isPerformanceTelemetryEnabled(): Boolean {
        return performanceTelemetryEnabled
    }

    public fun getCreative(): FetchCreative {
        return FetchCreative(AdgeistCore.getInstance())
    }

    public fun postCreativeAnalytics(): CreativeAnalytics {
        return CreativeAnalytics(AdgeistCore.getInstance())
    }

    @ExperimentalAdgeistApi
    public fun logEvent(event: Event) {
        ioScope.launch {
            val localUserDetails = userDetails
            val parameters = mutableMapOf<String, Any>()

            event.eventProperties?.forEach { (key, value) -> if (value != null) parameters[key] = value }
            if (localUserDetails != null) {
                parameters["userDetails"] = localUserDetails
            }

            @Suppress("UNUSED_VARIABLE")
            val fullEvent = event.copy(eventProperties = parameters)
        }
    }

    public fun hasPhoneStatePermission(): Boolean {
        return DeviceSignals.hasPhoneStatePermission(context)
    }
}