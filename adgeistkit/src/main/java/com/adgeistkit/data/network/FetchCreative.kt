package com.adgeistkit.data.network

import android.util.Log
import com.adgeistkit.AdgeistCore
import com.adgeistkit.data.models.CPMAdResponse
import com.adgeistkit.data.models.FixedAdResponse
import okhttp3.*
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.TimeZone

class FetchCreative(private val adgeistCore: AdgeistCore) {
    companion object {
        private const val TAG = "FetchCreative"
    }

    private val scope = CoroutineScope(Dispatchers.Main)

    private val bidRequestBackendDomain = adgeistCore.bidRequestBackendDomain

    private val packageID = adgeistCore.packageOrBundleID
    private val adgeistAppID = adgeistCore.adgeistAppID
    private val apiKey = adgeistCore.apiKey

    private val deviceIdentifier = adgeistCore.deviceIdentifier
    private val networkUtils = adgeistCore.networkUtils
    private val targetingInfo = adgeistCore.targetingInfo

    fun fetchCreative(
        adUnitID: String,
        buyType: String,
        isTestEnvironment: Boolean = true,
        callback: (Any?) -> Unit
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

            val payload = mutableMapOf<String, Any>()

            targetingInfo?.let {
                payload["device"] = it.get("deviceTargetingMetrics") ?: mapOf<String, Any>()
            }

            if (buyType == "FIXED") {
                payload["platform"] = "ANDROID"
                payload["deviceId"] = deviceId ?: ""
                payload["adspaceId"] = adUnitID
                payload["companyId"] = adgeistAppID
                payload["timeZone"] = TimeZone.getDefault().id
            } else {
                payload["appDto"] = mapOf(
                    "name" to "itwcrm",
                    "bundle" to "com.itwcrm"
                )
            }

            payload["origin"] = packageID
            payload["isTest"] = isTestEnvironment

            val requestPayload = Gson().toJson(payload)
            val requestBody = requestPayload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

            val request = if (buyType == "FIXED") {
                Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .header("Content-Type", "application/json")
                    .build()
            } else {
                Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .header("Content-Type", "application/json")
                    .header("Origin", packageID)
                    .header("x-user-id", deviceId ?: "")
                    .header("x-platform", "mobile_app")
                    .header("x-api-key", apiKey)
                    .header("x-forwarded-for", userIP)
                    .build()
            }

            val client = OkHttpClient()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.d(TAG, "Request Failed: ${bidRequestBackendDomain} - ${e.message}")
                    callback(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    val jsonString = response.body?.string()

                    if (!response.isSuccessful || jsonString.isNullOrBlank()) {
                        callback(null)
                        return
                    }

                    val adData = try {
                        val parsed = parseCreativeData(jsonString, buyType)
                        if (parsed == null || isEmptyCreative(parsed)) {
                            null
                        } else {
                            parsed
                        }
                    } catch (e: Exception) {
                        null
                    }

                    callback(adData)
                }

            })
        }
    }

    private fun isEmptyCreative(ad: Any): Boolean {
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

    private fun parseCreativeData(json: String, buyType: String): Any {
        return if (buyType == "FIXED") {
            Gson().fromJson(json, FixedAdResponse::class.java)
        } else {
            Gson().fromJson(json, CPMAdResponse::class.java)
        }
    }
}
