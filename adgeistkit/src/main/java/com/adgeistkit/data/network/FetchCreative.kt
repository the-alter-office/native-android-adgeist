package com.adgeistkit.data.network

import android.os.SystemClock
import com.adgeistkit.AdgeistCore
import com.adgeistkit.ads.AdgeistEventCode
import com.adgeistkit.constants.General
import com.adgeistkit.constants.Logs
import com.adgeistkit.utilities.logD
import com.adgeistkit.benchmark.FetchTimings
import com.adgeistkit.request.FetchCreativeRequest
import com.adgeistkit.data.models.FixedAdResponse
import com.adgeistkit.data.models.AdData
import com.adgeistkit.data.models.AdResponseData
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

public class FetchCreative(private val adgeistCore: AdgeistCore) {
    public companion object {
        private const val TAG = "FetchCreative"

        private val gson = Gson()

        private val utcFormat = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat =
                SimpleDateFormat(General.Network.UTC_TIMESTAMP_PATTERN, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
        }

        private fun utcTimestamp(): String = utcFormat.get()!!.format(Date())
    }

    private val scope = adgeistCore.ioScope

    private val bidRequestBackendDomain = adgeistCore.bidRequestBackendDomain

    private val packageID = adgeistCore.packageOrBundleID
    private val adgeistAppID = adgeistCore.adgeistAppID

    private val deviceIdentifier = adgeistCore.deviceIdentifier
    private val targetingInfo = adgeistCore.targetingInfo

    public fun fetchCreative(
        adUnitID: String,
        callback: (AdData) -> Unit
    ) {
        val tEntry = SystemClock.elapsedRealtime()

        scope.launch {
            val tStart = SystemClock.elapsedRealtime()

            AnalyticsRetryQueue.flushNow(adgeistCore.context)

            val deviceId = deviceIdentifier.getDeviceIdentifier()
            val tDeviceId = SystemClock.elapsedRealtime()

            val url = "$bidRequestBackendDomain${General.Network.AD_PATH}"

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

            val currentTimestamp = utcTimestamp()
                
            requestBuilder
                .setPlatform("ANDROID")
                .setDeviceId(deviceId ?: "")
                .setTimeZone(TimeZone.getDefault().id)
                .setRequestedAt(currentTimestamp)
                .setSdkVersion(adgeistCore.version)

            val fetchCreativeRequest = requestBuilder.build()
            val requestPayload = fetchCreativeRequest.toJson().toString()
            val requestBody = requestPayload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Content-Type", "application/json")
                .header("Origin",packageID)
                .build()

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
                    callback(createErrorProp(AdgeistEventCode.AE2, timings = timings()))
                }

                override fun onResponse(call: Call, response: Response) {
                    val tHeaders = SystemClock.elapsedRealtime()
                    val jsonString = try {
                        response.body?.string()
                    } catch (e: Exception) {
                        callback(createErrorProp(AdgeistEventCode.AE2, response.code, timings(tHeaders)))
                        return
                    }
                    val tBody = SystemClock.elapsedRealtime()

                    fun fail(eventCode: AdgeistEventCode, code: Int? = null) =
                        callback(createErrorProp(eventCode, code, timings(tHeaders, tBody)))

                    if (jsonString.isNullOrBlank()) {
                        val eventCode = if (response.isSuccessful) AdgeistEventCode.AE1 else codeForHttpStatus(response.code)
                        fail(eventCode, response.code)
                        return
                    }

                    if (!response.isSuccessful) {
                        fail(codeForHttpStatus(response.code), response.code)
                        return
                    }

                    try {
                        val parsed = parseCreativeData(jsonString)
                        
                        if (parsed == null) {
                            fail(AdgeistEventCode.AW10)
                        } else if ((parsed as? FixedAdResponse)?.creativesV1.isNullOrEmpty()) {
                            fail(AdgeistEventCode.AE1, response.code)
                        } else {
                            callback(AdData(data = parsed, error = null, statusCode = response.code, timings = timings(tHeaders, tBody)))
                        }
                    } catch (e: Exception) {
                        fail(AdgeistEventCode.AW10)
                    }
                }
            })
        }
    }

    private fun codeForHttpStatus(statusCode: Int): AdgeistEventCode =
        if (statusCode in 400..499) AdgeistEventCode.AW6 else AdgeistEventCode.AE2

    private fun createErrorProp(
        eventCode: AdgeistEventCode,
        statusCode: Int? = null,
        timings: FetchTimings? = null
    ): AdData {
        return AdData(
            data = null,
            error = eventCode,
            statusCode = statusCode,
            timings = timings
        )
    }

    private fun parseCreativeData(json: String): AdResponseData? {
        return gson.fromJson(json, FixedAdResponse::class.java)
    }
}
