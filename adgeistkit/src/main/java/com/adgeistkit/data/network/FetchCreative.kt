package com.adgeistkit.data.network

import android.os.SystemClock
import android.util.Log
import com.adgeistkit.AdgeistCore
import com.adgeistkit.benchmark.FetchTimings
import com.adgeistkit.request.FetchCreativeRequest
import com.adgeistkit.data.models.FixedAdResponse
import com.adgeistkit.data.models.AdData
import com.adgeistkit.data.models.AdErrorResponse
import com.adgeistkit.data.models.AdResponseData
import com.adgeistkit.data.models.AdVisibilityError
import okhttp3.*
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class FetchCreative(private val adgeistCore: AdgeistCore) {
    companion object {
        private const val TAG = "FetchCreative"
    }

    private val scope = adgeistCore.ioScope

    private val bidRequestBackendDomain = adgeistCore.bidRequestBackendDomain

    private val packageID = adgeistCore.packageOrBundleID
    private val adgeistAppID = adgeistCore.adgeistAppID

    private val deviceIdentifier = adgeistCore.deviceIdentifier
    private val networkSignals = adgeistCore.networkSignals
    private val targetingInfo = adgeistCore.targetingInfo

    fun fetchCreative(
        adUnitID: String,
        buyType: String,
        callback: (AdData) -> Unit
    ) {
        val tEntry = SystemClock.elapsedRealtime()

        scope.launch {
            val tStart = SystemClock.elapsedRealtime()

            AnalyticsRetryQueue.flushNow(adgeistCore.context, NetworkModule.httpClient)

            val deviceId = deviceIdentifier.getDeviceIdentifier()
            val tDeviceId = SystemClock.elapsedRealtime()

            val userIP = networkSignals.getLocalIpAddress()
                ?: networkSignals.getWifiIpAddress()
                ?: "unknown"

            val url = "$bidRequestBackendDomain/v2/dsp/ad"


            val requestBuilder = FetchCreativeRequest.FetchCreativeRequestBuilder(
                adSpaceId = adUnitID,
                companyId = adgeistAppID
            )

            targetingInfo?.let {
                val deviceMetrics = it.get("deviceTargetingMetrics") as? Map<String, Any>
                deviceMetrics?.let { metrics ->
                    requestBuilder.setDevice(metrics)
                }
            }

            if (buyType == "FIXED") {
                val utcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                utcFormat.timeZone = TimeZone.getTimeZone("UTC")
                val currentTimestamp = utcFormat.format(Date())
                
                requestBuilder
                    .setPlatform("ANDROID")
                    .setDeviceId(deviceId ?: "")
                    .setTimeZone(TimeZone.getDefault().id)
                    .setRequestedAt(currentTimestamp)
                    .setSdkVersion(adgeistCore.version)
            } else {
                requestBuilder.setAppDto("itwcrm", "com.itwcrm")
            }

            val fetchCreativeRequest = requestBuilder.build()
            val requestPayload = fetchCreativeRequest.toJson().toString()
            val requestBody = requestPayload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

            val request = if (buyType == "FIXED") {
                Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .header("Content-Type", "application/json")
                    .header("Origin",packageID)
                    .build()
            } else {
                Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .header("Content-Type", "application/json")
                    .header("Origin", packageID)
                    .header("x-user-id", deviceId ?: "")
                    .header("x-platform", "mobile_app")
                    .header("x-forwarded-for", userIP)
                    .build()
            }

            val tEnqueue = SystemClock.elapsedRealtime()

            fun timings(tHeaders: Long? = null, tBody: Long? = null): FetchTimings {
                val now = SystemClock.elapsedRealtime()
                return FetchTimings(
                    queueWaitMs = tStart - tEntry,
                    deviceIdMs = tDeviceId - tStart,
                    requestBuildMs = tEnqueue - tDeviceId,
                    networkRttMs = (tHeaders ?: now) - tEnqueue,
                    bodyReadMs = if (tHeaders != null && tBody != null) tBody - tHeaders else 0L,
                    responseParseMs = if (tBody != null) now - tBody else 0L,
                )
            }

            NetworkModule.httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.d(TAG, "Request Failed: ${bidRequestBackendDomain} - ${e.message}")
                    callback(createErrorProp(e.message ?: "Failed to connect to server", timings = timings()))
                }

                override fun onResponse(call: Call, response: Response) {
                    val tHeaders = SystemClock.elapsedRealtime()
                    val jsonString = response.body?.string()
                    val tBody = SystemClock.elapsedRealtime()

                    fun fail(message: String, code: Int? = null) =
                        callback(createErrorProp(message, code, timings(tHeaders, tBody)))

                    if (jsonString.isNullOrBlank()) {
                        fail("Server returned empty response", response.code)
                        return
                    }

                    if (!response.isSuccessful) {                        
                        val errorMessage = try {
                            val errorResponse = Gson().fromJson(jsonString, AdErrorResponse::class.java)
                            errorResponse.Error
                        } catch (e: Exception) {
                            response.message.ifEmpty { "Request failed" }
                        }
                        
                        fail(errorMessage, response.code)
                        return
                    }

                    try {
                        val parsed = parseCreativeData(jsonString, buyType)
                        
                        if (parsed == null) {
                            fail("Failed to parse creative data")
                        } else if (isEmptyCreative(parsed)) {
                            fail("No valid ad creative available")
                        } else {
                            callback(AdData(data = parsed, error = null, statusCode = response.code, timings = timings(tHeaders, tBody)))
                        }
                    } catch (e: Exception) {
                        fail(e.message ?: "Failed to parse ad response")
                    }
                }

            })
        }
    }

    private fun createErrorProp(
        errorMessage: String,
        statusCode: Int? = null,
        timings: FetchTimings? = null
    ): AdData {
        return AdData(
            data = null,
            error = AdVisibilityError(errorMessage),
            statusCode = statusCode,
            timings = timings
        )
    }

    private fun isEmptyCreative(ad: AdResponseData): Boolean {
        return when (ad) {
            is FixedAdResponse -> {
                ad.id.isNullOrEmpty() ||
                        ad.campaignId.isNullOrEmpty() ||
                        ad.advertiser == null
            }
            else -> false
        }
    }

    private fun parseCreativeData(json: String, buyType: String): AdResponseData? {
        return Gson().fromJson(json, FixedAdResponse::class.java)
    }
}
