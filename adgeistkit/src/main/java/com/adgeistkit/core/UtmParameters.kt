package com.adgeistkit.core

import com.google.gson.annotations.SerializedName

/**
 * Data class representing UTM parameters for tracking marketing campaigns.
 * These parameters can come from install referrers or deeplinks.
 */
data class UtmParameters(
    @SerializedName("utm_source") val source: String? = null,
    @SerializedName("utm_campaign") val campaign: String? = null,
    @SerializedName("utm_data") val data: String? = null,
    @SerializedName("session_id") val sessionId: String? = null
) {
    /**
     * Checks if any UTM parameter is set
     */
    fun hasData(): Boolean {
        return source != null || campaign != null || data != null
    }

    /**
     * Converts UTM parameters to a map for analytics
     */
    fun toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        source?.let { map["utm_source"] = it }
        campaign?.let { map["utm_campaign"] = it }
        data?.let { map["utm_data"] = it }
        sessionId?.let { map["session_id"] = it }
        return map
    }

    companion object {
        /**
         * Creates UtmParameters from a map of query parameters
         */
        fun fromMap(params: Map<String, String?>): UtmParameters {
            return UtmParameters(
                source = params["utm_source"],
                campaign = params["utm_campaign"],
                data = params["utm_data"],
                sessionId = null // Will be set later when saving
            )
        }
    }
}
