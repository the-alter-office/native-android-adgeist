package com.adgeistkit

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class CreativeAnalytics(
    private val context: Context,
    private val deviceIdentifier: DeviceIdentifier,
    private val networkUtils: NetworkUtils,
    private val domain: String
) {
    companion object {
        private const val TAG = "CreativeAnalytics"
        // Align with web SDK EVENT_TYPES
        const val IMPRESSION = "IMPRESSION"
        const val VIEW = "VIEW"
        const val TOTAL_VIEW = "TOTAL_VIEW"
        const val HOVER = "HOVER"
        const val CLICK = "CLICK"
        const val VIDEO_PLAYBACK = "VIDEO_PLAYBACK"
        const val VIDEO_QUARTILE = "VIDEO_QUARTILE"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val client = OkHttpClient()

    // Core method to send tracking data to backend
    fun sendTrackingData(
        campaignId: String,
        adSpaceId: String,
        publisherId: String,
        eventType: String,
        origin: String,
        apiKey: String,
        bidId: String,
        isTestEnvironment: Boolean = true,
        additionalProperties: JSONObject = JSONObject(),
        callback: (String?) -> Unit
    ) {
        scope.launch {
            val deviceId = deviceIdentifier.getDeviceIdentifier()
            val userIP = networkUtils.getLocalIpAddress() ?: networkUtils.getWifiIpAddress() ?: "unknown"

            val envFlag = if (isTestEnvironment) "1" else "0"
            val url = "https://$domain/api/analytics/track?adSpaceId=$adSpaceId&companyId=$publisherId&test=$envFlag"

            val requestBodyJson = JSONObject().apply {
                put("eventType", eventType)
                put("winningBidId", bidId)
                put("campaignId", campaignId)
                // Merge additional properties (e.g., visibility_ratio, scroll_depth)
                additionalProperties.keys().forEach { key ->
                    put(key, additionalProperties.get(key))
                }
            }.toString()

            val requestBody = requestBodyJson.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("Origin", origin)
                .header("x-user-id", deviceId)
                .header("x-platform", "mobile_app")
                .header("x-api-key", apiKey)
                .header("x-forwarded-for", userIP)
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.d(TAG, "Failed to send tracking data: ${e.message}")
                    callback(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string() ?: "No error message"
                            Log.d(TAG, "Request failed with code: ${response.code}, message: $errorBody")
                            callback(null)
                            return
                        }

                        val jsonString = response.body?.string()
                        Log.d(TAG, "Tracking data sent successfully: $jsonString")
                        callback(jsonString)
                    }
                }
            })
        }
    }

    // Track impression
    fun trackImpression(campaignId: String, adSpaceId: String, publisherId: String, apiKey: String, bidId: String, isTestEnvironment: Boolean, renderTime: Float) {
        val properties = JSONObject().apply {
            put("renderTime", renderTime)
        }
        sendTrackingData(campaignId, adSpaceId, publisherId, IMPRESSION, domain, apiKey, bidId, isTestEnvironment, properties) { result ->
            Log.d(TAG, "Impression event result: $result")
        }
    }

    // Track view event (scroll depth, visibility ratio, view time)
    fun trackView(campaignId: String, adSpaceId: String, publisherId: String, apiKey: String, bidId: String, isTestEnvironment: Boolean, viewTime: Float, visibilityRatio: Float, scrollDepth: Float, timeToVisible: Float) {
        val properties = JSONObject().apply {
            put("viewTime", viewTime)
            put("visibilityRatio", visibilityRatio)
            put("scrollDepth", scrollDepth)
            put("timeToVisible", timeToVisible)
        }
        sendTrackingData(campaignId, adSpaceId, publisherId, VIEW, domain, apiKey, bidId, isTestEnvironment, properties) { result ->
            Log.d(TAG, "View event result: $result")
        }
    }

    // Track total view time
    fun trackTotalView(campaignId: String, adSpaceId: String, publisherId: String, apiKey: String, bidId: String, isTestEnvironment: Boolean, totalViewTime: Float, visibilityRatio: Float) {
        val properties = JSONObject().apply {
            put("totalViewTime", totalViewTime)
            put("visibilityRatio", visibilityRatio)
        }
        sendTrackingData(campaignId, adSpaceId, publisherId, TOTAL_VIEW, domain, apiKey, bidId, isTestEnvironment, properties) { result ->
            Log.d(TAG, "Total view event result: $result")
        }
    }

    // Track click
    fun trackClick(campaignId: String, adSpaceId: String, publisherId: String, apiKey: String, bidId: String, isTestEnvironment: Boolean) {
        val properties = JSONObject().apply {
        }
        sendTrackingData(campaignId, adSpaceId, publisherId, CLICK, domain, apiKey, bidId, isTestEnvironment, properties) { result ->
            Log.d(TAG, "Click event result: $result")
        }
    }

    // Track video playback
    fun trackVideoPlayback(campaignId: String, adSpaceId: String, publisherId: String, apiKey: String, bidId: String, isTestEnvironment: Boolean, totalPlaybackTime: Float) {
        val properties = JSONObject().apply {
            put("totalPlaybackTime", totalPlaybackTime)
        }
        sendTrackingData(campaignId, adSpaceId, publisherId, VIDEO_PLAYBACK, domain, apiKey, bidId, isTestEnvironment, properties) { result ->
            Log.d(TAG, "Video playback event result: $result")
        }
    }

    // Track video quartile
    fun trackVideoQuartile(campaignId: String, adSpaceId: String, publisherId: String, apiKey: String, bidId: String, isTestEnvironment: Boolean, quartile: String) {
        val properties = JSONObject().apply {
            put("quartile", quartile)
        }
        sendTrackingData(campaignId, adSpaceId, publisherId, VIDEO_QUARTILE, domain, apiKey, bidId, isTestEnvironment, properties) { result ->
            Log.d(TAG, "Video quartile event result: $result")
        }
    }
}