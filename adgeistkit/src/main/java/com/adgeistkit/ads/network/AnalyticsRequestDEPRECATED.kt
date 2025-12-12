package com.adgeistkit.ads.network

import org.json.JSONObject


class AnalyticsRequestDEPRECATED private constructor(analyticsRequestDEPRECATED: AnalyticsRequestBuilderDEPRECATED) {
    //Required
    val adUnitID = analyticsRequestDEPRECATED.adUnitID
    val isTestMode = analyticsRequestDEPRECATED.isTestMode

    val buyType: String?
    private val campaignID: String?
    private val bidID:String?
    private val metaData: String?

    //Optional
    private val eventType: String?
    private val renderTime: Float
    private val visibilityRatio: Float
    private val scrollDepth: Float
    private val viewTime: Float
    private val timeToVisible: Float
    private val totalViewTime: Float
    private val totalPlaybackTime: Float

    init {
        this.eventType = analyticsRequestDEPRECATED.eventType
        this.renderTime = analyticsRequestDEPRECATED.renderTime
        this.visibilityRatio = analyticsRequestDEPRECATED.visibilityRatio
        this.scrollDepth = analyticsRequestDEPRECATED.scrollDepth
        this.viewTime = analyticsRequestDEPRECATED.viewTime
        this.timeToVisible = analyticsRequestDEPRECATED.timeToVisible
        this.totalViewTime = analyticsRequestDEPRECATED.totalViewTime
        this.totalPlaybackTime = analyticsRequestDEPRECATED.totalPlaybackTime

        this.buyType = analyticsRequestDEPRECATED.buyType
        this.campaignID = analyticsRequestDEPRECATED.campaignID
        this.bidID = analyticsRequestDEPRECATED.bidID
        this.metaData = analyticsRequestDEPRECATED.metaData
    }

    class AnalyticsRequestBuilderDEPRECATED(
        internal val adUnitID: String, val isTestMode: Boolean
    ) {
        var buyType: String? = null
        var campaignID: String? = null
        var bidID: String? = null
        var metaData: String? = null

        //Optional
        var eventType: String? = null
        var renderTime: Float = 0f
        var visibilityRatio: Float = 0f
        var scrollDepth: Float = 0f
        var viewTime: Float = 0f
        var timeToVisible: Float = 0f
        var totalViewTime: Float = 0f
        var totalPlaybackTime: Float = 0f

        fun buildCPMRequest(campaignID: String, bidID: String): AnalyticsRequestBuilderDEPRECATED{
            this.buyType = "CPM"
            this.campaignID = campaignID
            this.bidID = bidID
            return this
        }

        fun buildFIXEDRequest(metaData: String): AnalyticsRequestBuilderDEPRECATED{
            this.buyType = "FIXED"
            this.metaData = metaData
            return this
        }

        fun trackImpression(renderTime: Float): AnalyticsRequestBuilderDEPRECATED {
            this.eventType = "IMPRESSION"
            this.renderTime = renderTime
            return this
        }

        fun trackViewableImpression(
            timeToVisible: Float,
            scrollDepth: Float,
            visibilityRatio: Float,
            viewTime: Float
        ): AnalyticsRequestBuilderDEPRECATED {
            this.eventType = "VIEW"
            this.timeToVisible = timeToVisible
            this.scrollDepth = scrollDepth
            this.visibilityRatio = visibilityRatio
            this.viewTime = viewTime
            return this
        }

        fun trackClick(): AnalyticsRequestBuilderDEPRECATED {
            this.eventType = "CLICK"
            return this
        }

        fun trackTotalViewTime(totalViewTime: Float): AnalyticsRequestBuilderDEPRECATED {
            this.eventType = "TOTAL_VIEW_TIME"
            this.totalViewTime = totalViewTime
            return this
        }

        fun trackTotalPlaybackTime(totalPlaybackTime: Float): AnalyticsRequestBuilderDEPRECATED {
            this.eventType = "TOTAL_PLAYBACK_TIME"
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
            when (buyType) {
                "FIXED" -> {
                    json.put("metaData", metaData)
                    json.put("isTest", isTestMode)
                    json.put("type", eventType)
                }
                else -> {
                    json.put("winningBidId", bidID)
                    json.put("campaignId", campaignID)
                    json.put("eventType", eventType)
                }
            }

            when (eventType) {
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