package com.adgeistkit.ads.tracking

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
import com.adgeistkit.ads.BaseAdView

/**
 * Tracks viewability, impressions, clicks and video playback for one ad. Survives
 * AdView recreation via pause/resume/rebind, keeping its impression state so an
 * adopted ad is never counted twice.
 *
 * Note: the video branches are gated on [mediaType], which nothing currently sets,
 * so video tracking is inert until that is wired up.
 */
internal class AdActivity(private var baseAdView: BaseAdView) {

    companion object {
        private const val TAG = "Ad Activity"
        private const val VISIBILITY_THRESHOLD = 0.5
        private const val MIN_VIEW_TIME = 1000L
        private const val CLICK_DEBOUNCE_MS = 1000L
    }

    // ---- Collaborators ----

    private val postCreativeAnalytics = getInstance().postCreativeAnalytics()
    private val renderStartTime = SystemClock.elapsedRealtime()
    private val mediaType = baseAdView.mediaType
    private val handler = Handler(Looper.getMainLooper())

    // ---- Viewability state ----

    private var currentVisibilityRatio = 0f
    private var isVisible = false
    private var viewStartTime: Long = 0
    private var hasViewEvent = false
    private var hasImpression = false
    private var lastClickTime = 0L

    // ---- Video playback state ----

    private var playbackStartTime: Long = 0
    private var hasEnded = false

    // ---- Observer registration ----

    private var scrollListener: OnScrollChangedListener? = null
    private var focusListener: OnWindowFocusChangeListener? = null
    private var listenersAttached = false
    private var visibilityCheckRunnable: Runnable? = null

    init {
        attachListeners()
        checkVisibility()
    }

    // ---- Lifecycle: pause on detach, resume on attach, rebind on adoption ----

    /** Suspends tracking while detached; view time and impression flags are kept. */
    fun pause() {
        endViewInterval()
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
        endViewInterval()
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

    // ---- Viewability ----

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
            endViewInterval()
            stopVisibilityCheck()
            if ("video" == mediaType && !hasEnded) {
                webView?.onPause()
                onVideoPause()
            }
        }
    }

    private fun startVisibilityCheck() {
        if (visibilityCheckRunnable != null || hasViewEvent) return

        val runnable = object : Runnable {
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
                            AnalyticsRequest.AnalyticsRequestBuilder(baseAdView.metaData)
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

        visibilityCheckRunnable = runnable
        handler.post(runnable)
    }

    private fun stopVisibilityCheck() {
        visibilityCheckRunnable?.let { handler.removeCallbacks(it) }
        visibilityCheckRunnable = null
    }

    /** Closes the current visible interval; the reported viewTime is measured separately. */
    private fun endViewInterval() {
        if (viewStartTime > 0) {
            viewStartTime = 0
            stopVisibilityCheck()
        }
    }

    fun onVisibilityChange(hasFocus: Boolean) {
        if (!hasFocus) {
            endViewInterval()
            stopVisibilityCheck()
            if ("video" == mediaType && !hasEnded) {
                webView?.onPause()
                onVideoPause()
            }
            isVisible = false
        } else {
            checkVisibility()
            if (isVisible && "video" == mediaType && !hasEnded) {
                webView?.onResume()
                onVideoPlay()
            }
        }
    }

    // ---- Events ----

    fun captureImpression() {
        if (!hasImpression) {
            baseAdView.listener?.onAdLoaded()
            hasImpression = true
        }
    }

    fun captureClick() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastClickTime < CLICK_DEBOUNCE_MS) {
            Log.d(TAG, "Click ignored - debounced (${now - lastClickTime}ms since last)")
            return
        }
        lastClickTime = now

        baseAdView.listener?.onAdClicked()
        val analyticsRequest: AnalyticsRequest =
            AnalyticsRequest.AnalyticsRequestBuilder(baseAdView.metaData)
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
        endPlaybackInterval()
    }

    fun onVideoEnd() {
        if (!hasEnded && "video" == mediaType) {
            hasEnded = true
            endPlaybackInterval()
        }
    }

    private fun endPlaybackInterval() {
        if (playbackStartTime > 0 && "video" == mediaType) {
            playbackStartTime = 0
        }
    }

    // ---- Helpers ----

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
