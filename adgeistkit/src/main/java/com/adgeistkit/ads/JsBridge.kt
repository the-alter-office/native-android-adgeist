package com.adgeistkit.ads

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import com.adgeistkit.logging.SdkShield
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
        SdkShield.runSafely("JsBridge.recordClickListener") {
            adActivity?.captureClick()
        }
    }

    fun destroyListeners() {
        adActivity?.destroy()
        adActivity = null
    }

    @JavascriptInterface
    fun postMessage(json: String) {        
        SdkShield.runSafely("JsBridge.postMessage") {
            val obj = JSONObject(json)
            val type = obj.optString("type")
            val msg = obj.optString("message")

            if ("RENDER_STATUS" == type && "Success" == msg) {
                adActivity?.captureImpression()
            }
        }
    }

    @JavascriptInterface
    fun postVideoStatus(json: String) {        
        SdkShield.runSafely("JsBridge.postVideoStatus") {
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
        }
    }

    @JavascriptInterface
    fun reportOverflow(contentWidth: Int, contentHeight: Int, viewWidth: Int, viewHeight: Int) {
        SdkShield.runSafely("JsBridge.reportOverflow") {
            Log.e(TAG, "Ad overflow detected! Content: ${contentWidth}x${contentHeight} > View: ${viewWidth}x${viewHeight}")
            baseAdView.post {
                SdkShield.runSafely("JsBridge.reportOverflow.post") {
                    baseAdView.listener?.onAdFailedToLoad("For companion ads, you should have minimum 320x320 dimensions. But available space is ${viewWidth}x${viewHeight}. So we are collapsing the ad, we won't track impressions, clicks etc for this ad.")
                    baseAdView.destroy()
                    baseAdView.removeFromParent()
                }
            }
        }
    }

    @JavascriptInterface
    fun showAd() {
        SdkShield.runSafely("JsBridge.showAd") {
            baseAdView.post {
                SdkShield.runSafely("JsBridge.showAd.post") {
                    baseAdView.webView?.visibility = android.view.View.VISIBLE
                }
            }
        }
    }

    companion object {
        private const val TAG = "Javascript Bridge"
    }
}