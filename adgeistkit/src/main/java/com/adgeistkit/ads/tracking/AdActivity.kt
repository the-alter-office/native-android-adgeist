package com.adgeistkit.ads.tracking

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver.OnScrollChangedListener
import android.view.ViewTreeObserver.OnWindowFocusChangeListener
import android.webkit.WebView
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import com.adgeistkit.AdgeistCore
import com.adgeistkit.request.AnalyticsRequest
import com.adgeistkit.ads.AdgeistEvent
import com.adgeistkit.ads.AdgeistEventCode
import com.adgeistkit.ads.BaseAdView
import com.adgeistkit.constants.General

/**
 * Tracks viewability, impressions, clicks and video playback for one ad.
 *
 * A new instance is built every time the ad is rendered, including after a rotation
 * or a return to the screen. What must not restart lives in [tracking], which the
 * screen's cache owns and hands to every instance - so a rebuilt ad is never counted
 * twice.
 */
internal class AdActivity(private var baseAdView: BaseAdView) {
    private val tracking: AdTrackingState = baseAdView.tracking

    companion object {
        private const val VISIBILITY_THRESHOLD = 0.5
    }

    // ---- Collaborators ----

    private val postCreativeAnalytics = AdgeistCore.getInstance()?.postCreativeAnalytics()
    private val renderStartTime = SystemClock.elapsedRealtime()
    private val handler = Handler(Looper.getMainLooper())

    private val isVideo: Boolean
        get() = "video" == baseAdView.mediaType

    // ---- Viewability state ----

    private val visibleRect = Rect()
    private var currentVisibilityRatio = 0f
    private var isVisible = false
    private var viewStartTime: Long = 0
    private var accumulatedVisibleMs: Long = 0
    private var hasImpression = false
    private var hasRendered = false

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
        if (isVideo && !hasEnded) {
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
        val isVisible = baseAdView.getGlobalVisibleRect(visibleRect)

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
            (visibleRect.width() * visibleRect.height()) / (totalWidth * totalHeight).toFloat()
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
            if (isVideo && !hasEnded) {
                webView?.onResume()
                onVideoPlay()
            }
        } else if (!isVisible && wasVisible) {
            endViewInterval()
            stopVisibilityCheck()
            if (isVideo && !hasEnded) {
                webView?.onPause()
                onVideoPause()
            }
        }
    }

    private fun startVisibilityCheck() {
        if (!hasRendered) return
        if (visibilityCheckRunnable != null || tracking.impressionSent) return

        val runnable = object : Runnable {
            override fun run() {
                // Stop if this check was cancelled (stopVisibilityCheck nulls the field)
                if (visibilityCheckRunnable !== this) return
                if (!isVisible || viewStartTime <= 0 || tracking.impressionSent) return

                val timeInView = SystemClock.elapsedRealtime() - viewStartTime
                if (timeInView < General.Timing.MIN_VIEW_TIME_MS) {
                    handler.postDelayed(this, General.Timing.MIN_VIEW_TIME_MS - timeInView)
                    return
                }

                tracking.impressionSent = true

                val scrollDepth: Float = scrollDepth()
                val timeToVisible = SystemClock.elapsedRealtime() - renderStartTime
                val analyticsRequest: AnalyticsRequest =
                    AnalyticsRequest.AnalyticsRequestBuilder(baseAdView.metaData)
                        .trackViewableImpression(
                            timeToVisible,
                            scrollDepth,
                            currentVisibilityRatio
                        )
                        .build()
                postCreativeAnalytics?.sendTrackingDataV2(analyticsRequest)

                stopVisibilityCheck()
            }
        }

        visibilityCheckRunnable = runnable
        handler.postDelayed(runnable, General.Timing.MIN_VIEW_TIME_MS)
    }

    private fun stopVisibilityCheck() {
        visibilityCheckRunnable?.let { handler.removeCallbacks(it) }
        visibilityCheckRunnable = null
    }

    /** Closes the current visible interval, adding its duration to [accumulatedVisibleMs]. */
    private fun endViewInterval() {
        if (viewStartTime > 0) {
            accumulatedVisibleMs += SystemClock.elapsedRealtime() - viewStartTime
            viewStartTime = 0
            stopVisibilityCheck()
        }
    }

    fun onVisibilityChange(hasFocus: Boolean) {
        if (!hasFocus) {
            endViewInterval()
            stopVisibilityCheck()
            if (isVideo && !hasEnded) {
                webView?.onPause()
                onVideoPause()
            }
            isVisible = false
        } else {
            checkVisibility()
            if (isVisible && isVideo && !hasEnded) {
                webView?.onResume()
                onVideoPlay()
            }
        }
    }

    // ---- Events ----

    fun captureImpression() {
        handler.post {
            if (!hasImpression) {
                baseAdView.listener?.onAdEvent(AdgeistEvent(AdgeistEventCode.AL1))
                hasImpression = true
            }

            if (hasRendered) return@post
            hasRendered = true
            isVisible = false
            viewStartTime = 0
            checkVisibility()
        }
    }

    fun captureClick() {
        val now = SystemClock.elapsedRealtime()
        val sinceLastClick = now - tracking.lastClickTime
        if (sinceLastClick < General.Timing.CLICK_DEBOUNCE_MS) {
            return
        }
        tracking.lastClickTime = now

        baseAdView.listener?.onAdEvent(AdgeistEvent(AdgeistEventCode.AI1))
        val analyticsRequest: AnalyticsRequest =
            AnalyticsRequest.AnalyticsRequestBuilder(baseAdView.metaData)
                .trackClick()
                .build()
        postCreativeAnalytics?.sendTrackingDataV2(analyticsRequest)
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
        if (!hasEnded && isVideo) {
            hasEnded = true
            endPlaybackInterval()
        }
    }

    private fun endPlaybackInterval() {
        if (playbackStartTime > 0 && isVideo) {
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
