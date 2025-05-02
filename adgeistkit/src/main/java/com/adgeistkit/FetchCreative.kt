package com.adgeistkit

import com.adgeistkit.CreativeDataModel
import android.util.Log
import android.content.Context
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FetchCreative(private val context: Context, private val deviceIdentifier: DeviceIdentifier) {
    private val scope = CoroutineScope(Dispatchers.Main)

    fun fetchCreative(
        adSpaceId: String,
        publisherId: String,
        callback: (CreativeDataModel?) -> Unit
    ) {
        scope.launch {
            val deviceId = deviceIdentifier.getDeviceIdentifier()
            Log.d("MyData", "${deviceId}----------------------------")

            val url =
                "https://beta-api.adgeist.ai/campaign/dummy?adSpaceId=$adSpaceId&companyId=$publisherId"
            val request = Request.Builder()
                .url(url)
                .header("Origin", "https://beta.adgeist.ai")
                .build()
            val client = OkHttpClient()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.d("MyData", "Failed to fetch ad data")
                    callback(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    val jsonString = response.body?.string()
                    val adData = jsonString?.let { parseCreativeData(it) }

                    Log.d("MyData", "${adData}")
                    callback(adData)
                }
            })
        }
    }

    private fun parseCreativeData(json: String): CreativeDataModel {
        return Gson().fromJson(json, CreativeDataModel::class.java)
    }
}