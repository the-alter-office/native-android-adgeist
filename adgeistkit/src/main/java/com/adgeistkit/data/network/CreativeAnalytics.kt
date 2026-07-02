package com.adgeistkit.data.network

import android.util.Log
import com.adgeistkit.AdgeistCore
import com.adgeistkit.logging.HttpRequestLog
import com.adgeistkit.request.AnalyticsRequest
import com.adgeistkit.request.AnalyticsRequestDEPRECATED
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

    fun sendTrackingData(analyticsRequestDEPRECATED: AnalyticsRequestDEPRECATED) {
        scope.launch {
            val envFlag = if (analyticsRequestDEPRECATED.isTestMode) "1" else "0"

            val url =  if (analyticsRequestDEPRECATED.buyType == "FIXED") {
                "$bidRequestBackendDomain/v2/ssp/impression"
            } else {
                "$bidRequestBackendDomain/api/analytics/track?adSpaceId=${analyticsRequestDEPRECATED.adUnitID}&companyId=$adgeistAppID&test=$envFlag"
            }

            val deviceId = deviceIdentifier.getDeviceIdentifier()
            val userIP = networkUtils.getLocalIpAddress() ?: networkUtils.getWifiIpAddress() ?: "unknown"

            val requestPayload = analyticsRequestDEPRECATED.toJson().toString();
            val requestBody = requestPayload.toRequestBody("application/json".toMediaType())

            val request = if (analyticsRequestDEPRECATED.buyType == "FIXED") {
                Request.Builder()
                    .url(url)
                    .header("Content-Type", "application/json")
                    .post(requestBody)
                    .build()
            } else {
                Request.Builder()
                    .url(url)
                    .header("Content-Type", "application/json")
                    .header("Origin", packageOrBundleID)
                    .header("x-user-id", deviceId ?: "")
                    .header("x-platform", "mobile_app")
                    .header("x-forwarded-for", userIP)
                    .post(requestBody)
                    .build()
            }

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
                        Log.d(TAG, "Tracking data sent successfully: $responseBody")
                    }
                }
            })
        }
    }
}