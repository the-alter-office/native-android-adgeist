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
        private const val KEY_UTM_MEDIUM = "utm_medium"
        private const val KEY_UTM_CAMPAIGN = "utm_campaign"
        private const val KEY_UTM_TERM = "utm_term"
        private const val KEY_UTM_CONTENT = "utm_content"
        private const val KEY_UTM_TIMESTAMP = "utm_timestamp"
        private const val KEY_UTM_X_DATA = "utm_x_data"
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
        uri.getQueryParameter("utm_medium")?.let { params["utm_medium"] = it }
        uri.getQueryParameter("utm_campaign")?.let { params["utm_campaign"] = it }
        uri.getQueryParameter("utm_term")?.let { params["utm_term"] = it }
        uri.getQueryParameter("utm_content")?.let { params["utm_content"] = it }
        uri.getQueryParameter("utm_x_data")?.let { params["utm_x_data"] = it }

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
            uri.getQueryParameter("utm_medium")?.let { params["utm_medium"] = it }
            uri.getQueryParameter("utm_campaign")?.let { params["utm_campaign"] = it }
            uri.getQueryParameter("utm_term")?.let { params["utm_term"] = it }
            uri.getQueryParameter("utm_content")?.let { params["utm_content"] = it }
            uri.getQueryParameter("utm_x_data")?.let { params["utm_x_data"] = it }

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
     * Save UTM parameters to SharedPreferences
     */
    private fun saveUtmParameters(params: UtmParameters) {
        prefs.edit().apply {
            params.source?.let { putString(KEY_UTM_SOURCE, it) }
            params.medium?.let { putString(KEY_UTM_MEDIUM, it) }
            params.campaign?.let { putString(KEY_UTM_CAMPAIGN, it) }
            params.term?.let { putString(KEY_UTM_TERM, it) }
            params.content?.let { putString(KEY_UTM_CONTENT, it) }
            params.timestamp?.let { putLong(KEY_UTM_TIMESTAMP, it) }
            params.x_data?.let { putString(KEY_UTM_X_DATA, it) }
            apply()
        }
        
        // Send UTM data to backend
        sendUtmDataToBackend(params)
    }

    /**
     * Send UTM parameters to backend API
     */
    private fun sendUtmDataToBackend(params: UtmParameters) {
        utmAnalytics.sendUtmData(params)
    }

    /**
     * Get stored UTM parameters
     */
    fun getUtmParameters(): UtmParameters? {
        val source = prefs.getString(KEY_UTM_SOURCE, null)
        val medium = prefs.getString(KEY_UTM_MEDIUM, null)
        val campaign = prefs.getString(KEY_UTM_CAMPAIGN, null)
        val term = prefs.getString(KEY_UTM_TERM, null)
        val content = prefs.getString(KEY_UTM_CONTENT, null)
        val timestamp = prefs.getLong(KEY_UTM_TIMESTAMP, 0L).takeIf { it > 0 }
        val x_data = prefs.getString(KEY_UTM_X_DATA, null)

        val params = UtmParameters(source, medium, campaign, term, content, timestamp,x_data)
        return if (params.hasData()) params else null
    }

    /**
     * Clear all stored UTM parameters
     */
    fun clearUtmParameters() {
        prefs.edit().apply {
            remove(KEY_UTM_SOURCE)
            remove(KEY_UTM_MEDIUM)
            remove(KEY_UTM_CAMPAIGN)
            remove(KEY_UTM_TERM)
            remove(KEY_UTM_CONTENT)
            remove(KEY_UTM_TIMESTAMP)
            remove(KEY_UTM_X_DATA)
            apply()
        }
        Log.d(TAG, "UTM parameters cleared")
    }
}
