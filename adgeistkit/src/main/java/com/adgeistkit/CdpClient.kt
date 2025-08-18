package com.adgeistkit

import android.util.Log
import com.adgeistkit.AdgeistCore.Companion
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.Date

/**
 * Handles communication with the CDP platform for user details and events.
 */
class CdpClient(
    private val deviceIdentifier: DeviceIdentifier,
    private val networkUtils: NetworkUtils,
    private val bearerToken: String
) {
    companion object {
        private const val TAG = "CdpClient"
    }

    private val cdpDomain = "rl2ptnqw5f.execute-api.ap-south-1.amazonaws.com"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    /**
     * Sends an event to the CDP platform.
     * @param event The event to send, with userDetails optionally included in event_properties.
     */
    fun sendEventToCdp(event: Event) {
        CoroutineScope(Dispatchers.IO).launch {
            val deviceId = deviceIdentifier.getDeviceIdentifier()
            val userIP = networkUtils.getLocalIpAddress() ?: networkUtils.getWifiIpAddress() ?: "unknown"

            // Structure the traits map
            val traits = mutableMapOf<String, Any?>(
                "consent_given" to true,
                "source" to "mobile",
                "timestamp" to Date().toISOString(),
                "google_ad_id" to deviceId
            )

            // Create a clean event_properties map, excluding userDetails
            val cleanedEventProperties = event.eventProperties?.toMutableMap() ?: mutableMapOf()
            val userDetails = cleanedEventProperties.remove("userDetails") as? UserDetails

            // Merge userDetails into traits, if present
            userDetails?.toMap()?.let { traits.putAll(it) }

            // Structure the request body
            val requestBody = mapOf(
                "event_type" to event.eventType,
                "traits" to traits,
                "event_properties" to cleanedEventProperties
            )

            val body = gson.toJson(requestBody)
            val request = Request.Builder()
                .url("https://$cdpDomain/ingest")
                .post(body.toRequestBody("application/json".toMediaType()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $bearerToken")
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
//                        Log.d(TAG, "Event sent to CDP: ${response.body?.string()}")
                    } else {
                        Log.e(TAG, "Failed to send event to CDP: ${response.code}, ${response.body?.string()}")
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error sending event to CDP: ${e.message}")
            }
        }
    }
}

/**
 * Extension function to convert Date to ISO 8601 string.
 */
fun Date.toISOString(): String {
    return java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }.format(this)
}