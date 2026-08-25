package com.adgeistkit.benchmark

import android.os.SystemClock
import android.util.Log

internal class AdRenderBenchmark {

    companion object {
        private const val TAG = "AdBenchmark"

        const val MESSAGE_TYPE = "BENCHMARK"
        private const val MESSAGE_JS_BOOT = "JS_BOOT"
        private const val MESSAGE_JS_DOM = "JS_DOM"
        private const val MESSAGE_JS_INIT = "JS_INIT"
        private const val MESSAGE_JS_RENDER = "JS_RENDER"

        /** Appended to the ad HTML so the WebView reports when its JS runtime is alive. */
        val jsReadyScript: String =
            "<script>Android.postMessage(JSON.stringify(" +
                "{type:'$MESSAGE_TYPE', message:'$MESSAGE_JS_BOOT'}));</script>"

        fun jsPhaseOf(message: String): JsPhase? = when (message) {
            MESSAGE_JS_BOOT -> JsPhase.BOOT
            MESSAGE_JS_DOM -> JsPhase.DOM
            MESSAGE_JS_INIT -> JsPhase.INIT
            MESSAGE_JS_RENDER -> JsPhase.RENDER
            else -> null
        }
    }

    enum class JsPhase { BOOT, DOM, INIT, RENDER }

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
    @Volatile private var jsInit: Long = 0
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
        jsInit = 0
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
            JsPhase.INIT -> jsInit = now
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
        if (jsInit == 0L) jsInit = jsDom
        if (jsRender == 0L) jsRender = jsInit

        val fetchTime = if (fromCache) 0 else fetchEnd - fetchStart
        val prepFrom = if (fromCache) loadStart else fetchEnd
        val prepTime = renderStart - prepFrom
        val webViewAllocTime = webViewAllocEnd - renderStart
        val webViewInitTime = webViewInitEnd - webViewAllocEnd
        val htmlAssetReadTime = htmlReadEnd - webViewInitEnd
        val addViewTime = addViewEnd - htmlReadEnd
        val jsBootTime = jsBoot - addViewEnd
        val jsDomTime = jsDom - jsBoot
        val jsInitDelayTime = jsInit - jsDom
        val jsRenderTime = jsRender - jsInit
        val paintTime = firstFrame - jsRender
        val totalTime = firstFrame - loadStart

        val t = fetchTimings ?: FetchTimings.EMPTY

        Log.i(TAG, """
            🎬 Ad Render Cycle (Unit: $adUnitId, cached: $fromCache):
            - Queue Wait:                ${t.queueWaitMs}ms
            ─ Device ID:                 ${t.deviceIdMs}ms
            ─ Request Build:             ${t.requestBuildMs}ms
            ─ Network RTT:               ${t.networkRttMs}ms
            ─ Body Read:                 ${t.bodyReadMs}ms
            ─ Response Parse:            ${t.responseParseMs}ms
            ─ Total Ad Fetch:            ${fetchTime}ms

            - Payload Prep + Layout:     ${prepTime}ms

            - WebView Alloc:             ${webViewAllocTime}ms
            - WebView Init:              ${webViewInitTime}ms
            - HTML Asset Read:           ${htmlAssetReadTime}ms

            - JS Boot + Lib Eval:        ${jsBootTime}ms
            - DOMContentLoaded:          ${jsDomTime}ms
            - JS Init Delay:             ${jsInitDelayTime}ms
            - JS Render (DOM Build):     ${jsRenderTime}ms
            - Media Decode + Paint:      ${paintTime}ms
            ------------------------------------
            - Total Time to First Frame: ${totalTime}ms
        """.trimIndent())
    }
}
