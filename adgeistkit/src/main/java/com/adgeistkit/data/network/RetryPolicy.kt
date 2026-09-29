package com.adgeistkit.data.network

import com.adgeistkit.constants.General
import okhttp3.Response
import java.util.concurrent.TimeUnit

internal object RetryPolicy {

    const val MAX_REATTEMPTS = 5

    val MIN_RETRY_INTERVAL_MS = General.Timing.RETRY_BASE_BACKOFF_MS

    private const val NOT_IMPLEMENTED = 501
    private val RETRYABLE_CLIENT_CODES = setOf(408, 425, 429, 499)

    fun isRetryable(code: Int): Boolean =
        code in RETRYABLE_CLIENT_CODES || (code in 500..599 && code != NOT_IMPLEMENTED)

    fun backoffMillis(round: Int): Long {
        if (round <= 0) return General.Timing.RETRY_BASE_BACKOFF_MS

        var delay = General.Timing.RETRY_BASE_BACKOFF_MS
        repeat(round) {
            delay *= 2
            if (delay >= General.Timing.RETRY_MAX_BACKOFF_MS) return General.Timing.RETRY_MAX_BACKOFF_MS
        }

        return delay
    }

    fun nextDelayMillis(reattempts: Int, retryAfterMillis: Long?): Long =
        maxOf(backoffMillis(reattempts), retryAfterMillis ?: 0L)

    /**
     * Reads either legal form of Retry-After: delta-seconds, or an HTTP-date. Capped so a
     * malformed header cannot freeze the queue for years.
     */
    fun retryAfterMillis(response: Response, nowMillis: Long): Long? {
        val raw = response.header("Retry-After")?.trim() ?: return null

        raw.toLongOrNull()?.let { seconds ->
            return TimeUnit.SECONDS.toMillis(seconds).coerceIn(0L, General.Timing.RETRY_MAX_BACKOFF_MS)
        }

        val date = response.headers.getDate("Retry-After") ?: return null

        return (date.time - nowMillis).coerceIn(0L, General.Timing.RETRY_MAX_BACKOFF_MS)
    }
}
