package com.adgeistkit.data.local

internal data class QueuedAnalyticsRequest(
    val id: Long,
    val url: String,
    val body: String,
    val reattempts: Int,
)
