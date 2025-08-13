package com.adgeistkit

import android.content.Context
import android.util.Log
import okhttp3.*
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaTypeOrNull

class FetchCreative(
    private val context: Context,
    private val deviceIdentifier: DeviceIdentifier,
    private val networkUtils: NetworkUtils,
    private val domain: String
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    fun fetchCreative(
        apiKey: String,
        origin: String,
        adSpaceId: String,
        companyId: String,
        isTestEnvironment: Boolean = true,
        callback: (CreativeDataModel?) -> Unit
    ) {
        scope.launch {

            val deviceId = deviceIdentifier.getDeviceIdentifier()
            val userIP = networkUtils.getLocalIpAddress() ?: networkUtils.getWifiIpAddress() ?: "unknown"

            val envFlag = if (isTestEnvironment) "1" else "0"
            val url = "https://$domain/app/ssp/bid?adSpaceId=$adSpaceId&companyId=$companyId&test=$envFlag"

            val jsonBody = """
                {
                    "appDto": {
                        "name": "itwcrm",
                        "bundle": "com.itwcrm" 
                    }
                }
            """.trimIndent()

            val requestBody = RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), jsonBody)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Content-Type", "application/json")
                .header("Origin", origin)
                .header("x-user-id", deviceId)
                .header("x-platform", "mobile_app")
                .header("x-api-key", apiKey)
                .header("x-forwarded-for", userIP)
                .build()

            val client = OkHttpClient()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.d("MyData", "Request Failed: ${e.message}")
                    callback(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    val jsonString = response.body?.string()
                    val adData = jsonString?.let { parseCreativeData(it) }

                    Log.d("MyData", "Response: ${adData}")
                    callback(adData)
                }
            })
        }
    }

    private fun parseCreativeData(json: String): CreativeDataModel {
        return Gson().fromJson(json, CreativeDataModel::class.java)
    }
}
