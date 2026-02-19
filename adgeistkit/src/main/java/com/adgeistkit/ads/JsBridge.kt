package com.adgeistkit.ads

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import org.json.JSONObject

class JsBridge(private val baseAdView: BaseAdView, var mContext: Context) {
    private var adActivity: AdActivity? = null

    init {
        initializeAdTracker()
    }

    private fun initializeAdTracker() {
        adActivity = AdActivity(baseAdView)
    }

    fun recordClickListener() {
        adActivity?.captureClick()
    }

    fun destroyListeners() {
        adActivity?.destroy()
        adActivity = null
    }

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
            val msg = obj.optString("message")

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

    companion object {
        private const val TAG = "Javascript Bridge"
    }
}