package com.adgeistkit.logging

import android.util.Log
import com.adgeistkit.BuildConfig

object SdkShield {
    private const val TAG = "AdgeistShield"
    private const val MAX_STACK_FRAMES = 5

    inline fun runSafely(tag: String, httpLog: HttpRequestLog? = null, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            handleException(tag, t, httpLog?.snapshot())
        }
    }

    inline fun <T> runSafelyWithReturn(tag: String, fallback: T, httpLog: HttpRequestLog? = null, block: () -> T): T {
        return try {
            block()
        } catch (t: Throwable) {
            handleException(tag, t, httpLog?.snapshot())
            fallback
        }
    }

    @PublishedApi
    internal fun handleException(tag: String, t: Throwable, httpRequests: List<Map<String, Any?>>? = null) {
        val payload = buildErrorPayload(tag, t)
        Log.e(TAG, payload)

        EventCollector.logError(tag, t, httpRequests)
    }

    private fun buildErrorPayload(tag: String, t: Throwable): String {
        val sdkFrames = t.stackTrace.take(MAX_STACK_FRAMES)

        val threadName = Thread.currentThread().name
        val timestamp = System.currentTimeMillis()

        return "[$tag] ${t.javaClass.simpleName}: ${t.message} | thread=$threadName | ts=$timestamp | trace=[$sdkFrames]"
    }
}
