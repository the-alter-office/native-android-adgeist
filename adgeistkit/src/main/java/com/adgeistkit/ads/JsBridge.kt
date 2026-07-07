package com.adgeistkit.ads

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import org.json.JSONObject

/**
 * JS <-> native bridge registered on the ad WebView as the "Android" object.
 * Survives AdView recreation together with its AdActivity tracker.
 */
class JsBridge(private var baseAdView: BaseAdView, var mContext: Context) {

    companion object {
        private const val TAG = "Javascript Bridge"
    }

    private var adActivity: AdActivity? = null

    init {
        adActivity = AdActivity(baseAdView)
    }

    // ---------------------------------------------------------------------
    // Host lifecycle plumbing (called by BaseAdView)
    // ---------------------------------------------------------------------

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
        mContext = newHost.context
        adActivity?.rebind(newHost)
    }

    fun recordClickListener() {
        adActivity?.captureClick()
    }

    fun destroyListeners() {
        adActivity?.destroy()
        adActivity = null
    }

    // ---------------------------------------------------------------------
    // Calls from the ad page
    // ---------------------------------------------------------------------

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
            val type = obj.optString("type")

            if ("PLAY" == type) {
                adActivity?.onVideoPlay()
            } else if ("PAUSE" == type) {
                adActivity?.onVideoPause()
            } else if ("ENDED" == type) {
                adActivity?.onVideoEnd()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Invalid JSON in postVideoStatus: $json", e)
        }
    }

    @JavascriptInterface
    fun reportOverflow(contentWidth: Int, contentHeight: Int, viewWidth: Int, viewHeight: Int) {
        Log.e(TAG, "Ad overflow detected! Content: ${contentWidth}x${contentHeight} > View: ${viewWidth}x${viewHeight}")
        baseAdView.post {
            baseAdView.listener?.onAdFailedToLoad("For companion ads, you should have minimum 320x320 dimensions. But available space is ${viewWidth}x${viewHeight}. So we are collapsing the ad, we won't track impressions, clicks etc for this ad.")
            baseAdView.destroy()
            baseAdView.removeFromParent()
        }
    }

    @JavascriptInterface
    fun showAd() {
        baseAdView.post {
            baseAdView.webView?.visibility = android.view.View.VISIBLE
        }
    }
}
