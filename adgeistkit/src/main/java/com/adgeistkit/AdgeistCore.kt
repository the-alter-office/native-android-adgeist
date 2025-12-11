package com.adgeistkit

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import com.adgeistkit.core.TargetingOptions
import com.adgeistkit.core.device.DeviceIdentifier
import com.adgeistkit.core.device.DeviceMeta
import com.adgeistkit.core.device.NetworkUtils
import com.adgeistkit.data.models.Event
import com.adgeistkit.data.models.UserDetails
import com.adgeistkit.data.network.CdpClient
import com.adgeistkit.data.network.CreativeAnalytics
import com.adgeistkit.data.network.FetchCreative
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AdgeistCore private constructor(
    private val context: Context,
    val bidRequestBackendDomain: String,
    private val customPackageOrBundleID: String? = null,
    private val customAdgeistAppID: String? = null,
) {
    companion object {
        private const val BidRequestBackendDomain = com.adgeistkit.BuildConfig.BASE_API_URL
        @Volatile private var instance: AdgeistCore? = null

        @JvmStatic
        fun initialize(context: Context,
                       customBidRequestBackendDomain: String? = null,
                       customPackageOrBundleID : String? = null,
                       customAdgeistAppID : String? = null, ): AdgeistCore
        {
                    return instance ?: synchronized(this) {
                        instance ?: AdgeistCore(
                            context.applicationContext,
                            customBidRequestBackendDomain ?: BidRequestBackendDomain,
                            customPackageOrBundleID,
                            customAdgeistAppID,
                        ).also { instance = it }
                    }
        }

        @JvmStatic
        fun getInstance(): AdgeistCore {
            return instance ?: throw IllegalStateException("AdgeistCore is not initialized")
        }
    }

    val packageOrBundleID = customPackageOrBundleID ?: getMetaValue("com.adgeistkit.ads.ADGEIST_CUSTOM_PACKAGE_OR_BUNDLE_ID") ?: ""
    val adgeistAppID = customAdgeistAppID ?: getMetaValue("com.adgeistkit.ads.ADGEIST_APP_ID") ?: ""
    val apiKey = getMetaValue("com.adgeistkit.ads.ADGEIST_API_KEY") ?: ""

    private val PREFS_NAME = "AdgeistPrefs"
    private val prefs: SharedPreferences

    private val KEY_CONSENT = "adgeist_consent"
    private var consentGiven: Boolean = false

    val deviceMeta = DeviceMeta(context)
    val deviceIdentifier = DeviceIdentifier(context)
    val networkUtils = NetworkUtils(context)
    var targetingInfo: Map<String, Any?>? = null

    private val cdpClient = CdpClient(deviceIdentifier, networkUtils, "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJraXNob3JlIiwiaWF0IjoxNzU0Mzc1NzIwLCJuYmYiOjE3NTQzNzU3MjAsImV4cCI6MTc1Nzk3NTcyMCwianRpIjoiOTdmNTI1YjAtM2NhNy00MzQwLTlhOGItZDgwZWI2ZjJmOTAzIiwicm9sZSI6ImFkbWluIiwic2NvcGUiOiJpbmdlc3QiLCJwbGF0Zm9ybSI6Im1vYmlsZSIsImNvbXBhbnlfaWQiOiJraXNob3JlIiwiaXNzIjoiQWRHZWlzdC1DRFAifQ.IYQus53aQETqOaQzEED8L51jwKRN3n-Oq-M8jY_ZSaw")
    private var userDetails: UserDetails? = null

    init {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        consentGiven = prefs.getBoolean(KEY_CONSENT, false)

        val targetingOptions = TargetingOptions(context)
        targetingInfo = targetingOptions.getTargetingInfo()
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
        prefs.edit().putBoolean(KEY_CONSENT, consentGiven).apply()
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
        CoroutineScope(Dispatchers.IO).launch {
            val localUserDetails = userDetails
            val parameters = mutableMapOf<String, Any>()
            event.eventProperties?.forEach { (key, value) -> if (value != null) parameters[key] = value }
            if (localUserDetails != null) parameters["userDetails"] = localUserDetails
            val fullEvent = event.copy(eventProperties = parameters)
            cdpClient.sendEventToCdp(fullEvent, consentGiven)
        }
    }

    fun requestPhoneStatePermission(activity: android.app.Activity) {
        DeviceMeta.requestPhoneStatePermission(activity)
    }

    fun hasPhoneStatePermission(): Boolean {
        return DeviceMeta.hasPhoneStatePermission(context)
    }
}