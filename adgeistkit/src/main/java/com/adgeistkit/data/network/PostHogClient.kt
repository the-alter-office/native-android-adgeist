package com.adgeistkit.data.network

import com.adgeistkit.AdgeistCore
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

internal object PostHogClient {

    private const val API_KEY = "phc_kqTQTisRrHXss5HknH3g7XbPdyrEAw49oa3ssRUaxaSK"
    private const val EVENT_ENDPOINT = "https://us.i.posthog.com/i/v0/e/"

    private val JSON = "application/json".toMediaType()

    fun capture(event: String, properties: Map<String, Any?>) {
        if (!AdgeistCore.isInitialized()) return
        val core = AdgeistCore.getInstance()
        if (!core.isPerformanceTelemetryEnabled()) return

        core.ioScope.launch {
            val distinctId = runCatching { core.deviceIdentifier.getDeviceIdentifier() }
                .getOrNull() ?: return@launch

            val props = JSONObject()
            properties.forEach { (key, value) -> props.put(key, value ?: JSONObject.NULL) }
            props.put("sdk_version", core.version)
            props.put("app_bundle_id", core.packageOrBundleID)
            props.put("adgeist_app_id", core.adgeistAppID)
            props.put("\$lib", "adgeist-android")

            val payload = JSONObject()
                .put("api_key", API_KEY)
                .put("event", event)
                .put("distinct_id", distinctId)
                .put("properties", props)
                .toString()

            val request = Request.Builder()
                .url(EVENT_ENDPOINT)
                .post(payload.toRequestBody(JSON))
                .build()

            try {
                NetworkModule.httpClient.newCall(request).execute().close()
            } catch (_: IOException) {
            }
        }
    }
}

