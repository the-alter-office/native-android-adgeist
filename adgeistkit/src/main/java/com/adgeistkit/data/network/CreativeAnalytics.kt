package com.adgeistkit.data.network

import android.util.Log
import com.adgeistkit.AdgeistCore
import com.adgeistkit.logging.HttpRequestLog
import com.adgeistkit.request.AnalyticsRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class CreativeAnalytics(private val adgeistCore: AdgeistCore, private val httpRequestLog: HttpRequestLog? = null) {
    companion object {
        private const val TAG = "CreativeAnalytics"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val client = OkHttpClient()

    private val bidRequestBackendDomain = adgeistCore.bidRequestBackendDomain

    private val packageOrBundleID = adgeistCore.packageOrBundleID
    private val adgeistAppID = adgeistCore.adgeistAppID

    private val deviceIdentifier = adgeistCore.deviceIdentifier
    private val networkUtils = adgeistCore.networkUtils


    fun sendTrackingDataV2(analyticsRequest: AnalyticsRequest){
        scope.launch {
            val url = "$bidRequestBackendDomain/v2/ssp/impression";

            val requestPayload = analyticsRequest.toJson().toString();
            val requestBody = requestPayload.toRequestBody("application/json".toMediaType());

            val request = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val fetchStartTime = System.currentTimeMillis()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    httpRequestLog?.record(
                        method = "POST",
                        url = url,
                        latencyMs = System.currentTimeMillis() - fetchStartTime,
                        requestHeaders = request.headers,
                        requestPayload = requestPayload,
                        errorMessage = e.message
                    )
                    Log.d(TAG, "Failed to send tracking data: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val responseBody = response.body?.string()
                        httpRequestLog?.record(
                            method = "POST",
                            url = url,
                            statusCode = response.code,
                            latencyMs = System.currentTimeMillis() - fetchStartTime,
                            requestHeaders = request.headers,
                            responseHeaders = response.headers,
                            requestPayload = requestPayload,
                            responsePayload = responseBody,
                        )
                        if (!response.isSuccessful) {
                            Log.d(TAG, "Request failed with code: ${response.code}, message: ${responseBody ?: "No error message"}")
                            return
                        }
                    }
                }
            })
        }
    }
}