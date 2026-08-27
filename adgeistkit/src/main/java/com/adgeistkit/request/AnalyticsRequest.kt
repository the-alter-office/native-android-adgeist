package com.adgeistkit.request

import org.json.JSONObject

class AnalyticsRequest private constructor(analyticsRequest: AnalyticsRequestBuilder) {
    //Required
    private val metaData = analyticsRequest.metaData

    //Optional
    private val type: String?
    private val visibilityRatio: Float
    private val scrollDepth: Float
    private val viewTime: Long
    private val timeToVisible: Long

    init {
        this.type = analyticsRequest.type
        this.visibilityRatio = analyticsRequest.visibilityRatio
        this.scrollDepth = analyticsRequest.scrollDepth
        this.viewTime = analyticsRequest.viewTime
        this.timeToVisible = analyticsRequest.timeToVisible
    }

    class AnalyticsRequestBuilder(//Required
        internal val metaData: String
    ) {
        //Optional
        var type: String? = null
        var visibilityRatio: Float = 0f
        var scrollDepth: Float = 0f
        var viewTime: Long = 0
        var timeToVisible: Long = 0

        fun trackViewableImpression(
            timeToVisible: Long,
            scrollDepth: Float,
            visibilityRatio: Float,
            viewTime: Long
        ): AnalyticsRequestBuilder {
            this.type = "VIEW"
            this.timeToVisible = timeToVisible
            this.scrollDepth = scrollDepth
            this.visibilityRatio = visibilityRatio
            this.viewTime = viewTime
            return this
        }

        fun trackClick(): AnalyticsRequestBuilder {
            this.type = "CLICK"
            return this
        }


        fun build(): AnalyticsRequest {
            return AnalyticsRequest(this)
        }
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
        try {
            json.put("metaData", metaData)
            json.put("type", type)

            when (type) {
                "VIEW" -> {
                    json.put("timeToVisible", timeToVisible)
                    json.put("scrollDepth", scrollDepth.toDouble())
                    json.put("visibilityRatio", visibilityRatio.toDouble())
                    json.put("viewTime", viewTime)
                }

                "CLICK" -> {}
                else -> {}
            }
        } catch (ignored: Exception) {
        }
        return json
    }
}
