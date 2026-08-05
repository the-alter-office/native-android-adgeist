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
    private val screenLabel: String?
    private val screenToken: String?
    private val slotLabel: String?

    init {
        this.type = analyticsRequest.type
        this.visibilityRatio = analyticsRequest.visibilityRatio
        this.scrollDepth = analyticsRequest.scrollDepth
        this.viewTime = analyticsRequest.viewTime
        this.timeToVisible = analyticsRequest.timeToVisible
        this.screenLabel = analyticsRequest.screenLabel
        this.screenToken = analyticsRequest.screenToken
        this.slotLabel = analyticsRequest.slotLabel
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

        // Where the ad was placed. Sent with every event so duplicate-placement
        // detection can be done server-side, where it cannot be bypassed and
        // where a unit's declared placement is actually known.
        var screenLabel: String? = null
        var screenToken: String? = null
        var slotLabel: String? = null

        /** Identifies the screen and slot this ad rendered in. */
        fun withPlacement(
            screenLabel: String?,
            screenToken: String?,
            slotLabel: String?
        ): AnalyticsRequestBuilder {
            this.screenLabel = screenLabel
            this.screenToken = screenToken
            this.slotLabel = slotLabel
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


        fun build(): AnalyticsRequest {
            return AnalyticsRequest(this)
        }
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
        try {
            json.put("metaData", metaData)
            json.put("type", type)

            screenLabel?.let { json.put("screenLabel", it) }
            screenToken?.let { json.put("screenToken", it) }
            slotLabel?.let { json.put("slotLabel", it) }

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
