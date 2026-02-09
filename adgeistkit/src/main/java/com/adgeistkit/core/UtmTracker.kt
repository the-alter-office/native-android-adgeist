package com.adgeistkit.core

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.adgeistkit.ads.network.UTMAnalytics
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener

/**
 * Handles tracking and persistence of UTM parameters from install referrer and deeplinks.
 */
class UtmTracker(
    private val context: Context,
    private val bidRequestBackendDomain: String
) {
    
    companion object {
        private const val TAG = "UtmTracker"
        private const val PREFS_NAME = "AdgeistUtmPrefs"
        private const val KEY_UTM_SOURCE = "utm_source"
        private const val KEY_UTM_CAMPAIGN = "utm_campaign"
        private const val KEY_UTM_DATA = "utm_data"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_FIRST_LAUNCH = "first_launch"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val utmAnalytics = UTMAnalytics(bidRequestBackendDomain)

    /**
     * Track UTM parameters from a deeplink URI
     */
    fun trackFromDeeplink(uri: Uri) {
        val params = mutableMapOf<String, String?>()
        
        uri.getQueryParameter("utm_source")?.let { params["utm_source"] = it }
        uri.getQueryParameter("utm_campaign")?.let { params["utm_campaign"] = it }
        uri.getQueryParameter("utm_data")?.let { params["utm_data"] = it }

        if (params.isNotEmpty()) {
            val utmParams = UtmParameters.fromMap(params)
            saveUtmParameters(utmParams)
            Log.d(TAG, "UTM parameters tracked from deeplink: $utmParams")
        }
    }

    /**
     * Track UTM parameters from install referrer (Google Play Install Referrer API)
     * This should be called when the app is first installed
     */
    fun trackFromInstallReferrer(referrerUrl: String) {
        try {
            val uri = Uri.parse("https://example.com?$referrerUrl")
            val params = mutableMapOf<String, String?>()
            
            uri.getQueryParameter("utm_source")?.let { params["utm_source"] = it }
            uri.getQueryParameter("utm_campaign")?.let { params["utm_campaign"] = it }
            uri.getQueryParameter("utm_data")?.let { params["utm_data"] = it }

            if (params.isNotEmpty()) {
                val utmParams = UtmParameters.fromMap(params)
                saveUtmParameters(utmParams)
                Log.d(TAG, "UTM parameters tracked from install referrer: $utmParams")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing install referrer: ${e.message}")
        }
    }

    /**
     * Initialize and fetch install referrer for first launch
     * Uses Google Play Install Referrer API
     */
    fun initializeInstallReferrer() {
        val isFirstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        
        if (isFirstLaunch) {
            // Mark first launch as complete
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
            
            // Fetch install referrer using Google Play Install Referrer API
            val referrerClient = InstallReferrerClient.newBuilder(context).build()
            
            referrerClient.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    when (responseCode) {
                        InstallReferrerClient.InstallReferrerResponse.OK -> {
                            try {
                                val response = referrerClient.installReferrer
                                val referrerUrl = response.installReferrer
                                Log.d(TAG, "Install referrer received: $referrerUrl")
                                trackFromInstallReferrer(referrerUrl)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error reading install referrer: ${e.message}")
                            }
                        }
                        InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED -> {
                            Log.w(TAG, "Install referrer API not supported")
                        }
                        InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE -> {
                            Log.w(TAG, "Install referrer service unavailable")
                        }
                        else -> {
                            Log.w(TAG, "Install referrer response code: $responseCode")
                        }
                    }
                    referrerClient.endConnection()
                }

                override fun onInstallReferrerServiceDisconnected() {
                    Log.d(TAG, "Install referrer service disconnected")
                }
            })
        }
    }

    /**
     * Generates a unique session ID
     */
    private fun generateSessionId(): String {
        return "${System.currentTimeMillis()}-${java.util.UUID.randomUUID().toString().take(9)}"
    }

    /**
     * Save UTM parameters to SharedPreferences
     */
    private fun saveUtmParameters(params: UtmParameters) {
        val sessionId = generateSessionId()
        prefs.edit().apply {
            params.source?.let { putString(KEY_UTM_SOURCE, it) }
            params.campaign?.let { putString(KEY_UTM_CAMPAIGN, it) }
            params.data?.let { putString(KEY_UTM_DATA, it) }
            putString(KEY_SESSION_ID, sessionId)
            apply()
        }
        
        // Send UTM data to backend
        sendUtmDataToBackend(params, sessionId)
    }

    /**
     * Send UTM parameters to backend API
     */
    private fun sendUtmDataToBackend(params: UtmParameters, sessionId: String) {
        utmAnalytics.sendUtmData(params, sessionId)
    }

    /**
     * Get stored UTM parameters
     */
    fun getUtmParameters(): UtmParameters? {
        val source = prefs.getString(KEY_UTM_SOURCE, null)
        val campaign = prefs.getString(KEY_UTM_CAMPAIGN, null)
        val data = prefs.getString(KEY_UTM_DATA, null)
        val sessionId = prefs.getString(KEY_SESSION_ID, null)

        val params = UtmParameters(source, campaign, data, sessionId)
        return if (params.hasData()) params else null
    }

    /**
     * Clear all stored UTM parameters
     */
    fun clearUtmParameters() {
        prefs.edit().apply {
            remove(KEY_UTM_SOURCE)
            remove(KEY_UTM_CAMPAIGN)
            remove(KEY_UTM_DATA)
            remove(KEY_SESSION_ID)
            apply()
        }
        Log.d(TAG, "UTM parameters cleared")
    }
}
