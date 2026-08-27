package com.adgeistkit.data.network

import java.util.concurrent.TimeUnit
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPolicyTest {

    private val baseMs = TimeUnit.MINUTES.toMillis(1)
    private val maxMs = TimeUnit.MINUTES.toMillis(15)

    private fun responseWith(retryAfter: String?): Response {
        val builder = Response.Builder()
            .request(Request.Builder().url("https://example.test/impression").build())
            .protocol(Protocol.HTTP_1_1)
            .code(503)
            .message("Service Unavailable")

        retryAfter?.let { builder.header("Retry-After", it) }

        return builder.build()
    }

    // ---- isRetryable ----

    @Test
    fun transientClientCodesAreRetryable() {
        assertTrue(RetryPolicy.isRetryable(408))
        assertTrue(RetryPolicy.isRetryable(425))
        assertTrue(RetryPolicy.isRetryable(429))
        assertTrue(RetryPolicy.isRetryable(499))
    }

    @Test
    fun serverCodesAreRetryableExceptNotImplemented() {
        assertTrue(RetryPolicy.isRetryable(500))
        assertTrue(RetryPolicy.isRetryable(502))
        assertTrue(RetryPolicy.isRetryable(503))
        assertTrue(RetryPolicy.isRetryable(504))
        assertTrue(RetryPolicy.isRetryable(599))

        assertFalse("a server that does not implement the endpoint never will", RetryPolicy.isRetryable(501))
    }

    @Test
    fun permanentClientCodesAreNotRetryable() {
        assertFalse(RetryPolicy.isRetryable(400))
        assertFalse(RetryPolicy.isRetryable(401))
        assertFalse(RetryPolicy.isRetryable(403))
        assertFalse(RetryPolicy.isRetryable(404))
        assertFalse(RetryPolicy.isRetryable(422))
    }

    @Test
    fun successIsNotRetryable() {
        assertFalse(RetryPolicy.isRetryable(200))
        assertFalse(RetryPolicy.isRetryable(204))
    }

    // ---- backoffMillis ----

    @Test
    fun backoffDoublesEachRound() {
        assertEquals(baseMs, RetryPolicy.backoffMillis(0))
        assertEquals(baseMs * 2, RetryPolicy.backoffMillis(1))
        assertEquals(baseMs * 4, RetryPolicy.backoffMillis(2))
        assertEquals(baseMs * 8, RetryPolicy.backoffMillis(3))
    }

    @Test
    fun backoffIsCappedAndNeverOverflows() {
        // Round 4 would be 16m, so the last re-attempt lands exactly on the cap.
        assertEquals(maxMs, RetryPolicy.backoffMillis(4))
        assertEquals(maxMs, RetryPolicy.backoffMillis(5))
        assertEquals(maxMs, RetryPolicy.backoffMillis(50))
        assertEquals(maxMs, RetryPolicy.backoffMillis(Int.MAX_VALUE))
    }

    @Test
    fun backoffTreatsNegativeRoundsAsTheBase() {
        assertEquals(baseMs, RetryPolicy.backoffMillis(-1))
    }

    // ---- nextDelayMillis ----

    @Test
    fun nextDelayFallsBackToTheCurveWithoutARetryAfter() {
        assertEquals(baseMs, RetryPolicy.nextDelayMillis(0, null))
        assertEquals(baseMs * 2, RetryPolicy.nextDelayMillis(1, null))
    }

    @Test
    fun nextDelayTakesWhicheverIsLonger() {
        val tenMinutes = TimeUnit.MINUTES.toMillis(10)

        // The server asked for longer than round 0's 1m, so its wait wins.
        assertEquals(tenMinutes, RetryPolicy.nextDelayMillis(0, tenMinutes))
        // Round 3 is 8m, but the server only asked for 1m, so the curve wins.
        assertEquals(baseMs * 8, RetryPolicy.nextDelayMillis(3, baseMs))
    }

    @Test
    fun minRetryIntervalMatchesTheBase() {
        assertEquals(baseMs, RetryPolicy.MIN_RETRY_INTERVAL_MS)
    }

    // ---- retryAfterMillis ----

    @Test
    fun retryAfterReadsDeltaSeconds() {
        val result = RetryPolicy.retryAfterMillis(responseWith("120"), nowMillis = 0L)

        assertEquals(TimeUnit.SECONDS.toMillis(120), result)
    }

    @Test
    fun retryAfterReadsAnHttpDate() {
        val now = 1_000_000_000_000L
        // 60s past the epoch reference the header encodes, expressed as an HTTP-date.
        val response = responseWith("Thu, 01 Jan 1970 00:01:00 GMT")

        val result = RetryPolicy.retryAfterMillis(response, nowMillis = 0L)

        assertEquals(TimeUnit.SECONDS.toMillis(60), result)

        // Well in the past relative to `now`, so it clamps to zero rather than going negative.
        assertEquals(0L, RetryPolicy.retryAfterMillis(response, nowMillis = now))
    }

    @Test
    fun retryAfterIsNullWhenAbsentOrUnparseable() {
        assertNull(RetryPolicy.retryAfterMillis(responseWith(null), nowMillis = 0L))
        assertNull(RetryPolicy.retryAfterMillis(responseWith("soon"), nowMillis = 0L))
    }

    @Test
    fun retryAfterClampsNegativesToZero() {
        assertEquals(0L, RetryPolicy.retryAfterMillis(responseWith("-30"), nowMillis = 0L))
    }

    @Test
    fun retryAfterCapsAnAbsurdValue() {
        // Ten years, which would otherwise freeze the queue for the life of the install.
        assertEquals(maxMs, RetryPolicy.retryAfterMillis(responseWith("315360000"), nowMillis = 0L))
        assertEquals(maxMs, RetryPolicy.retryAfterMillis(responseWith("${Long.MAX_VALUE}"), nowMillis = 0L))
    }
}
