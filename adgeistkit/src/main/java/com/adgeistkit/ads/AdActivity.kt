package com.adgeistkit.ads

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver.OnScrollChangedListener
import android.view.ViewTreeObserver.OnWindowFocusChangeListener
import android.webkit.WebView
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import com.adgeistkit.AdgeistCore.Companion.getInstance
import com.adgeistkit.ads.network.AnalyticsRequest

class AdActivity(private val baseAdView: BaseAdView) {
    private val postCreativeAnalytics = getInstance().postCreativeAnalytics()

    private val renderStartTime = SystemClock.elapsedRealtime()

    private val mediaType = baseAdView.mediaType
    private var currentVisibilityRatio = 0f
    private var isVisible = false
    private var viewStartTime: Long = 0
    private var totalViewTime: Long = 0
    private var hasViewEvent = false
    private var hasImpression = false
    private var renderTime: Long = 0

    private var playbackStartTime: Long = 0
    private var totalPlaybackTime: Long = 0
    private var hasEnded = false
    private var hasSentPlaybackEvent = false

    private var scrollListener: OnScrollChangedListener? = null
    private var focusListener: OnWindowFocusChangeListener? = null

    private val handler = Handler(Looper.getMainLooper())
    private var visibilityCheckRunnable: Runnable? = null

    init {
        initialize()
    }

    private fun initialize() {
        setupVisibilityTracking()
    }

    private fun setupVisibilityTracking() {
        val vto = baseAdView.viewTreeObserver

        scrollListener = OnScrollChangedListener { this.checkVisibility() }
        vto.addOnScrollChangedListener(scrollListener)

        focusListener =
            OnWindowFocusChangeListener { hasFocus: Boolean -> onVisibilityChange(hasFocus) }
        vto.addOnWindowFocusChangeListener(focusListener)

        checkVisibility()
    }

    private fun checkVisibility() {
        val rect = Rect()
        val isVisible = baseAdView.getGlobalVisibleRect(rect)

        if (!isVisible) {
            handleVisibilityChange(false)
            return
        }

        val totalWidth = baseAdView.width
        val totalHeight = baseAdView.height

        if (totalWidth == 0 || totalHeight == 0) {
            return
        }

        val visibleWidth = rect.width()
        val visibleHeight = rect.height()

        currentVisibilityRatio =
            (visibleWidth * visibleHeight) / (totalWidth * totalHeight).toFloat()
        val newVisible = currentVisibilityRatio >= VISIBILITY_THRESHOLD

        handleVisibilityChange(newVisible)
    }

    private fun handleVisibilityChange(newVisible: Boolean) {
        val wasVisible = isVisible
        isVisible = newVisible

        if (isVisible && !wasVisible) {
            if (viewStartTime == 0L) {
                viewStartTime = SystemClock.elapsedRealtime()
                startVisibilityCheck()
            }
            if ("video" == mediaType && !hasEnded) {
                webView?.onResume()
                onVideoPlay()
            }
        } else if (!isVisible && wasVisible) {
            updateViewTime()
            stopVisibilityCheck()
            if ("video" == mediaType && !hasEnded) {
                webView?.onPause()
                onVideoPause()
            }
        }
    }

    private fun startVisibilityCheck() {
        if (visibilityCheckRunnable != null) return
        visibilityCheckRunnable = object : Runnable {
            override fun run() {
                if (isVisible && viewStartTime > 0 && !hasViewEvent) {
                    val timeInView = SystemClock.elapsedRealtime() - viewStartTime
                    if (timeInView >= MIN_VIEW_TIME) {
                        hasViewEvent = true
                        baseAdView.listener?.onAdImpression()
                        val scrollDepth: Float = scrollDepth()
                        val timeToVisible = SystemClock.elapsedRealtime() - renderStartTime
                        val analyticsRequest: AnalyticsRequest =
                            AnalyticsRequest.AnalyticsRequestBuilder(baseAdView.metaData, baseAdView.isTestMode)
                                .trackViewableImpression(
                                    timeToVisible,
                                    scrollDepth,
                                    currentVisibilityRatio,
                                    timeInView
                                )
                                .build()
                        postCreativeAnalytics.sendTrackingDataV2(analyticsRequest)
                        stopVisibilityCheck()
                    }
                }
                handler.postDelayed(this, 100)
            }
        }

        val runnable = visibilityCheckRunnable
        handler.post(runnable!!)
    }

    private fun findRootScrollView(view: View?): View? {
        var view = view
        var scrollParent: View? = null

        while (view != null) {
            if (isScrollableView(view)) {
                scrollParent = view
            }
            if (view.parent is View) {
                view = view.parent as View
            } else {
                break
            }
        }
        return scrollParent
    }

    private fun isScrollableView(view: View): Boolean {
        return (view is ScrollView
                || view is NestedScrollView
                || view is HorizontalScrollView)
    }

    private fun scrollDepth(): Float {
        val scrollView = findRootScrollView(baseAdView)

        // Get AdView absolute Y on screen
        val adLocation = IntArray(2)
        baseAdView.getLocationOnScreen(adLocation)
        val adTopOnScreen = adLocation[1]

        // Get ScrollView top on screen
        val scrollLocation = IntArray(2)
        scrollView!!.getLocationOnScreen(scrollLocation)
        val scrollTopOnScreen = scrollLocation[1]

        val requiredScroll = adTopOnScreen - scrollTopOnScreen
        if (requiredScroll <= 0) {
            return 1f
        }

        // 4. Current scroll amount
        val currentScroll = scrollView.scrollY
        var rawRatio = currentScroll.toFloat() / requiredScroll.toFloat()
        if (rawRatio < 0f) rawRatio = 0f
        if (rawRatio > 1f) rawRatio = 1f

        return rawRatio
    }

    private fun updateViewTime() {
        if (viewStartTime > 0) {
            totalViewTime += SystemClock.elapsedRealtime() - viewStartTime
            viewStartTime = 0
            stopVisibilityCheck()
        }
    }

    private fun stopVisibilityCheck() {
        if (visibilityCheckRunnable != null) {
            handler.removeCallbacks(visibilityCheckRunnable!!)
            visibilityCheckRunnable = null
        }
    }

    fun onVisibilityChange(hasFocus: Boolean) {
        if (!hasFocus) {
            updateViewTime()
            stopVisibilityCheck()
            if ("video" == mediaType && !hasEnded) {
                webView?.onPause()
                onVideoPause()
            }
        } else if (isVisible) {
            if ("video" == mediaType && !hasEnded) {
                webView?.onResume()
                onVideoPlay()
            }
        }
    }

    fun onVideoPlay() {
        if (playbackStartTime == 0L) {
            playbackStartTime = SystemClock.elapsedRealtime()
        }
    }

    fun onVideoPause() {
        updatePlaybackTime()
    }

    fun onVideoEnd() {
        if (!hasEnded && "video" == mediaType) {
            hasEnded = true
            updatePlaybackTime()
        }
    }

    private fun updatePlaybackTime() {
        if (playbackStartTime > 0 && "video" == mediaType) {
            totalPlaybackTime += SystemClock.elapsedRealtime() - playbackStartTime
            playbackStartTime = 0
        }
    }

    fun captureImpression() {
        if (!hasImpression) {
            renderTime = SystemClock.elapsedRealtime() - renderStartTime
            Log.e(
                TAG,
                "renderTime= $renderTime"
            )
            baseAdView.listener?.onAdLoaded()
            val analyticsRequest: AnalyticsRequest =
                AnalyticsRequest.AnalyticsRequestBuilder(baseAdView.metaData, baseAdView.isTestMode)
                    .trackImpression(renderTime)
                    .build()
            postCreativeAnalytics.sendTrackingDataV2(analyticsRequest)
            hasImpression = true
        }
    }

    fun captureClick() {
        baseAdView.listener?.onAdClicked()
        val analyticsRequest: AnalyticsRequest =
            AnalyticsRequest.AnalyticsRequestBuilder(baseAdView.metaData, baseAdView.isTestMode)
                .trackClick()
                .build()
        postCreativeAnalytics.sendTrackingDataV2(analyticsRequest)
    }

    fun captureTotalViewTime() {
        val analyticsRequest: AnalyticsRequest =
            AnalyticsRequest.AnalyticsRequestBuilder(baseAdView.metaData, baseAdView.isTestMode)
                .trackTotalViewTime(totalViewTime)
                .build()
        postCreativeAnalytics.sendTrackingDataV2(analyticsRequest)
    }

    fun captureTotalVideoPlaybackTime() {
        if (totalPlaybackTime > 0 && !hasSentPlaybackEvent && "video" == mediaType) {
            val analyticsRequest: AnalyticsRequest =
                AnalyticsRequest.AnalyticsRequestBuilder(baseAdView.metaData, baseAdView.isTestMode)
                    .trackTotalPlaybackTime(totalPlaybackTime)
                    .build()
            postCreativeAnalytics.sendTrackingDataV2(analyticsRequest)
            hasSentPlaybackEvent = true
        }
    }

    private val webView: WebView?
        get() {
            if (baseAdView.childCount > 0 && baseAdView.getChildAt(0) is WebView) {
                return baseAdView.getChildAt(0) as WebView
            }
            return null
        }

    fun destroy() {
        val vto = baseAdView.viewTreeObserver
        if (vto.isAlive) {
            vto.removeOnScrollChangedListener(scrollListener)
        }

        captureTotalViewTime()
        captureTotalVideoPlaybackTime()
        updateViewTime()
        stopVisibilityCheck()
    }

    companion object {
        private const val TAG = "Ad Activity"
        private const val VISIBILITY_THRESHOLD = 0.5
        private const val MIN_VIEW_TIME = 1000L
    }
}