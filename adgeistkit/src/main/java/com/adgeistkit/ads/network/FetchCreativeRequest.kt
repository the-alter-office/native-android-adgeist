package com.adgeistkit.ads.network

import org.json.JSONObject

class FetchCreativeRequest private constructor(builder: FetchCreativeRequestBuilder) {
    //Required
    private val adSpaceId = builder.adSpaceId
    private val companyId = builder.companyId
    private val isTest = builder.isTest

    //Optional
    private val platform: String?
    private val deviceId: String?
    private val timeZone: String?
    private val requestedAt: String?
    private val sdkVersion: String?
    private val device: Map<String, Any>?
    private val appDto: Map<String, String>?

    init {
        this.platform = builder.platform
        this.deviceId = builder.deviceId
        this.timeZone = builder.timeZone
        this.requestedAt = builder.requestedAt
        this.sdkVersion = builder.sdkVersion
        this.device = builder.device
        this.appDto = builder.appDto
    }

    class FetchCreativeRequestBuilder(
        //Required
        internal val adSpaceId: String,
        internal val companyId: String,
        internal val isTest: Boolean
    ) {
        //Optional
        var platform: String? = null
        var deviceId: String? = null
        var timeZone: String? = null
        var requestedAt: String? = null
        var sdkVersion: String? = null
        var device: Map<String, Any>? = null
        var appDto: Map<String, String>? = null

        fun setPlatform(platform: String): FetchCreativeRequestBuilder {
            this.platform = platform
            return this
        }

        fun setDeviceId(deviceId: String): FetchCreativeRequestBuilder {
            this.deviceId = deviceId
            return this
        }

        fun setTimeZone(timeZone: String): FetchCreativeRequestBuilder {
            this.timeZone = timeZone
            return this
        }

        fun setRequestedAt(requestedAt: String): FetchCreativeRequestBuilder {
            this.requestedAt = requestedAt
            return this
        }

        fun setSdkVersion(sdkVersion: String): FetchCreativeRequestBuilder {
            this.sdkVersion = sdkVersion
            return this
        }

        fun setDevice(device: Map<String, Any>): FetchCreativeRequestBuilder {
            this.device = device
            return this
        }

        fun setAppDto(appName: String, appBundle: String): FetchCreativeRequestBuilder {
            this.appDto = mapOf(
                "name" to appName,
                "bundle" to appBundle
            )
            return this
        }

        fun build(): FetchCreativeRequest {
            return FetchCreativeRequest(this)
        }
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
        try {
            json.put("isTest", isTest)

            device?.let {
                json.put("device", JSONObject(it))
            }

            platform?.let { json.put("platform", it) }
            deviceId?.let { json.put("deviceId", it) }
            json.put("adspaceId", adSpaceId)
            json.put("companyId", companyId)
            timeZone?.let { json.put("timeZone", it) }
            requestedAt?.let { json.put("requestedAt", it) }
            sdkVersion?.let { json.put("sdkVersion", it) }
            
            appDto?.let {
                json.put("appDto", JSONObject(it))
            }
        } catch (ignored: Exception) {
        }
        return json
    }

    fun getAdSpaceId(): String = adSpaceId
    fun getCompanyId(): String = companyId
    fun isTestEnvironment(): Boolean = isTest
}
