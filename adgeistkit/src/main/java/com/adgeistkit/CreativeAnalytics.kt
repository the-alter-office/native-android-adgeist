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

class CreativeAnalytics(
    private val context: Context,
    private val deviceIdentifier: DeviceIdentifier
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
        callback: (String?) -> Unit
    ) {
        scope.launch {
            val deviceId = deviceIdentifier.getDeviceIdentifier()
            Log.i(TAG, "Successfully fetched Device ID: $deviceId")

            val url = "https://beta-api.adgeist.ai/campaign/campaign-analytics" +
                    "?campaignId=$campaignId&adSpaceId=$adSpaceId&companyId=$publisherId"

            val requestBodyJson = JSONObject().apply {
                when (eventType.lowercase()) {
                    "click" -> put("clicks", 1)
                    "impression" -> put("impressions", 1)
                }
            }.toString()

            val requestBody = requestBodyJson.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .header("Origin", "https://beta.adgeist.ai")
                .header("Content-Type", "application/json")
                .put(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.d("CreativeAnalytics", "Failed to send tracking data: ${e.message}")
                    callback(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            Log.d("CreativeAnalytics", "Request failed with code: ${response.code}")
                            callback(null)
                            return
                        }

                        val jsonString = response.body?.string()
                        callback(jsonString)
                    }
                }
            })
        }
    }
}
