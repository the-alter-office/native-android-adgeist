package com.adgeistkit.data.network

import android.util.Log
import com.adgeistkit.AdgeistCore
import com.adgeistkit.ads.network.AnalyticsRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class CreativeAnalytics(private val adgeistCore: AdgeistCore) {
    companion object {
        private const val TAG = "CreativeAnalytics"

        const val IMPRESSION = "IMPRESSION"
        const val VIEW = "VIEW"
        const val TOTAL_VIEW = "TOTAL_VIEW"
        const val CLICK = "CLICK"
        const val VIDEO_PLAYBACK = "VIDEO_PLAYBACK"
        const val VIDEO_QUARTILE = "VIDEO_QUARTILE"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val client = OkHttpClient()

    private val bidRequestBackendDomain = adgeistCore.bidRequestBackendDomain

    private val packageOrBundleID = adgeistCore.packageOrBundleID
    private val adgeistAppID = adgeistCore.adgeistAppID
    private val apiKey = adgeistCore.apiKey

    private val deviceIdentifier = adgeistCore.deviceIdentifier
    private val networkUtils = adgeistCore.networkUtils

    fun sendTrackingDataV2(analyticsRequest: AnalyticsRequest){
        scope.launch {
            val url = "https://$bidRequestBackendDomain/v2/ssp/impression";

            val requestPayload = analyticsRequest.toJson().toString();
            val requestBody = requestPayload.toRequestBody("application/json".toMediaType());

            val request = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build()


            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.d(TAG, "Failed to send tracking data: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string() ?: "No error message"
                            Log.d(TAG, "Request failed with code: ${response.code}, message: $errorBody")
                            return
                        }
                    }
                }
            })
        }
    }

    fun sendTrackingData(
        campaignId: String,
        adSpaceId: String,
        eventType: String,
        bidId: String,
        bidMeta: String,
        buyType: String,
        isTestEnvironment: Boolean = true,
        additionalProperties: JSONObject = JSONObject(),
        callback: (String?) -> Unit
    ) {
        scope.launch {
            val deviceId = deviceIdentifier.getDeviceIdentifier()
            val userIP = networkUtils.getLocalIpAddress() ?: networkUtils.getWifiIpAddress() ?: "unknown"

            val envFlag = if (isTestEnvironment) "1" else "0"
            val url = if (buyType == "FIXED") {
                "https://$bidRequestBackendDomain/v2/ssp/impression"
            }else{
                "https://$bidRequestBackendDomain/api/analytics/track?adSpaceId=$adSpaceId&companyId=$adgeistAppID&test=$envFlag"
            }

            val requestPayload = if(buyType == "FIXED") {
                JSONObject().apply {
                    put("type", eventType)
                    put("metaData", bidMeta)
                    additionalProperties.keys().forEach { key ->
                        put(key, additionalProperties.get(key))
                    }
                }.toString()
            }else{
                JSONObject().apply {
                    put("eventType", eventType)
                    put("winningBidId", bidId)
                    put("campaignId", campaignId)
                    additionalProperties.keys().forEach { key ->
                        put(key, additionalProperties.get(key))
                    }
                }.toString()
            }

            val requestBody = requestPayload.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("Origin", packageOrBundleID)
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

    fun trackImpression(campaignId: String, adSpaceId: String, bidId: String, bidMeta: String, buyType: String, isTestEnvironment: Boolean, renderTime: Float) {
        val properties = JSONObject().apply {
            put("renderTime", renderTime)
        }
        sendTrackingData(campaignId, adSpaceId, IMPRESSION, bidId, bidMeta, buyType, isTestEnvironment, properties) { result ->
            Log.d(TAG, "Impression event result: $result")
        }
    }

    fun trackView(campaignId: String, adSpaceId: String, bidId: String, bidMeta: String, buyType: String, isTestEnvironment: Boolean, viewTime: Float, visibilityRatio: Float, scrollDepth: Float, timeToVisible: Float) {
        val properties = JSONObject().apply {
            put("viewTime", viewTime)
            put("visibilityRatio", visibilityRatio)
            put("scrollDepth", scrollDepth)
            put("timeToVisible", timeToVisible)
        }
        sendTrackingData(campaignId, adSpaceId, VIEW, bidId, bidMeta, buyType, isTestEnvironment, properties) { result ->
            Log.d(TAG, "View event result: $result")
        }
    }

    fun trackTotalView(campaignId: String, adSpaceId: String, bidId: String, bidMeta: String, buyType: String, isTestEnvironment: Boolean, totalViewTime: Float, visibilityRatio: Float) {
        val properties = JSONObject().apply {
            put("totalViewTime", totalViewTime)
            put("visibilityRatio", visibilityRatio)
        }
        sendTrackingData(campaignId, adSpaceId, TOTAL_VIEW, bidId, bidMeta, buyType, isTestEnvironment, properties) { result ->
            Log.d(TAG, "Total view event result: $result")
        }
    }

    fun trackClick(campaignId: String, adSpaceId: String, bidId: String, bidMeta: String, buyType: String, isTestEnvironment: Boolean) {
        val properties = JSONObject().apply {
        }
        sendTrackingData(campaignId, adSpaceId, CLICK, bidId, bidMeta, buyType, isTestEnvironment, properties) { result ->
            Log.d(TAG, "Click event result: $result")
        }
    }

    fun trackVideoPlayback(campaignId: String, adSpaceId: String, bidId: String, bidMeta: String, buyType: String, isTestEnvironment: Boolean, totalPlaybackTime: Float) {
        val properties = JSONObject().apply {
            put("totalPlaybackTime", totalPlaybackTime)
        }
        sendTrackingData(campaignId, adSpaceId, VIDEO_PLAYBACK, bidId, bidMeta, buyType, isTestEnvironment, properties) { result ->
            Log.d(TAG, "Video playback event result: $result")
        }
    }

    fun trackVideoQuartile(campaignId: String, adSpaceId: String, bidId: String, bidMeta: String, buyType: String, isTestEnvironment: Boolean, quartile: String) {
        val properties = JSONObject().apply {
            put("quartile", quartile)
        }
        sendTrackingData(campaignId, adSpaceId, VIDEO_QUARTILE, bidId, bidMeta, buyType, isTestEnvironment, properties) { result ->
            Log.d(TAG, "Video quartile event result: $result")
        }
    }
}