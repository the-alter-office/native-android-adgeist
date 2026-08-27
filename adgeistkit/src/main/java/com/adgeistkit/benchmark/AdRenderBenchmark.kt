package com.adgeistkit.benchmark

import android.os.SystemClock
import android.util.Log
import com.adgeistkit.AdgeistCore
import com.adgeistkit.data.network.PostHogClient

internal class AdRenderBenchmark {

    companion object {
        private const val TAG = "AdBenchmark"
        private const val EVENT_NAME = "ad_render_benchmark"

        const val MESSAGE_TYPE = "BENCHMARK"
        private const val MESSAGE_JS_BOOT = "JS_BOOT"
        private const val MESSAGE_JS_DOM = "JS_DOM"
        private const val MESSAGE_JS_RENDER = "JS_RENDER"

        fun jsPhaseOf(message: String): JsPhase? = when (message) {
            MESSAGE_JS_BOOT -> JsPhase.BOOT
            MESSAGE_JS_DOM -> JsPhase.DOM
            MESSAGE_JS_RENDER -> JsPhase.RENDER
            else -> null
        }
    }

    enum class JsPhase { BOOT, DOM, RENDER }

    private var loadStart: Long = 0
    private var fetchStart: Long = 0
    @Volatile private var fetchEnd: Long = 0
    @Volatile private var fetchTimings: FetchTimings? = null
    private var renderStart: Long = 0
    private var webViewAllocEnd: Long = 0
    private var webViewInitEnd: Long = 0
    private var htmlReadEnd: Long = 0
    private var addViewEnd: Long = 0
    @Volatile private var jsBoot: Long = 0
    @Volatile private var jsDom: Long = 0
    @Volatile private var jsRender: Long = 0
    @Volatile private var firstFrame: Long = 0
    @Volatile private var firstFrameReported: Boolean = false
    private var fromCache: Boolean = false

    fun onLoadStart() {
        loadStart = SystemClock.elapsedRealtime()
        fetchStart = 0
        fetchEnd = 0
        fetchTimings = null
        renderStart = 0
        webViewAllocEnd = 0
        webViewInitEnd = 0
        htmlReadEnd = 0
        addViewEnd = 0
        jsBoot = 0
        jsDom = 0
        jsRender = 0
        firstFrame = 0
        firstFrameReported = false
        fromCache = false
    }

    fun onCacheHit() { fromCache = true }

    fun onFetchStart() { fetchStart = SystemClock.elapsedRealtime() }

    fun onFetchEnd(timings: FetchTimings?) {
        fetchEnd = SystemClock.elapsedRealtime()
        fetchTimings = timings
    }

    fun onRenderStart() { renderStart = SystemClock.elapsedRealtime() }

    fun onWebViewReady(allocEndAt: Long) {
        webViewAllocEnd = allocEndAt
        webViewInitEnd = SystemClock.elapsedRealtime()
    }

    fun onHtmlRead() { htmlReadEnd = SystemClock.elapsedRealtime() }

    fun onViewAdded() { addViewEnd = SystemClock.elapsedRealtime() }

    fun onJsPhase(phase: JsPhase) {
        val now = SystemClock.elapsedRealtime()
        when (phase) {
            JsPhase.BOOT -> jsBoot = now
            JsPhase.DOM -> jsDom = now
            JsPhase.RENDER -> jsRender = now
        }
    }

    @Synchronized
    fun markFirstFrame(): Boolean {
        if (firstFrameReported) return false
        firstFrameReported = true
        firstFrame = SystemClock.elapsedRealtime()
        return true
    }

    fun log(adUnitId: String) {
        if (jsBoot == 0L) jsBoot = addViewEnd
        if (jsDom == 0L) jsDom = jsBoot
        if (jsRender == 0L) jsRender = jsDom

        val fetchTime = if (fromCache) 0 else fetchEnd - fetchStart
        val prepFrom = if (fromCache) loadStart else fetchEnd
        val prepTime = renderStart - prepFrom
        val webViewAllocTime = webViewAllocEnd - renderStart
        val webViewInitTime = webViewInitEnd - webViewAllocEnd
        val htmlAssetReadTime = htmlReadEnd - webViewInitEnd
        val jsBootTime = jsBoot - addViewEnd
        val jsDomTime = jsDom - jsBoot
        val jsRenderTime = jsRender - jsDom
        val paintTime = firstFrame - jsRender
        val totalTime = firstFrame - loadStart

        val t = fetchTimings ?: FetchTimings.EMPTY

        Log.i(TAG, """
            🎬 Ad Render Cycle (Unit: $adUnitId, cached: $fromCache):
            - Queue Wait:                       ${t.queueWaitMs}ms
            ─ Device ID:                        ${t.deviceIdMs}ms
            ─ Request Build:                    ${t.requestBuildMs}ms
            ─ Network RTT:                      ${t.networkRttMs}ms
            ─ Body Read:                        ${t.bodyReadMs}ms
            ─ Response Parse:                   ${t.responseParseMs}ms
            ─ Total Ad Fetch:                   ${fetchTime}ms

            - Payload Prep + Layout:            ${prepTime}ms

            - WebView Alloc:                    ${webViewAllocTime}ms
            - WebView Init:                     ${webViewInitTime}ms
            - HTML Asset Read:                  ${htmlAssetReadTime}ms

            - JS Boot + Lib Eval:               ${jsBootTime}ms
            - DOMContentLoaded:                 ${jsDomTime}ms
            - Ad DOM Build from Adcard class:   ${jsRenderTime}ms
            - Render First Frame:               ${paintTime}ms
            ------------------------------------
            - Total Time to First Frame:        ${totalTime}ms
        """.trimIndent())

        if (!AdgeistCore.isInitialized() || AdgeistCore.getInstance().isHostAppDebuggable) return

        PostHogClient.capture(
            EVENT_NAME,
            mapOf(
                "ad_unit_id" to adUnitId,
                "from_cache" to fromCache,
                "queue_wait_ms" to t.queueWaitMs,
                "device_id_ms" to t.deviceIdMs,
                "request_build_ms" to t.requestBuildMs,
                "network_rtt_ms" to t.networkRttMs,
                "body_read_ms" to t.bodyReadMs,
                "response_parse_ms" to t.responseParseMs,
                "ad_fetch_ms" to fetchTime,
                "payload_prep_ms" to prepTime,
                "webview_alloc_ms" to webViewAllocTime,
                "webview_init_ms" to webViewInitTime,
                "html_asset_read_ms" to htmlAssetReadTime,
                "js_boot_ms" to jsBootTime,
                "dom_content_loaded_ms" to jsDomTime,
                "ad_dom_build_ms" to jsRenderTime,
                "first_frame_paint_ms" to paintTime,
                "total_time_to_first_frame_ms" to totalTime,
            )
        )
    }
}
