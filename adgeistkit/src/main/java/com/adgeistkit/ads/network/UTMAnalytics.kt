package com.adgeistkit.ads.network

import android.util.Log
import com.adgeistkit.core.UtmParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/**
 * Handles sending UTM tracking data to the backend analytics API
 */
class UTMAnalytics(
    private val bidRequestBackendDomain: String
) {
    
    companion object {
        private const val TAG = "UTMAnalytics"
        private const val ANALYTICS_ENDPOINT = "/v2/analytics/utm"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val client = OkHttpClient()

    /**
     * Send UTM parameters to backend API
     */
    fun sendUtmData(params: UtmParameters, onComplete: ((Boolean, String?) -> Unit)? = null) {
        scope.launch {
            try {
                val url = "$bidRequestBackendDomain$ANALYTICS_ENDPOINT"
                
                // Create JSON payload with UTM parameters
                val payload = buildPayload(params)
                val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url(url)
                    .header("Content-Type", "application/json")
                    .post(requestBody)
                    .build()
                
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        val errorMessage = "Failed to send UTM data to backend: ${e.message}"
                        Log.d(TAG, errorMessage)
                        onComplete?.invoke(false, errorMessage)
                    }
                    
                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (!response.isSuccessful) {
                                val errorBody = response.body?.string() ?: "No error message"
                                val errorMessage = "UTM API request failed with code: ${response.code}, message: $errorBody"
                                Log.d(TAG, errorMessage)
                                onComplete?.invoke(false, errorMessage)
                                return
                            }
                            Log.d(TAG, "UTM data sent successfully to backend")
                            onComplete?.invoke(true, null)
                        }
                    }
                })
            } catch (e: Exception) {
                val errorMessage = "Error sending UTM data to backend: ${e.message}"
                Log.e(TAG, errorMessage)
                onComplete?.invoke(false, errorMessage)
            }
        }
    }

    /**
     * Build JSON payload for UTM tracking
     */
    private fun buildPayload(params: UtmParameters): JSONObject {
        return JSONObject().apply {
            params.source?.let { put("utm_source", it) }
            params.medium?.let { put("utm_medium", it) }
            params.campaign?.let { put("utm_campaign", it) }
            params.term?.let { put("utm_term", it) }
            params.content?.let { put("utm_content", it) }
            params.timestamp?.let { put("utm_timestamp", it) }
            params.x_data?.let { put("utm_x_data", it) }
            put("platform", "android")
            put("event_type", "utm_tracked")
        }
    }
}
