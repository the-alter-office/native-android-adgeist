package com.adgeistkit.ads

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import org.json.JSONObject
import com.adgeistkit.ads.tracking.AdActivity

/**
 * JS <-> native bridge registered on the ad WebView as the "Android" object, and
 * owner of the ad's tracker. Survives AdView recreation along with it.
 *
 * The `@JavascriptInterface` method names below are a wire contract with the
 * creative JS, kept by name in consumer-rules.pro and asserted by JsBridgeR8Test.
 * Renaming one silently breaks ads in minified host builds.
 */
class JsBridge(
    private var baseAdView: BaseAdView,
    // Unused, retained so the published constructor signature does not change
    @Suppress("UNUSED_PARAMETER") context: Context,
) {

    companion object {
        private const val TAG = "Javascript Bridge"
    }

    private var adActivity: AdActivity? = AdActivity(baseAdView)

    // ---- Host lifecycle (called by BaseAdView) ----

    /** Suspends tracking on window detach; the ad itself stays alive. */
    fun onHostDetached() {
        adActivity?.pause()
    }

    /** Re-registers tracking against the new window on re-attach. */
    fun onHostAttached() {
        adActivity?.resume()
    }

    /** Redirects this bridge and its tracker to the AdView that adopted the ad. */
    fun rebind(newHost: BaseAdView) {
        baseAdView = newHost
        adActivity?.rebind(newHost)
    }

    fun recordClickListener() {
        adActivity?.captureClick()
    }

    fun destroyListeners() {
        adActivity?.destroy()
        adActivity = null
    }

    // ---- Calls from the ad page ----

    @JavascriptInterface
    fun postMessage(json: String) {
        try {
            val obj = JSONObject(json)
            val type = obj.optString("type")
            val msg = obj.optString("message")

            if ("RENDER_STATUS" == type && "Success" == msg) {
                adActivity?.captureImpression()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Invalid JSON: $json")
        }
    }

    @JavascriptInterface
    fun postVideoStatus(json: String) {
        try {
            val obj = JSONObject(json)
            when (obj.optString("type")) {
                "PLAY" -> adActivity?.onVideoPlay()
                "PAUSE" -> adActivity?.onVideoPause()
                "ENDED" -> adActivity?.onVideoEnd()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Invalid JSON in postVideoStatus: $json", e)
        }
    }

    @JavascriptInterface
    fun reportOverflow(contentWidth: Int, contentHeight: Int, viewWidth: Int, viewHeight: Int) {
        Log.e(TAG, "Ad overflow: content ${contentWidth}x${contentHeight} > view ${viewWidth}x${viewHeight}")
        baseAdView.post {
            baseAdView.listener?.onAdFailedToLoad(
                "For companion ads, you should have minimum 320x320 dimensions. But available " +
                    "space is ${viewWidth}x${viewHeight}. So we are collapsing the ad, we won't " +
                    "track impressions, clicks etc for this ad."
            )
            baseAdView.destroyAd()
        }
    }

    @JavascriptInterface
    fun showAd() {
        baseAdView.post {
            baseAdView.webView?.visibility = android.view.View.VISIBLE
        }
    }
}
