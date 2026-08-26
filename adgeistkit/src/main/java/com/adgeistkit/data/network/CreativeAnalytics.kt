package com.adgeistkit.data.network

import com.adgeistkit.AdgeistCore
import com.adgeistkit.request.AnalyticsRequest
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class CreativeAnalytics(private val adgeistCore: AdgeistCore) {

    private val scope = adgeistCore.ioScope
    private val client = NetworkModule.httpClient

    private val bidRequestBackendDomain = adgeistCore.bidRequestBackendDomain

    private val packageOrBundleID = adgeistCore.packageOrBundleID
    private val adgeistAppID = adgeistCore.adgeistAppID

    private val deviceIdentifier = adgeistCore.deviceIdentifier
    private val networkSignals = adgeistCore.networkSignals

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


            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    AnalyticsRetryQueue.enqueue(adgeistCore.context, client, url, requestPayload)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (it.isSuccessful) return

                        if (RetryPolicy.isRetryable(it.code)) {
                            AnalyticsRetryQueue.enqueue(
                                adgeistCore.context, client, url, requestPayload, it
                            )
                        }
                    }
                }
            })
        }
    }

}