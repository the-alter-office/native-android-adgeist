package com.adgeistkit.benchmark

import android.os.SystemClock
import com.adgeistkit.AdgeistCore
import com.adgeistkit.utilities.logI
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

    private var loadAdAt: Long = 0
    private var loadDispatchedAt: Long = 0
    private var preloadStartAt: Long = 0
    private var webViewAllocatedAt: Long = 0
    private var webViewConfiguredAt: Long = 0
    private var shellAssetsReadAt: Long = 0
    private var shellHandedToWebViewAt: Long = 0
    private var fetchStartAt: Long = 0
    @Volatile private var jsBootAt: Long = 0
    @Volatile private var jsDomAt: Long = 0
    @Volatile private var preloadEndAt: Long = 0
    @Volatile private var fetchEndAt: Long = 0
    @Volatile private var fetchTimings: FetchTimings? = null
    @Volatile private var payloadReadyAt: Long = 0
    private var renderStartAt: Long = 0
    @Volatile private var jsRenderAt: Long = 0
    @Volatile private var firstFrameAt: Long = 0
    @Volatile private var firstFrameReported: Boolean = false
    private var fromCache: Boolean = false

    fun onLoadStart() {
        loadAdAt = SystemClock.elapsedRealtime()
        loadDispatchedAt = 0
        preloadStartAt = 0
        webViewAllocatedAt = 0
        webViewConfiguredAt = 0
        shellAssetsReadAt = 0
        shellHandedToWebViewAt = 0
        fetchStartAt = 0
        jsBootAt = 0
        jsDomAt = 0
        preloadEndAt = 0
        fetchEndAt = 0
        fetchTimings = null
        payloadReadyAt = 0
        renderStartAt = 0
        jsRenderAt = 0
        firstFrameAt = 0
        firstFrameReported = false
        fromCache = false
    }

    fun onCacheHit() { fromCache = true }

    fun onLoadDispatched() { loadDispatchedAt = SystemClock.elapsedRealtime() }

    fun onPreloadStart() { preloadStartAt = SystemClock.elapsedRealtime() }

    fun onWebViewCreated(allocEndAt: Long) {
        webViewAllocatedAt = allocEndAt
        webViewConfiguredAt = SystemClock.elapsedRealtime()
    }

    fun onShellAssetsRead() { shellAssetsReadAt = SystemClock.elapsedRealtime() }

    fun onShellHandedToWebView() { shellHandedToWebViewAt = SystemClock.elapsedRealtime() }

    fun onPreloadEnd() { preloadEndAt = SystemClock.elapsedRealtime() }

    fun onFetchStart() { fetchStartAt = SystemClock.elapsedRealtime() }

    fun onFetchEnd(timings: FetchTimings?) {
        fetchEndAt = SystemClock.elapsedRealtime()
        fetchTimings = timings
    }

    fun onPayloadReady() { payloadReadyAt = SystemClock.elapsedRealtime() }

    fun onRenderStart() { renderStartAt = SystemClock.elapsedRealtime() }

    fun onJsPhase(phase: JsPhase) {
        val now = SystemClock.elapsedRealtime()
        when (phase) {
            JsPhase.BOOT -> jsBootAt = now
            JsPhase.DOM -> jsDomAt = now
            JsPhase.RENDER -> jsRenderAt = now
        }
    }

    @Synchronized
    fun markFirstFrame(): Boolean {
        if (firstFrameReported) return false
        firstFrameReported = true
        firstFrameAt = SystemClock.elapsedRealtime()
        return true
    }

    private fun at(milestone: Long): String =
        if (milestone == 0L) "-" else "${milestone - loadAdAt}ms"

    fun log(adUnitId: String) {
        if (loadDispatchedAt == 0L) loadDispatchedAt = loadAdAt
        if (preloadStartAt == 0L) preloadStartAt = loadDispatchedAt
        if (shellHandedToWebViewAt == 0L) shellHandedToWebViewAt = preloadStartAt
        if (jsBootAt == 0L) jsBootAt = shellHandedToWebViewAt
        if (jsDomAt == 0L) jsDomAt = jsBootAt
        if (preloadEndAt == 0L) preloadEndAt = jsDomAt
        if (payloadReadyAt == 0L) payloadReadyAt = renderStartAt
        if (jsRenderAt == 0L) jsRenderAt = renderStartAt

        val loadDispatchMs = loadDispatchedAt - loadAdAt
        val blockingBeforeFetchMs =
            if (fromCache || fetchStartAt == 0L) 0 else fetchStartAt - loadAdAt

        val webViewAllocMs = webViewAllocatedAt - preloadStartAt
        val webViewConfigureMs = webViewConfiguredAt - webViewAllocatedAt
        val shellAssetReadMs = shellAssetsReadAt - webViewConfiguredAt
        val shellHandoffMs = shellHandedToWebViewAt - shellAssetsReadAt
        val jsBootMs = jsBootAt - shellHandedToWebViewAt
        val jsDomMs = jsDomAt - jsBootAt
        val preloadConfirmMs = preloadEndAt - jsDomAt
        val preloadTotalMs = preloadEndAt - preloadStartAt

        val adFetchMs = if (fromCache) 0 else fetchEndAt - fetchStartAt
        val payloadPrepFrom = if (fromCache) loadAdAt else fetchEndAt
        val payloadPrepMs = payloadReadyAt - payloadPrepFrom
        val waitForPreloadMs = renderStartAt - payloadReadyAt
        val jsRenderMs = jsRenderAt - renderStartAt
        val mediaPaintMs = firstFrameAt - jsRenderAt
        val totalMs = firstFrameAt - loadAdAt

        val t = fetchTimings ?: FetchTimings.EMPTY

        logI(TAG) { """
            🎬 Ad Render Cycle (Unit: $adUnitId, cached: $fromCache):

            ⏱ Timestamps (ms after loadAd, '-' = never happened)
              loadAd called                     @ 0ms
              load dispatched                   @ ${at(loadDispatchedAt)}
              fetch started                     @ ${at(fetchStartAt)}
              preload started                   @ ${at(preloadStartAt)}
              webview allocated                 @ ${at(webViewAllocatedAt)}
              webview configured                @ ${at(webViewConfiguredAt)}
              shell assets read                 @ ${at(shellAssetsReadAt)}
              shell handed to webview           @ ${at(shellHandedToWebViewAt)}
              js booted                         @ ${at(jsBootAt)}
              js dom ready                      @ ${at(jsDomAt)}
              preload ended (js confirmed)      @ ${at(preloadEndAt)}
              fetch ended                       @ ${at(fetchEndAt)}
              payload ready                     @ ${at(payloadReadyAt)}
              render started (initAd called)    @ ${at(renderStartAt)}
              js rendered (html appended)       @ ${at(jsRenderAt)}
              first frame painted               @ ${at(firstFrameAt)}

            ⏳ Time taken

            [before anything starts]
            - Load Dispatch:                    ${loadDispatchMs}ms
            - Blocking Before Fetch:            ${blockingBeforeFetchMs}ms

            [ad fetch]
            ─ Queue Wait:                       ${t.queueWaitMs}ms
            ─ Device ID:                        ${t.deviceIdMs}ms
            ─ Request Build:                    ${t.requestBuildMs}ms
            ─ Network RTT:                      ${t.networkRttMs}ms
            ─ Body Read:                        ${t.bodyReadMs}ms
            ─ Response Parse:                   ${t.responseParseMs}ms
            ─ Total Ad Fetch:                   ${adFetchMs}ms

            [preload - overlaps the fetch, see timestamps]
            - WebView Alloc:                    ${webViewAllocMs}ms
            - WebView Configure:                ${webViewConfigureMs}ms
            - Shell Asset Read:                 ${shellAssetReadMs}ms
            - Shell Handoff:                    ${shellHandoffMs}ms
            - JS Boot + Lib Eval:               ${jsBootMs}ms
            - DOMContentLoaded:                 ${jsDomMs}ms
            - Ready Confirmation:               ${preloadConfirmMs}ms
            - Preload Total:                    ${preloadTotalMs}ms

            [render]
            - Payload Prep + Layout:            ${payloadPrepMs}ms
            - Wait For Preload:                 ${waitForPreloadMs}ms
            - JS Render (DOM build):            ${jsRenderMs}ms
            - Media Load + Paint:               ${mediaPaintMs}ms
            ------------------------------------
            - Total Time To First Frame:        ${totalMs}ms
        """.trimIndent() }

        val core = AdgeistCore.getInstance() ?: return
//        if (core.isHostAppDebuggable) return

        PostHogClient.capture(
            EVENT_NAME,
            mapOf(
                "adSpaceId" to adUnitId,
                "platform" to "android", 
                "from_cache" to fromCache,

                "load_dispatched_at_ms" to loadDispatchedAt - loadAdAt,
                "fetch_started_at_ms" to fetchStartAt - loadAdAt,
                "preload_started_at_ms" to preloadStartAt - loadAdAt,
                "shell_handed_to_webview_at_ms" to shellHandedToWebViewAt - loadAdAt,
                "js_booted_at_ms" to jsBootAt - loadAdAt,
                "js_dom_ready_at_ms" to jsDomAt - loadAdAt,
                "preload_ended_at_ms" to preloadEndAt - loadAdAt,
                "fetch_ended_at_ms" to fetchEndAt - loadAdAt,
                "payload_ready_at_ms" to payloadReadyAt - loadAdAt,
                "render_started_at_ms" to renderStartAt - loadAdAt,
                "js_rendered_at_ms" to jsRenderAt - loadAdAt,
                "first_frame_at_ms" to firstFrameAt - loadAdAt,

                "load_dispatch_ms" to loadDispatchMs,
                "blocking_before_fetch_ms" to blockingBeforeFetchMs,
                "queue_wait_ms" to t.queueWaitMs,
                "device_id_ms" to t.deviceIdMs,
                "request_build_ms" to t.requestBuildMs,
                "network_rtt_ms" to t.networkRttMs,
                "body_read_ms" to t.bodyReadMs,
                "response_parse_ms" to t.responseParseMs,
                "ad_fetch_ms" to adFetchMs,
                "webview_alloc_ms" to webViewAllocMs,
                "webview_configure_ms" to webViewConfigureMs,
                "shell_asset_read_ms" to shellAssetReadMs,
                "shell_handoff_ms" to shellHandoffMs,
                "js_boot_ms" to jsBootMs,
                "js_dom_ms" to jsDomMs,
                "preload_confirm_ms" to preloadConfirmMs,
                "preload_total_ms" to preloadTotalMs,
                "payload_prep_ms" to payloadPrepMs,
                "wait_for_preload_ms" to waitForPreloadMs,
                "js_render_ms" to jsRenderMs,
                "media_load_paint_ms" to mediaPaintMs,
                "total_time_to_first_frame_ms" to totalMs,
            )
        )
    }
}
