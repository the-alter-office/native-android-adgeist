package com.adgeistkit.logging

import android.util.Log
import com.adgeistkit.BuildConfig

object SdkShield {

    private const val TAG = "AdgeistShield"
    private val SDK_PACKAGE = BuildConfig.LIBRARY_PACKAGE_NAME
    private const val MAX_STACK_FRAMES = 5

    inline fun runSafely(tag: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            handleException(tag, t)
        }
    }

    inline fun <T> runSafelyWithReturn(tag: String, fallback: T, block: () -> T): T {
        return try {
            block()
        } catch (t: Throwable) {
            handleException(tag, t)
            fallback
        }
    }

    @PublishedApi
    internal fun handleException(tag: String, t: Throwable) {
        val payload = buildErrorPayload(tag, t)
        Log.e(TAG, payload)

        EventCollector.logError(tag, t)

        if (BuildConfig.DEBUG) {
            throw t
        }
    }

    private fun buildErrorPayload(tag: String, t: Throwable): String {
        val sdkFrames = t.stackTrace
            .filter { it.className.startsWith(SDK_PACKAGE) }
            .take(MAX_STACK_FRAMES)
            .joinToString(" → ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }

        val threadName = Thread.currentThread().name
        val timestamp = System.currentTimeMillis()

        return "[$tag] ${t.javaClass.simpleName}: ${t.message} | thread=$threadName | ts=$timestamp | trace=[$sdkFrames]"
    }
}
