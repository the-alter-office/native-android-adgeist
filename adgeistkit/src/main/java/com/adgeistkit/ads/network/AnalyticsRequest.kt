package com.adgeistkit.ads.network

import org.json.JSONObject

class AnalyticsRequest private constructor(analyticsRequest: AnalyticsRequestBuilder) {
    //Required
    private val metaData = analyticsRequest.metaData
    private val isTestMode = analyticsRequest.isTestMode

    //Optional
    private val type: String?
    private val renderTime: Long
    private val visibilityRatio: Float
    private val scrollDepth: Float
    private val viewTime: Long
    private val timeToVisible: Long
    private val totalViewTime: Long
    private val totalPlaybackTime: Long

    init {
        this.type = analyticsRequest.type
        this.renderTime = analyticsRequest.renderTime
        this.visibilityRatio = analyticsRequest.visibilityRatio
        this.scrollDepth = analyticsRequest.scrollDepth
        this.viewTime = analyticsRequest.viewTime
        this.timeToVisible = analyticsRequest.timeToVisible
        this.totalViewTime = analyticsRequest.totalViewTime
        this.totalPlaybackTime = analyticsRequest.totalPlaybackTime
    }

    class AnalyticsRequestBuilder(//Required
        internal val metaData: String, val isTestMode: Boolean
    ) {
        //Optional
        var type: String? = null
        var renderTime: Long = 0
        var visibilityRatio: Float = 0f
        var scrollDepth: Float = 0f
        var viewTime: Long = 0
        var timeToVisible: Long = 0
        var totalViewTime: Long = 0
        var totalPlaybackTime: Long = 0

        fun trackImpression(renderTime: Long): AnalyticsRequestBuilder {
            this.type = "IMPRESSION"
            this.renderTime = renderTime
            return this
        }

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

        fun trackTotalViewTime(totalViewTime: Long): AnalyticsRequestBuilder {
            this.type = "TOTAL_VIEW_TIME"
            this.totalViewTime = totalViewTime
            return this
        }

        fun trackTotalPlaybackTime(totalPlaybackTime: Long): AnalyticsRequestBuilder {
            this.type = "TOTAL_PLAYBACK_TIME"
            this.totalPlaybackTime = totalPlaybackTime
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
            json.put("isTestMode", isTestMode)
            json.put("type", type)

            when (type) {
                "IMPRESSION" -> json.put("renderTime", renderTime)
                "VIEW" -> {
                    json.put("timeToVisible", timeToVisible)
                    json.put("scrollDepth", scrollDepth.toDouble())
                    json.put("visibilityRatio", visibilityRatio.toDouble())
                    json.put("viewTime", viewTime)
                }

                "TOTAL_VIEW_TIME" -> json.put("totalViewTime", totalViewTime)
                "TOTAL_PLAYBACK_TIME" -> json.put("totalPlaybackTime", totalPlaybackTime)
                "CLICK" -> {}
                else -> {}
            }
        } catch (ignored: Exception) {
        }
        return json
    }
}