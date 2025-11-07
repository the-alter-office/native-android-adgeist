package com.adgeistkit

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
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
    private val domain: String
) {
    companion object {
        private const val TAG = "AdgeistCore"
        @Volatile private var instance: AdgeistCore? = null
        private const val DEFAULT_DOMAIN = com.adgeistkit.BuildConfig.BASE_API_URL

        /**
         * Initializes the SDK with the given context and optional custom domain.
         * @param context The application context.
         * @param customDomain Optional custom domain for API requests.
         * @return The initialized AdgeistCore instance.
         */
        fun initialize(context: Context, customDomain: String? = null): AdgeistCore {
            return instance ?: synchronized(this) {
                instance ?: AdgeistCore(
                    context.applicationContext,
                    customDomain ?: DEFAULT_DOMAIN
                ).also { instance = it }
            }
        }

        /**
         * Gets the initialized instance of AdgeistCore.
         * @throws IllegalStateException if not initialized.
         */
        fun getInstance(): AdgeistCore {
            return instance ?: throw IllegalStateException("AdgeistCore is not initialized")
        }
    }

    private val PREFS_NAME = "AdgeistPrefs"
    private val KEY_CONSENT = "adgeist_consent"
    private val prefs: SharedPreferences
    private val deviceIdentifier = DeviceIdentifier(context)
    private val networkUtils = NetworkUtils(context)
    private var userDetails: UserDetails? = null
    private val cdpClient = CdpClient(deviceIdentifier, networkUtils, "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJraXNob3JlIiwiaWF0IjoxNzU0Mzc1NzIwLCJuYmYiOjE3NTQzNzU3MjAsImV4cCI6MTc1Nzk3NTcyMCwianRpIjoiOTdmNTI1YjAtM2NhNy00MzQwLTlhOGItZDgwZWI2ZjJmOTAzIiwicm9sZSI6ImFkbWluIiwic2NvcGUiOiJpbmdlc3QiLCJwbGF0Zm9ybSI6Im1vYmlsZSIsImNvbXBhbnlfaWQiOiJraXNob3JlIiwiaXNzIjoiQWRHZWlzdC1DRFAifQ.IYQus53aQETqOaQzEED8L51jwKRN3n-Oq-M8jY_ZSaw")
    private var consentGiven: Boolean = false
    private var deviceMeta = DeviceMeta(context)
    private var targetingInfo: Map<String, Any?>? = null

    init {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        consentGiven = prefs.getBoolean(KEY_CONSENT, false)

        val targetingOptions = TargetingOptions(context)
        targetingInfo = targetingOptions.getTargetingInfo()
    }

    /**
     * Sets optional user details to be included in ad requests and sent to CDP.
     * @param details The user details to set.
     */
    @Synchronized
    fun setUserDetails(details: UserDetails) {
        userDetails = details
    }

    /**
     * Sets user consent sent to CDP.
     */
    fun updateConsentStatus(consentGiven: Boolean) {
        this.consentGiven = consentGiven
        prefs.edit().putBoolean(KEY_CONSENT, consentGiven).apply()
    }

    /**
     * gets user consent.
     */
    fun getConsentStatus(): Boolean {
        return consentGiven
    }

    /**
     * Gets FetchCreative instance with user details if set.
     */
    fun getCreative(): FetchCreative {
        return FetchCreative(context, deviceIdentifier, networkUtils, domain, targetingInfo)
    }

    /**
     * Gets CreativeAnalytics instance with user details if set.
     */
    fun postCreativeAnalytics(): CreativeAnalytics {
        return CreativeAnalytics(context, deviceIdentifier, networkUtils, domain)
    }

    /**
     * Logs an event to be sent to the CDP platform.
     * @param event The event to log.
     */
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

    /**
     * Requests READ_PHONE_STATE permission if not granted.
     * Must be called from an Activity in the host app before using device meta features.
     */
    fun requestPhoneStatePermission(activity: android.app.Activity) {
        DeviceMeta.requestPhoneStatePermission(activity)
    }

    /**
     * Checks if READ_PHONE_STATE permission is granted.
     */
    fun hasPhoneStatePermission(): Boolean {
        return DeviceMeta.hasPhoneStatePermission(context)
    }

}