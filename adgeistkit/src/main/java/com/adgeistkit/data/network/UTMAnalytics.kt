package com.adgeistkit.data.network

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
        const val EVENT_TYPE_SESSION_DURATION = "SESSION_DURATION"
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
     * Send session duration event to backend API
     */
    fun sendSessionDurationEvent(
        sessionId: String,
        durationMs: Long,
        utmSource: String,
        utmData: String,
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) {
        scope.launch {
            try {
                val url = "$bidRequestBackendDomain$ANALYTICS_ENDPOINT"
                
                // Create JSON payload with session duration
                val additionalData = mapOf("sessionDuration" to durationMs)
                val payload = buildPayload(
                    sessionId = sessionId,
                    eventType = EVENT_TYPE_SESSION_DURATION,
                    utmSource = utmSource,
                    utmData = utmData,
                    additionalData = additionalData
                )
                
                val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url(url)
                    .header("Content-Type", "application/json")
                    .post(requestBody)
                    .build()
                
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        val errorMessage = "Failed to send session duration: ${e.message}"
                        Log.w(TAG, errorMessage)
                        onComplete?.invoke(false, errorMessage)
                    }
                    
                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (!response.isSuccessful) {
                                val errorBody = response.body?.string() ?: "No error message"
                                val errorMessage = "Session duration API failed with code: ${response.code}, message: $errorBody"
                                Log.w(TAG, errorMessage)
                                onComplete?.invoke(false, errorMessage)
                                return
                            }
                            Log.i(TAG, "Session duration sent successfully: ${durationMs}ms")
                            onComplete?.invoke(true, null)
                        }
                    }
                })
            } catch (e: Exception) {
                val errorMessage = "Error sending session duration: ${e.message}"
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

    /**
     * Build JSON payload with additional data
     */
    private fun buildPayload(
        sessionId: String,
        eventType: String,
        utmSource: String,
        utmData: String,
        additionalData: Map<String, Any>? = null
    ): JSONObject {
        return JSONObject().apply {
            put("metaData", utmData)
            put("flowId", sessionId)
            put("type", eventType)
            put("origin", utmSource)
            put("platform", "ANDROID")
            additionalData?.let {
                put("additionalData", JSONObject(it))
            }
        }
    }
}
