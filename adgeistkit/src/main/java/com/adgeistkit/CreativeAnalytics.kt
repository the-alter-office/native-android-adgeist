package com.adgeistkit

import android.bluetooth.BluetoothClass.Device
import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaTypeOrNull

class CreativeAnalytics(
    private val context: Context,
    private val deviceIdentifier: DeviceIdentifier,
    private val networkUtils: NetworkUtils
) {
    companion object {
        private const val TAG = "CreativeAnalytics"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val client = OkHttpClient()

    fun sendTrackingData(
        campaignId: String,
        adSpaceId: String,
        publisherId: String,
        eventType: String,
        origin: String,
        apiKey: String,
        bidId: String,
        isTestEnvironment: Boolean = true,
        callback: (String?) -> Unit
    ) {
        scope.launch {
            val deviceId = deviceIdentifier.getDeviceIdentifier()
            val userIP = networkUtils.getLocalIpAddress() ?: networkUtils.getWifiIpAddress() ?: "unknown"

            val envFlag = if (isTestEnvironment) "1" else "0"
            val url = "https://bg-services-api.adgeist.ai/api/analytics/track?adSpaceId=$adSpaceId&companyId=$publisherId&test=$envFlag"

            val requestBodyJson = JSONObject().apply {
                put("eventType", eventType)
                put("winningBidId", bidId)
                put("campaignId", campaignId)
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
                    Log.d("CreativeAnalytics", "Failed to send tracking data: ${e.message}")
                    callback(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string() ?: "No error message"
                            Log.d("CreativeAnalytics", "Request failed with code: ${response.code}, message: $errorBody")
                            callback(null)
                            return
                        }

                        val jsonString = response.body?.string()
                        Log.d("CreativeAnalytics", "Tracking data sent successfully: $jsonString")
                        callback(jsonString)
                    }
                }
            })
        }
    }
}
