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
import com.adgeistkit.request.AnalyticsRequest

/**
 * Tracks viewability, impressions, clicks and video playback for one ad.
 * Survives AdView recreation (see AdSessionStore) via pause/resume/rebind.
 */
class AdActivity(private var baseAdView: BaseAdView) {

    companion object {
        private const val TAG = "Ad Activity"
        private const val VISIBILITY_THRESHOLD = 0.5
        private const val MIN_VIEW_TIME = 1000L
    }

    private val postCreativeAnalytics = getInstance().postCreativeAnalytics()
    private val renderStartTime = SystemClock.elapsedRealtime()
    private val mediaType = baseAdView.mediaType

    // Viewability state
    private var currentVisibilityRatio = 0f
    private var isVisible = false
    private var viewStartTime: Long = 0
    private var totalViewTime: Long = 0
    private var hasViewEvent = false
    private var hasImpression = false
    private var renderTime: Long = 0

    // Video playback state
    private var playbackStartTime: Long = 0
    private var totalPlaybackTime: Long = 0
    private var hasEnded = false

    // ViewTreeObserver listeners and the periodic visibility check
    private var scrollListener: OnScrollChangedListener? = null
    private var focusListener: OnWindowFocusChangeListener? = null
    private var listenersAttached = false
    private val handler = Handler(Looper.getMainLooper())
    private var visibilityCheckRunnable: Runnable? = null

    init {
        attachListeners()
        checkVisibility()
    }

    // ---------------------------------------------------------------------
    // Lifecycle: pause on detach, resume on attach, rebind on adoption
    // ---------------------------------------------------------------------

    /** Suspends tracking while detached; view time and impression flags are kept. */
    fun pause() {
        updateViewTime()
        stopVisibilityCheck()
        detachListeners()
        if ("video" == mediaType && !hasEnded) {
            onVideoPause()
        }
        // Force a visibility transition on resume so timers restart correctly
        isVisible = false
    }

    /** Re-registers on the new window's ViewTreeObserver after re-attach. */
    fun resume() {
        attachListeners()
        checkVisibility()
    }

    /** Points tracking at the AdView that adopted this ad; nothing re-fires. */
    fun rebind(newHost: BaseAdView) {
        detachListeners()
        baseAdView = newHost
        attachListeners()
        checkVisibility()
    }

    fun destroy() {
        updateViewTime()
        stopVisibilityCheck()
        detachListeners()
    }

    private fun attachListeners() {
        if (listenersAttached) return

        val vto = baseAdView.viewTreeObserver
        if (!vto.isAlive) return

        scrollListener = OnScrollChangedListener { this.checkVisibility() }
        vto.addOnScrollChangedListener(scrollListener)

        focusListener =
            OnWindowFocusChangeListener { hasFocus: Boolean -> onVisibilityChange(hasFocus) }
        vto.addOnWindowFocusChangeListener(focusListener)

        listenersAttached = true
    }

    private fun detachListeners() {
        if (!listenersAttached) return

        val vto = baseAdView.viewTreeObserver
        if (vto.isAlive) {
            scrollListener?.let { vto.removeOnScrollChangedListener(it) }
            focusListener?.let { vto.removeOnWindowFocusChangeListener(it) }
        }
        scrollListener = null
        focusListener = null
        listenersAttached = false
    }

    // ---------------------------------------------------------------------
    // Viewability
    // ---------------------------------------------------------------------

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

        currentVisibilityRatio =
            (rect.width() * rect.height()) / (totalWidth * totalHeight).toFloat()
        handleVisibilityChange(currentVisibilityRatio >= VISIBILITY_THRESHOLD)
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
        if (visibilityCheckRunnable != null || hasViewEvent) return

        visibilityCheckRunnable = object : Runnable {
            override fun run() {
                // Stop if this check was cancelled (stopVisibilityCheck nulls the field)
                if (visibilityCheckRunnable !== this) return

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
                        return
                    }
                }
                handler.postDelayed(this, 100)
            }
        }

        val runnable = visibilityCheckRunnable
        handler.post(runnable!!)
    }

    private fun stopVisibilityCheck() {
        if (visibilityCheckRunnable != null) {
            handler.removeCallbacks(visibilityCheckRunnable!!)
            visibilityCheckRunnable = null
        }
    }

    private fun updateViewTime() {
        if (viewStartTime > 0) {
            totalViewTime += SystemClock.elapsedRealtime() - viewStartTime
            viewStartTime = 0
            stopVisibilityCheck()
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

    // ---------------------------------------------------------------------
    // Events
    // ---------------------------------------------------------------------

    fun captureImpression() {
        if (!hasImpression) {
            renderTime = SystemClock.elapsedRealtime() - renderStartTime
            baseAdView.listener?.onAdLoaded()
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

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private val webView: WebView?
        get() {
            if (baseAdView.childCount > 0 && baseAdView.getChildAt(0) is WebView) {
                return baseAdView.getChildAt(0) as WebView
            }
            return null
        }

    /** How far the user had to scroll for the ad to become reachable (0..1). */
    private fun scrollDepth(): Float {
        val scrollView = findRootScrollView(baseAdView) ?: return 1f

        val adLocation = IntArray(2)
        baseAdView.getLocationOnScreen(adLocation)
        val adTopOnScreen = adLocation[1]

        val scrollLocation = IntArray(2)
        scrollView.getLocationOnScreen(scrollLocation)
        val scrollTopOnScreen = scrollLocation[1]

        val requiredScroll = adTopOnScreen - scrollTopOnScreen
        if (requiredScroll <= 0) {
            return 1f
        }

        val currentScroll = scrollView.scrollY
        var rawRatio = currentScroll.toFloat() / requiredScroll.toFloat()
        if (rawRatio < 0f) rawRatio = 0f
        if (rawRatio > 1f) rawRatio = 1f

        return rawRatio
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
}
