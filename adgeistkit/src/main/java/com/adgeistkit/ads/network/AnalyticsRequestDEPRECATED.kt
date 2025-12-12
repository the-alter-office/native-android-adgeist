package com.adgeistkit.ads.network

import org.json.JSONObject


class AnalyticsRequestDEPRECATED private constructor(analyticsRequestDEPRECATED: AnalyticsRequestBuilderDEPRECATED) {
    //Required
    private val campaignID = analyticsRequestDEPRECATED.campaignID
    private val bidID = analyticsRequestDEPRECATED.bidID
    val adUnitID = analyticsRequestDEPRECATED.adUnitID
    val isTestMode = analyticsRequestDEPRECATED.isTestMode

    //Optional
    private val type: String?
    private val renderTime: Float
    private val visibilityRatio: Float
    private val scrollDepth: Float
    private val viewTime: Float
    private val timeToVisible: Float
    private val totalViewTime: Float
    private val totalPlaybackTime: Float

    init {
        this.type = analyticsRequestDEPRECATED.type
        this.renderTime = analyticsRequestDEPRECATED.renderTime
        this.visibilityRatio = analyticsRequestDEPRECATED.visibilityRatio
        this.scrollDepth = analyticsRequestDEPRECATED.scrollDepth
        this.viewTime = analyticsRequestDEPRECATED.viewTime
        this.timeToVisible = analyticsRequestDEPRECATED.timeToVisible
        this.totalViewTime = analyticsRequestDEPRECATED.totalViewTime
        this.totalPlaybackTime = analyticsRequestDEPRECATED.totalPlaybackTime
    }

    class AnalyticsRequestBuilderDEPRECATED(
        internal val campaignID: String, val adUnitID: String, val bidID: String, val isTestMode: Boolean
    ) {
        //Optional
        var type: String? = null
        var renderTime: Float = 0f
        var visibilityRatio: Float = 0f
        var scrollDepth: Float = 0f
        var viewTime: Float = 0f
        var timeToVisible: Float = 0f
        var totalViewTime: Float = 0f
        var totalPlaybackTime: Float = 0f

        fun trackImpression(renderTime: Float): AnalyticsRequestBuilderDEPRECATED {
            this.type = "IMPRESSION"
            this.renderTime = renderTime
            return this
        }

        fun trackViewableImpression(
            timeToVisible: Float,
            scrollDepth: Float,
            visibilityRatio: Float,
            viewTime: Float
        ): AnalyticsRequestBuilderDEPRECATED {
            this.type = "VIEW"
            this.timeToVisible = timeToVisible
            this.scrollDepth = scrollDepth
            this.visibilityRatio = visibilityRatio
            this.viewTime = viewTime
            return this
        }

        fun trackClick(): AnalyticsRequestBuilderDEPRECATED {
            this.type = "CLICK"
            return this
        }

        fun trackTotalViewTime(totalViewTime: Float): AnalyticsRequestBuilderDEPRECATED {
            this.type = "TOTAL_VIEW_TIME"
            this.totalViewTime = totalViewTime
            return this
        }

        fun trackTotalPlaybackTime(totalPlaybackTime: Float): AnalyticsRequestBuilderDEPRECATED {
            this.type = "TOTAL_PLAYBACK_TIME"
            this.totalPlaybackTime = totalPlaybackTime
            return this
        }

        fun build(): AnalyticsRequestDEPRECATED {
            return AnalyticsRequestDEPRECATED(this)
        }
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
        try {
            json.put("winningBidId", bidID)
            json.put("campaignId", campaignID)
            json.put("eventType", type)


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