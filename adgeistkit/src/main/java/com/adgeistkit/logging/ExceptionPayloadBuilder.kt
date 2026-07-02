package com.adgeistkit.logging

import com.adgeistkit.BuildConfig
import java.io.PrintWriter
import java.io.StringWriter

object ExceptionPayloadBuilder {

    private val SDK_PACKAGE = BuildConfig.LIBRARY_PACKAGE_NAME
    private const val MAX_STACK_FRAMES = 5
    private const val MAX_RAW_TRACE_LENGTH = 250 // 0.2KB
    private const val MAX_CAUSE_DEPTH = 5
    private const val DEFAULT_CATEGORY = "MEDIATION"

    // Single source of truth for the errorCode <-> errorCategory taxonomy.
    private val CATEGORY_TO_ERROR_CODES: Map<String, List<String>> = mapOf(
        "INITIALIZING" to listOf("ERR_INIT_FAILED"),
        "AD_LOADING" to listOf("ERR_AD_LOAD", "ERR_FETCH_CREATIVE", "ERR_NETWORK_TIMEOUT", "ERR_NETWORK_ERROR"),
        "AD_RENDERING" to listOf("ERR_RENDER_FAILED", "ERR_WEBVIEW_CRASH"),
        "JS_BRIDGE_ACTIVE" to listOf("ERR_JS_BRIDGE"),
        "NETWORK" to listOf("ERR_SESSION_UPLOAD", "ERR_EVENT_UPLOAD"),
        "MEDIATION" to emptyList() // reserved, no mediation feature yet
    )

    private val ERROR_CODE_TO_CATEGORY: Map<String, String> =
        CATEGORY_TO_ERROR_CODES.flatMap { (category, codes) -> codes.map { it to category } }.toMap()

    fun build(tag: String, t: Throwable): Map<String, Any?> {
        val errorCode = deriveErrorCode(tag, t)
        return mapOf(
            "type" to "NON_FATAL",
            "severity" to deriveSeverity(t),
            "errorCode" to errorCode,
            "errorCategory" to (ERROR_CODE_TO_CATEGORY[errorCode] ?: DEFAULT_CATEGORY),
            "exception" to buildException(t),
            "detectionMethod" to "try_catch"
        )
    }

    private fun buildException(t: Throwable): Map<String, Any?> {
        return mapOf(
            "type" to t.javaClass.simpleName,
            "message" to (t.message ?: ""),
            "stackTrace" to buildRawStackTrace(t),
            "stackFrames" to buildStackFrames(t),
            "threadName" to Thread.currentThread().name,
            "isFatal" to false,
            "cause" to buildCauseChain(t.cause, 0)
        )
    }

    private fun buildRawStackTrace(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        val raw = sw.toString()
        return if (raw.length > MAX_RAW_TRACE_LENGTH) raw.take(MAX_RAW_TRACE_LENGTH) else raw
    }

    private fun buildStackFrames(t: Throwable): List<Map<String, Any>> {
        return t.stackTrace.take(MAX_STACK_FRAMES).map { frame ->
            mapOf(
                "className" to frame.className,
                "methodName" to frame.methodName,
                "fileName" to (frame.fileName ?: "Unknown"),
                "lineNumber" to frame.lineNumber,
                "isSdkFrame" to frame.className.startsWith(SDK_PACKAGE)
            )
        }
    }

    private fun buildCauseChain(cause: Throwable?, depth: Int): Map<String, Any?>? {
        if (cause == null || depth >= MAX_CAUSE_DEPTH) return null

        return mapOf(
            "type" to cause.javaClass.simpleName,
            "message" to (cause.message ?: ""),
            "cause" to buildCauseChain(cause.cause, depth + 1)
        )
    }

    private fun deriveSeverity(t: Throwable): String {
        return when {
            t.javaClass.simpleName.contains("OutOfMemory") -> "CRITICAL"
            t.javaClass.simpleName.contains("RenderProcessGone") -> "CRITICAL"
            t is SecurityException -> "HIGH"
            t is java.net.SocketTimeoutException -> "MEDIUM"
            t is java.io.IOException -> "MEDIUM"
            t is NullPointerException -> "HIGH"
            t is IllegalStateException -> "HIGH"
            else -> "MEDIUM"
        }
    }

    private fun deriveErrorCode(tag: String, t: Throwable): String {
        return when {
            t is java.net.SocketTimeoutException -> "ERR_NETWORK_TIMEOUT"
            t is java.io.IOException -> "ERR_NETWORK_ERROR"
            t.javaClass.simpleName.contains("RenderProcessGone") -> "ERR_WEBVIEW_CRASH"
            tag.contains("JsBridge") -> "ERR_JS_BRIDGE"
            tag.contains("BaseAdView.loadAd") -> "ERR_AD_LOAD"
            tag.contains("renderAd") || tag.contains("onReceivedError") || tag.contains("shouldOverrideUrlLoading") -> "ERR_RENDER_FAILED"
            tag.contains("AdgeistCore.initialize") -> "ERR_INIT_FAILED"
            tag.contains("FetchCreative") -> "ERR_FETCH_CREATIVE"
            tag.contains("SessionUploadWorker") -> "ERR_SESSION_UPLOAD"
            tag.contains("EventUploadWorker") -> "ERR_EVENT_UPLOAD"
            else -> "ERR_UNKNOWN"
        }
    }
}
