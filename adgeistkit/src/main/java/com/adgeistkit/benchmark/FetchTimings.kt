package com.adgeistkit.benchmark

public data class FetchTimings(
    val queueWaitMs: Long,
    val deviceIdMs: Long,
    val requestBuildMs: Long,
    val networkRttMs: Long,
    val bodyReadMs: Long,
    val responseParseMs: Long,
) {
    public companion object {
        public val EMPTY: FetchTimings = FetchTimings(0, 0, 0, 0, 0, 0)
    }
}
