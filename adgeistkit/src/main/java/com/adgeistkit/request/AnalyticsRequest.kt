package com.adgeistkit.request

import org.json.JSONObject

public class AnalyticsRequest private constructor(analyticsRequest: AnalyticsRequestBuilder) {
    //Required
    private val metaData = analyticsRequest.metaData

    //Optional
    private val type: String?
    private val visibilityRatio: Float
    private val scrollDepth: Float
    private val timeToVisible: Long

    init {
        this.type = analyticsRequest.type
        this.visibilityRatio = analyticsRequest.visibilityRatio
        this.scrollDepth = analyticsRequest.scrollDepth
        this.timeToVisible = analyticsRequest.timeToVisible
    }

    public class AnalyticsRequestBuilder(//Required
        internal val metaData: String
    ) {
        //Optional
        public var type: String? = null
        public var visibilityRatio: Float = 0f
        public var scrollDepth: Float = 0f
        public var timeToVisible: Long = 0

        public fun trackViewableImpression(
            timeToVisible: Long,
            scrollDepth: Float,
            visibilityRatio: Float
        ): AnalyticsRequestBuilder {
            this.type = "VIEW"
            this.timeToVisible = timeToVisible
            this.scrollDepth = scrollDepth
            this.visibilityRatio = visibilityRatio
            return this
        }

        public fun trackClick(): AnalyticsRequestBuilder {
            this.type = "CLICK"
            return this
        }


        public fun build(): AnalyticsRequest {
            return AnalyticsRequest(this)
        }
    }

    public fun toJson(): JSONObject {
        val json = JSONObject()
        try {
            json.put("metaData", metaData)
            json.put("type", type)

            when (type) {
                "VIEW" -> {
                    json.put("timeToVisible", timeToVisible)
                    json.put("scrollDepth", scrollDepth.toDouble())
                    json.put("visibilityRatio", visibilityRatio.toDouble())
                }

                "CLICK" -> {}
                else -> {}
            }
        } catch (ignored: Exception) {
        }
        return json
    }
}
