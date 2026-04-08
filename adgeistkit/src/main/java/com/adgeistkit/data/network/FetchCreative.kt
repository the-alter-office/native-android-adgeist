package com.adgeistkit.data.network

import android.util.Log
import com.adgeistkit.AdgeistCore
import com.adgeistkit.request.FetchCreativeRequest
import com.adgeistkit.data.models.CPMAdResponse
import com.adgeistkit.data.models.FixedAdResponse
import com.adgeistkit.data.models.AdData
import com.adgeistkit.data.models.AdErrorResponse
import com.adgeistkit.data.models.AdResponseData
import com.adgeistkit.data.models.AdVisibilityError
import com.adgeistkit.logging.EventCollector
import com.adgeistkit.logging.SdkShield
import okhttp3.*
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    private val scope = CoroutineScope(Dispatchers.Main)

    private val bidRequestBackendDomain = adgeistCore.bidRequestBackendDomain

    private val packageID = adgeistCore.packageOrBundleID
    private val adgeistAppID = adgeistCore.adgeistAppID

    private val deviceIdentifier = adgeistCore.deviceIdentifier
    private val networkUtils = adgeistCore.networkUtils
    private val targetingInfo = adgeistCore.targetingInfo

    fun fetchCreative(
        adUnitID: String,
        buyType: String,
        isTestEnvironment: Boolean = true,
        callback: (AdData) -> Unit
    ) {
        scope.launch {
            val deviceId = deviceIdentifier.getDeviceIdentifier()
            val userIP = networkUtils.getLocalIpAddress()
                ?: networkUtils.getWifiIpAddress()
                ?: "unknown"

            val envFlag = if (isTestEnvironment) "1" else "0"

            val url = if (buyType == "FIXED") {
                "$bidRequestBackendDomain/v2/dsp/ad/fixed"
            } else {
                "$bidRequestBackendDomain/v1/app/ssp/bid?adSpaceId=$adUnitID&companyId=$adgeistAppID&test=$envFlag"
            }

            val requestBuilder = FetchCreativeRequest.FetchCreativeRequestBuilder(
                adSpaceId = adUnitID,
                companyId = adgeistAppID,
                isTest = isTestEnvironment
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

            val client = OkHttpClient()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    SdkShield.runSafely("FetchCreative.onFailure") {
                        Log.d(TAG, "Request Failed: ${bidRequestBackendDomain} - ${e.message}")

                        val eventName = if (e is java.net.SocketTimeoutException) "network_timeout" else "network_error"
                        val params = mutableMapOf<String, Any>(
                            "endpoint" to url,
                            "error_class" to e.javaClass.simpleName,
                            "message" to (e.message ?: "Unknown")
                        )
                        if (e is java.net.SocketTimeoutException) {
                            params["timeout_ms"] = 10000
                        }
                        EventCollector.logEvent(eventName, params)
                    }
                    callback(createErrorProp(e.message ?: "Failed to connect to server"))
                }

                override fun onResponse(call: Call, response: Response) {
                    SdkShield.runSafely("FetchCreative.onResponse") {
                    val jsonString = response.body?.string()

                    if (jsonString.isNullOrBlank()) {
                        callback(createErrorProp("Server returned empty response", response.code))
                        return
                    }

                    if (!response.isSuccessful) {                        
                        val errorMessage = try {
                            val errorResponse = Gson().fromJson(jsonString, AdErrorResponse::class.java)
                            errorResponse.Error
                        } catch (e: Exception) {
                            response.message.ifEmpty { "Request failed" }
                        }
                        
                        callback(createErrorProp(errorMessage, response.code))
                        return
                    }

                    try {
                        val parsed = parseCreativeData(jsonString, buyType)
                        
                        if (parsed == null) {
                            callback(createErrorProp("Failed to parse creative data"))
                        } else if (isEmptyCreative(parsed)) {
                            callback(createErrorProp("No valid ad creative available"))
                        } else {
                            callback(AdData(data = parsed, error = null, statusCode = response.code))
                        }
                    } catch (e: Exception) {
                        callback(createErrorProp(e.message ?: "Failed to parse ad response"))
                    }
                    }
                }

            })
        }
    }

    private fun createErrorProp(errorMessage: String, statusCode: Int? = null): AdData {
        return AdData(
            data = null,
            error = AdVisibilityError(errorMessage),
            statusCode = statusCode
        )
    }

    private fun isEmptyCreative(ad: AdResponseData): Boolean {
        return when (ad) {
            is FixedAdResponse -> {
                ad.id.isNullOrEmpty() ||
                        ad.campaignId.isNullOrEmpty() ||
                        ad.advertiser == null
            }
            is CPMAdResponse -> {
                ad.data?.seatBid.isNullOrEmpty()
            }
            else -> false
        }
    }

    private fun parseCreativeData(json: String, buyType: String): AdResponseData? {
        return if (buyType == "FIXED") {
            Gson().fromJson(json, FixedAdResponse::class.java)
        } else {
            Gson().fromJson(json, CPMAdResponse::class.java)
        }
    }
}
