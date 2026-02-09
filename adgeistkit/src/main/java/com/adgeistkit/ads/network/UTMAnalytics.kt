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
        private const val ANALYTICS_ENDPOINT = "/v2/ssp/campaign-event"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val client = OkHttpClient()

    /**
     * Send UTM parameters to backend API
     */
    fun sendUtmData(
        params: UtmParameters, 
        sessionId: String,
        eventType: String = "VISIT",
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) {
        scope.launch {
            try {
                val url = "$bidRequestBackendDomain$ANALYTICS_ENDPOINT"
                
                // Create JSON payload with UTM parameters 
                val payload = buildPayload(params, sessionId, eventType)
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
    private fun buildPayload(params: UtmParameters, sessionId: String, eventType: String): JSONObject {
        return JSONObject().apply {
            put("metaData", params.data ?: "")
            put("flowId", sessionId)
            put("type", eventType)
            put("origin", params.source ?: "")
            put("platform", "ANDROID")
        }
    }
}
