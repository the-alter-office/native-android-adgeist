package com.adgeistkit.benchmark

data class FetchTimings(
    val queueWaitMs: Long,
    val deviceIdMs: Long,
    val requestBuildMs: Long,
    val networkRttMs: Long,
    val bodyReadMs: Long,
    val responseParseMs: Long,
) {
    companion object {
        val EMPTY = FetchTimings(0, 0, 0, 0, 0, 0)
    }
}
