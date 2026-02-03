package com.adgeistkit.core

import com.google.gson.annotations.SerializedName

/**
 * Data class representing UTM parameters for tracking marketing campaigns.
 * These parameters can come from install referrers or deeplinks.
 */
data class UtmParameters(
    @SerializedName("utm_source") val source: String? = null,
    @SerializedName("utm_medium") val medium: String? = null,
    @SerializedName("utm_campaign") val campaign: String? = null,
    @SerializedName("utm_term") val term: String? = null,
    @SerializedName("utm_content") val content: String? = null,
    @SerializedName("utm_timestamp") val timestamp: Long? = null,
    @SerializedName("utm_x_data") val x_data: String? = null
) {
    /**
     * Checks if any UTM parameter is set
     */
    fun hasData(): Boolean {
        return source != null || medium != null || campaign != null || term != null || content != null || x_data != null
    }

    /**
     * Converts UTM parameters to a map for analytics
     */
    fun toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        source?.let { map["utm_source"] = it }
        medium?.let { map["utm_medium"] = it }
        campaign?.let { map["utm_campaign"] = it }
        term?.let { map["utm_term"] = it }
        content?.let { map["utm_content"] = it }
        timestamp?.let { map["utm_timestamp"] = it }
        x_data?.let { map["utm_x_data"] = it }
        return map
    }

    companion object {
        /**
         * Creates UtmParameters from a map of query parameters
         */
        fun fromMap(params: Map<String, String?>): UtmParameters {
            return UtmParameters(
                source = params["utm_source"],
                medium = params["utm_medium"],
                campaign = params["utm_campaign"],
                term = params["utm_term"],
                content = params["utm_content"],
                timestamp = System.currentTimeMillis(),
                x_data = params["utm_x_data"]
            )
        }
    }
}
