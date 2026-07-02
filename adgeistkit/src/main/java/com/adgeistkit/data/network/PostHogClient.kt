package com.adgeistkit.data.network

import android.util.Log
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

/**
 * Sends SDK telemetry events to PostHog in place of our own backend.
 */
object PostHogClient {
    private const val TAG = "PostHogClient"
    private const val API_KEY = "phc_kqTQTisRrHXss5HknH3g7XbPdyrEAw49oa3ssRUaxaSK"
    private const val SINGLE_EVENT_ENDPOINT = "https://us.i.posthog.com/i/v0/e/"
    private const val BATCH_ENDPOINT = "https://us.i.posthog.com/batch/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    /**
     * Fire-and-forget single event capture.
     */
    fun capture(event: String, distinctId: String, properties: Map<String, Any?>) {
        CoroutineScope(Dispatchers.IO).launch {
            val body = gson.toJson(
                mapOf(
                    "api_key" to API_KEY,
                    "event" to event,
                    "distinct_id" to distinctId,
                    "properties" to properties
                )
            )
            postJson(SINGLE_EVENT_ENDPOINT, body)
        }
    }

    /**
     * Blocking batch send. Called from a Worker, which already runs on a background thread.
     * Returns whether the send succeeded, so the caller can decide retry vs. done.
     */
    fun captureBatch(batch: List<Map<String, Any?>>): Boolean {
        val body = gson.toJson(mapOf("api_key" to API_KEY, "batch" to batch))
        return postJson(BATCH_ENDPOINT, body)
    }

    private fun postJson(url: String, body: String): Boolean {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")
            .build()

        return try {
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: IOException) {
            Log.e(TAG, "PostHog send failed: ${e.message}")
            false
        }
    }
}
