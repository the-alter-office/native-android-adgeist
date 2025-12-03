package com.adgeistkit.data.network

import android.content.Context
import android.util.Log
import com.adgeistkit.core.device.DeviceIdentifier
import com.adgeistkit.core.device.NetworkUtils
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

class FetchCreative(
    private val context: Context,
    private val deviceIdentifier: DeviceIdentifier,
    private val networkUtils: NetworkUtils,
    private val domain: String,
    private val targetingInfo: Map<String, Any?>?
) {
    companion object {
        private const val TAG = "FetchCreative"
    }

    private val scope = CoroutineScope(Dispatchers.Main)

    fun fetchCreative(
        apiKey: String,
        origin: String,
        adSpaceId: String,
        companyId: String,
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
                "https://$domain/v2/dsp/ad/fixed"
            } else {
                "https://$domain/v1/app/ssp/bid?adSpaceId=$adSpaceId&companyId=$companyId&test=$envFlag"
            }

            val payload = mutableMapOf<String, Any>()

            targetingInfo?.let {
                payload["device"] = it.get("deviceTargetingMetrics") ?: mapOf<String, Any>()
            }

            if (buyType == "FIXED") {
                payload["adspaceId"] = adSpaceId
                payload["companyId"] = companyId
                payload["timeZone"] = TimeZone.getDefault().id
            } else {
                payload["appDto"] = mapOf(
                    "name" to "itwcrm",
                    "bundle" to "com.itwcrm"
                )
            }

            payload["origin"] = origin
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
                    .header("Origin", origin)
                    .header("x-user-id", deviceId)
                    .header("x-platform", "mobile_app")
                    .header("x-api-key", apiKey)
                    .header("x-forwarded-for", userIP)
                    .build()
            }

            val client = OkHttpClient()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.d(TAG, "Request Failed: ${e.message}")
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
