package com.adgeistkit.ads

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import org.json.JSONObject
import com.adgeistkit.ads.tracking.AdActivity
import com.adgeistkit.benchmark.AdRenderBenchmark
import com.adgeistkit.constants.General
import com.adgeistkit.constants.Logs
import com.adgeistkit.constants.Messages

/**
 * JS <-> native bridge registered on the ad WebView as the "Android" object, and
 * owner of the ad's tracker.
 */
internal class JsBridge(
    private var baseAdView: BaseAdView,
    @Suppress("UNUSED_PARAMETER") context: Context,
) {

    public companion object {
        private const val TAG = "Javascript Bridge"
    }

    private var adActivity: AdActivity? = AdActivity(baseAdView)
    private val mainHandler = Handler(Looper.getMainLooper())

    public val deepLinkUrl: String?
        get() = baseAdView.deepLinkUrl

    // ---- Host lifecycle (called by BaseAdView) ----

    public fun onHostDetached() {
        adActivity?.pause()
    }

    public fun onHostAttached() {
        adActivity?.resume()
    }

    public fun rebind(newHost: BaseAdView) {
        baseAdView = newHost
        adActivity?.rebind(newHost)
    }

    public fun recordClickListener() {
        adActivity?.captureClick()
    }

    public fun onShellPageFinished() {
        baseAdView.onShellPageFinished()
    }

    public fun onRenderProcessGone(didCrash: Boolean) {
        baseAdView.onRenderProcessGone(didCrash)
    }

    public fun destroyListeners() {
        adActivity?.destroy()
        adActivity = null
    }

    // ---- Calls from the ad page ----

    @JavascriptInterface
    public fun postMessage(json: String) {
        try {
            val obj = JSONObject(json)
            val type = obj.optString("type")
            val msg = obj.optString("message")

            if (AdRenderBenchmark.MESSAGE_TYPE == type) {
                AdRenderBenchmark.jsPhaseOf(msg)?.let { baseAdView.markJsPhase(it) }
            }

            if (General.Bridge.SHELL_READY == type) {
                mainHandler.post { baseAdView.onShellReady() }
            }

            if (General.Bridge.RENDER_STATUS == type && General.Bridge.RENDER_SUCCESS == msg) {
                adActivity?.captureImpression()
                baseAdView.reportFirstFrame()
            }
        } catch (e: Exception) {
            Log.e(TAG, Logs.Error.invalidJson(json))
        }
    }

    @JavascriptInterface
    public fun postVideoStatus(json: String) {
        try {
            val obj = JSONObject(json)
            when (obj.optString("type")) {
                General.Bridge.VIDEO_PLAY -> adActivity?.onVideoPlay()
                General.Bridge.VIDEO_PAUSE -> adActivity?.onVideoPause()
                General.Bridge.VIDEO_ENDED -> adActivity?.onVideoEnd()
            }
        } catch (e: Exception) {
            Log.e(TAG, Logs.Error.invalidVideoStatusJson(json), e)
        }
    }

    @JavascriptInterface
    public fun reportOverflow(contentWidth: Int, contentHeight: Int, viewWidth: Int, viewHeight: Int) {
        Log.e(TAG, Logs.Error.adOverflow(contentWidth, contentHeight, viewWidth, viewHeight))
        baseAdView.post {
            baseAdView.listener?.onAdEvent(
                AdgeistEvent(
                    AdgeistEventCode.AW8,
                    AdgeistEventData(Messages.Listener.companionMinSizeNotMet(viewWidth, viewHeight))
                )
            )
            baseAdView.destroyAd()
        }
    }

    @JavascriptInterface
    public fun showAd() {
        baseAdView.post {
            baseAdView.webView?.visibility = android.view.View.VISIBLE
        }
    }
}
