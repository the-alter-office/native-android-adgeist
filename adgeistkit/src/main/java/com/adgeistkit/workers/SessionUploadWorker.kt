package com.adgeistkit.workers

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.adgeistkit.logging.SdkShield
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * WorkManager worker to reliably upload session duration events.
 * Handles automatic retry with exponential backoff and network constraints.
 */
class SessionUploadWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val TAG = "SessionUploadWorker"
        
        // Input data keys
        const val KEY_SESSION_ID = "session_id"
        const val KEY_DURATION_MS = "duration_ms"
        const val KEY_UTM_SOURCE = "utm_source"
        const val KEY_UTM_DATA = "utm_data"
        const val KEY_BACKEND_DOMAIN = "backend_domain"
        
        private const val ANALYTICS_ENDPOINT = "/v2/ssp/campaign-event"
        private const val EVENT_TYPE_SESSION_DURATION = "SESSION_DURATION"
        
        // SharedPreferences for clearing session state
        private const val PREFS_NAME = "AdgeistUtmPrefs"
        private const val PREF_SESSION_TRACKING = "session_tracking_active"
    }

    private val client = OkHttpClient()

    override fun doWork(): Result {
        return SdkShield.runSafelyWithReturn("SessionUploadWorker.doWork", Result.retry()) {
            doWorkInternal()
        }
    }

    private fun doWorkInternal(): Result {
        // Extract input data
        val sessionId = inputData.getString(KEY_SESSION_ID)
        val durationMs = inputData.getLong(KEY_DURATION_MS, 0)
        val utmSource = inputData.getString(KEY_UTM_SOURCE) ?: ""
        val utmData = inputData.getString(KEY_UTM_DATA) ?: ""
        val backendDomain = inputData.getString(KEY_BACKEND_DOMAIN)

        // Validate required data
        if (sessionId == null || durationMs <= 0 || backendDomain == null) {
            Log.e(TAG, "Invalid input data: sessionId=$sessionId, duration=$durationMs, domain=$backendDomain")
            clearSessionState()
            return Result.failure()
        }

        Log.i(TAG, "Uploading session: $sessionId, duration: ${durationMs}ms")

        return try {
            val url = "$backendDomain$ANALYTICS_ENDPOINT"
            
            // Build JSON payload
            val payload = JSONObject().apply {
                put("metaData", utmData)
                put("flowId", sessionId)
                put("type", EVENT_TYPE_SESSION_DURATION)
                put("origin", utmSource)
                put("platform", "ANDROID")
                put("additionalData", JSONObject().apply {
                    put("sessionDuration", durationMs)
                })
            }

            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build()

            // Execute synchronous request (Worker already runs on background thread)
            val response = client.newCall(request).execute()
            
            response.use {
                when {
                    response.isSuccessful -> {
                        Log.i(TAG, "Session duration uploaded successfully: $sessionId")
                        clearSessionState()
                        Result.success()
                    }
                    response.code in 400..499 -> {
                        // Client error - don't retry invalid data
                        val errorBody = response.body?.string() ?: "No error message"
                        Log.e(TAG, "Client error (${response.code}): $errorBody")
                        clearSessionState()
                        Result.failure()
                    }
                    else -> {
                        // Server error or network issue - retry
                        val errorBody = response.body?.string() ?: "No error message"
                        Log.w(TAG, "Server error (${response.code}), will retry: $errorBody")
                        Result.retry()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error uploading session, will retry: ${e.message}", e)
            Result.retry()
        }
    }

    /**
     * Clear session tracking state from SharedPreferences
     */
    private fun clearSessionState() {
        try {
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                remove(PREF_SESSION_TRACKING)
                remove("accumulated_duration")
                remove("last_persist_time")
                apply()
            }
            Log.d(TAG, "Session state cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing session state: ${e.message}", e)
        }
    }
}
