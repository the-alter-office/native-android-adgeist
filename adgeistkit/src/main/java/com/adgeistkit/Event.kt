package com.adgeistkit

import com.google.gson.annotations.SerializedName

data class Event(
    @SerializedName("event_type") val eventType: String,
    @SerializedName("event_properties") val eventProperties: Map<String, Any?>? = null
)