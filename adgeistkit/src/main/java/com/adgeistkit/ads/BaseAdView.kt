package com.adgeistkit.ads

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.annotation.RequiresPermission
import androidx.core.view.doOnLayout
import androidx.lifecycle.ViewModelStoreOwner
import com.adgeistkit.AdgeistCore.Companion.getInstance
import com.adgeistkit.R
import com.adgeistkit.utilities.logD
import com.adgeistkit.request.AdRequest
import com.adgeistkit.data.models.FixedAdResponse
import com.adgeistkit.ads.host.findActivity
import com.adgeistkit.ads.host.findAdViewModel
import com.adgeistkit.ads.host.pxToDp
import com.adgeistkit.ads.host.releaseImeSession
import com.adgeistkit.ads.measure.AdSizeResolver
import com.adgeistkit.ads.render.AdCreativePayload
import com.adgeistkit.ads.render.AdWebViewFactory
import com.adgeistkit.ads.render.AdShellPreloader
import com.adgeistkit.ads.render.AdWebViewTeardown
import com.adgeistkit.ads.tracking.AdTrackingState
import com.adgeistkit.ads.viewmodel.AdViewModel
import com.adgeistkit.ads.viewmodel.RetainedAd
import com.adgeistkit.benchmark.AdRenderBenchmark
import com.adgeistkit.data.models.AdSpaceType
import com.adgeistkit.data.network.FetchCreative

public open class BaseAdView : ViewGroup {

    public companion object {
        private const val TAG = "BaseAdView"
    }

    // ---- Ad configuration ----

    public var adUnitId: String = ""
    public var adSize: AdSize? = null
    public var reserveSpace: Boolean = true
    public var adIsResponsive: Boolean = false

    /**
     * False scopes identity and teardown to the Activity instead of the fragment,
     * for embedders whose fragments are transient wrappers (react-native-screens
     * recreates one every time a screen is covered).
     */
    public var watchFragmentLifecycle: Boolean = true

    public var viewModelStoreOwner: ViewModelStoreOwner? = null
        set(value) {
            if (value === field) return
            field = value
            adViewModel = null
        }

    // ---- Creative metadata (read by tracking) ----

    public var metaData: String = ""

    internal var mediaType: String = ""
        private set

    // ---- Collaborators ----

    public var listener: AdListener? = null

    // ---- Runtime state ----

    internal var webView: WebView? = null
    private var jsInterface: JsBridge? = null
    private var isLoading: Boolean = false
    private var isDestroyed = false
    private var mainHandler: Handler? = null

    private var serverAdSize: AdSize? = null
    private var responsiveAxisWarningSent = false
    private var adViewModel: AdViewModel? = null
    internal var tracking: AdTrackingState = AdTrackingState()
        private set

    // loadAd() before attach cannot reach the screen yet, so the request waits here
    // until onAttachedToWindow()
    private var pendingLoadRequest: AdRequest? = null

    private val benchmark = AdRenderBenchmark()
    private val shellPreloader = AdShellPreloader(
        benchmark,
        Handler(Looper.getMainLooper()),
        ::failShellLoad
    )

    protected constructor(context: Context, adViewType: Int) : super(context) {
        initialize(context, null)
    }

    protected constructor(context: Context, attrs: AttributeSet, adViewType: Int) : super(
        context,
        attrs
    ) {
        initialize(context, attrs)
    }

    protected constructor(
        context: Context,
        attrs: AttributeSet,
        defStyle: Int,
        adViewType: Int
    ) : super(context, attrs, defStyle) {
        initialize(context, attrs)
    }

    private fun initialize(context: Context, attrs: AttributeSet?) {
        mainHandler = Handler(Looper.getMainLooper())

        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.AdView)
            try {
                val xmlAdUnitId = typedArray.getString(R.styleable.AdView_adUnitId)
                if (xmlAdUnitId != null && !xmlAdUnitId.isEmpty()) {
                    adUnitId = xmlAdUnitId
                }
            } finally {
                typedArray.recycle()
            }
        }
    }

    public fun setAdListener(listener: AdListener?) {
        this.listener = listener
    }

    public fun setAdDimension(adSize: AdSize) {
        requireNotNull(adSize) { "AdSize cannot be null" }
        this.adSize = adSize
        requestLayout()
    }

    @RequiresPermission("android.permission.INTERNET")
    public fun loadAd(adRequest: AdRequest) {
        if (adUnitId.isEmpty()) {
            notifyAdFailedToLoad("Ad unit ID is null or empty")
            return
        }

        if (isLoading) {
            listener?.onAdFailedToLoad("Ad unit ID is already loading")
            return
        }

        benchmark.onLoadStart()

        if (!resolveAdViewModel() && !isAttachedToWindow) {
            // The view-tree owners are unreachable before attach. Resumed from onAttachedToWindow();
            pendingLoadRequest = adRequest
            return
        }

        performLoad()
    }

    private fun performLoad() {
        benchmark.onLoadDispatched()

        adViewModel?.retained(adUnitId)?.let { retained ->
            restoreRetained(retained)
            return
        }

        isLoading = true

        if (webView != null) {
            safelyDestroyWebView()
        }

        mainHandler?.post {
            isDestroyed = false
            startAdLoad()
            preloadShell()
        }
    }

    private fun restoreRetained(retained: RetainedAd) {
        benchmark.onCacheHit()
        isLoading = true

        if (webView != null) {
            safelyDestroyWebView()
        }

        tracking = retained.tracking
        mediaType = retained.response.creativesV1.firstOrNull()?.primary?.type ?: ""

        mainHandler?.post {
            isDestroyed = false
            preloadShell()

            doOnLayout {
                if (!isAttachedToWindow) {
                    isLoading = false
                    safelyDestroyWebView()
                    return@doOnLayout
                }

                isLoading = false

                val payload = AdCreativePayload.build(
                    response = retained.response,
                    adUnitId = adUnitId,
                    adIsResponsive = adIsResponsive,
                    adSize = adSize,
                    measuredWidthDp = pxToDp(measuredWidth),
                    measuredHeightDp = pxToDp(measuredHeight),
                )

                when (payload) {
                    is AdCreativePayload.Result.Failure -> {
                        safelyDestroyWebView()
                        notifyAdFailedToLoad(payload.message)
                    }

                    is AdCreativePayload.Result.Success -> {
                        metaData = payload.metaData
                        applyServerAdSize(retained.response)
                        renderAdWithAdCard(payload.creativeJson, retained.response.adSpaceType)
                    }
                }
            }
        }
    }

    public fun destroyAd() {
        adViewModel?.release(adUnitId)
        destroyInternal()
        removeFromParent()
    }

    internal fun destroyInternal() {
        if (isDestroyed) return

        isLoading = false
        mainHandler?.removeCallbacksAndMessages(null)
        listener?.onAdClosed()
        safelyDestroyWebView()
    }

    private fun resolveAdViewModel(): Boolean {
        if (adViewModel == null) {
            adViewModel = findAdViewModel(watchFragmentLifecycle, viewModelStoreOwner)
        }

        return adViewModel != null
    }

    public fun removeFromParent() {
        try {
            (parent as? ViewGroup)?.removeView(this)
        } catch (_: Exception) {
            //
        }
    }

    private fun applyServerAdSize(response: FixedAdResponse) {
        if (adIsResponsive) return

        val dimensions = response.displayOptions?.dimensions ?: return
        val serverWidth = dimensions.width ?: return
        val serverHeight = dimensions.height ?: return
        if (serverWidth <= 0 || serverHeight <= 0) return

        val resolved = AdSize(serverWidth, serverHeight)
        if (serverAdSize == resolved) return

        val requested = adSize
        serverAdSize = resolved
        requestLayout()

        if (requested == null || requested == resolved) return

        listener?.onAdWarning(
            "Ad unit '$adUnitId': the requested size $requested does not match the creative " +
                "size $resolved returned for this ad unit. The AdView has been resized to " +
                "$resolved. Set the ad unit's size to $resolved to avoid a layout shift."
        )
    }

    private fun notifyAdFailedToLoad(message: String) {
        listener?.onAdFailedToLoad(message)

        if (reserveSpace) return

        mainHandler?.post { removeFromParent() }
    }

    private fun failShellLoad(message: String) {
        if (isDestroyed) return

        safelyDestroyWebView()
        notifyAdFailedToLoad(message)
    }

    private fun safelyDestroyWebView() {
        if (isDestroyed) return
        isDestroyed = true

        val webViewToDestroy = webView
        webView = null
        shellPreloader.resetForNewLoad()
        jsInterface?.destroyListeners()
        jsInterface = null

        if (webViewToDestroy == null) return

        val handler = mainHandler ?: return
        handler.post {
            AdWebViewTeardown.destroy(webViewToDestroy, handler) {
                logD(TAG) { "WebView destroyed" }
            }
            removeAllViews()
        }
    }

    // ---- Benchmarking ----

    internal fun markJsPhase(phase: AdRenderBenchmark.JsPhase) {
        benchmark.onJsPhase(phase)
    }

    internal fun reportFirstFrame() {
        if (!benchmark.markFirstFrame()) return
        mainHandler?.post { benchmark.log(adUnitId) }
    }

    // ---- Loading and rendering ----

    private fun startAdLoad() {
        val adgeist = getInstance()
        val fetchCreative: FetchCreative = adgeist.getCreative()

        benchmark.onFetchStart()

        fetchCreative.fetchCreative(adUnitId) { result ->
            benchmark.onFetchEnd(result.timings)

            mainHandler?.post {
                isLoading = false
                if (isDestroyed) return@post

                if (!result.isSuccess) {
                    safelyDestroyWebView()
                    notifyAdFailedToLoad(result.errorMessage)
                    return@post
                }

                try {
                    val campaignDetails = result.data as FixedAdResponse

                    val payload = AdCreativePayload.build(
                        response = campaignDetails,
                        adUnitId = adUnitId,
                        adIsResponsive = adIsResponsive,
                        adSize = adSize,
                        measuredWidthDp = pxToDp(measuredWidth),
                        measuredHeightDp = pxToDp(measuredHeight),
                    )

                    when (payload) {
                        is AdCreativePayload.Result.Failure -> {
                            safelyDestroyWebView()
                            notifyAdFailedToLoad(payload.message)
                        }

                        is AdCreativePayload.Result.Success -> {
                            metaData = payload.metaData
                            mediaType = campaignDetails.creativesV1.firstOrNull()?.primary?.type ?: ""

                            adViewModel?.retain(adUnitId, RetainedAd(campaignDetails, tracking))

                            applyServerAdSize(campaignDetails)
                            renderAdWithAdCard(payload.creativeJson, campaignDetails.adSpaceType)
                        }
                    }
                } catch (err: Exception) {
                    Log.e(TAG, "Parsing error: ${err.message}", err)
                    safelyDestroyWebView()
                    notifyAdFailedToLoad(err.message ?: "Error")
                }
            }
        }
    }

    private fun preloadShell() {
        if (isDestroyed) return

        benchmark.onPreloadStart()
        removeAllViews()

        shellPreloader.resetForNewLoad()

        val bridge = JsBridge(this, context)
        jsInterface = bridge

        val created = AdWebViewFactory.create(context, bridge)
        benchmark.onWebViewCreated(created.allocEndAt)

        val adWebView = created.webView
        webView = adWebView

        if (!shellPreloader.loadShellIntoWebView(adWebView, context.assets)) return
    }

    internal fun onShellReady() {
        if (isDestroyed) return

        shellPreloader.markShellReadyAndReleaseCreative()?.let { renderCreative(it) }
    }

    internal fun onShellPageFinished() {
        if (isDestroyed) return

        shellPreloader.onShellPageFinished()
    }

    internal fun onRenderProcessGone(didCrash: Boolean) {
        if (isDestroyed) return

        shellPreloader.onRendererProcessLost(didCrash)
    }

    private fun renderAdWithAdCard(creativeJsonData: String, adSpaceType: AdSpaceType) {
        if (isDestroyed) return

        if (shellPreloader.submitCreativeForRender(creativeJsonData, adSpaceType)) {
            renderCreative(creativeJsonData)
        }
    }

    private fun renderCreative(creativeJsonData: String) {
        val adWebView = webView ?: return

        benchmark.onRenderStart()
        listener?.onAdOpened()

        addView(adWebView, AdWebViewFactory.matchParentLayoutParams())

        // Companion ads stay hidden until the overflow check completes
        if (shellPreloader.submittedAdSpaceType == AdSpaceType.COMPANION) {
            adWebView.visibility = View.INVISIBLE
        }

        shellPreloader.injectCreativeIntoShell(adWebView, creativeJsonData)
    }

    // ---- Measurement and layout ----
    private fun warnIfResponsiveAxisUndetermined(measurement: AdSizeResolver.Result) {
        if (responsiveAxisWarningSent) return

        val message = AdSizeResolver.undeterminedAxisWarning(
            adUnitId = adUnitId,
            widthUndetermined = measurement.widthUndetermined,
            heightUndetermined = measurement.heightUndetermined
        ) ?: return

        responsiveAxisWarningSent = true
        mainHandler?.post { listener?.onAdWarning(message) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = serverAdSize ?: adSize
        val measurement = AdSizeResolver.resolve(
            widthMeasureSpec = widthMeasureSpec,
            heightMeasureSpec = heightMeasureSpec,
            adIsResponsive = adIsResponsive,
            declaredWidthPx = size?.getWidthInPixels(context) ?: 0,
            declaredHeightPx = size?.getHeightInPixels(context) ?: 0,
            minWidthPx = suggestedMinimumWidth,
            minHeightPx = suggestedMinimumHeight
        )

        if (adIsResponsive) {
            warnIfResponsiveAxisUndetermined(measurement)
        }

        AdSizeResolver.measureChild(this, measurement.widthPx, measurement.heightPx)

        setMeasuredDimension(measurement.widthPx, measurement.heightPx)
    }

    /** Centers the WebView child within this container. */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val child = getChildAt(0)
        if (child != null && child.visibility != GONE) {
            val width = child.measuredWidth
            val height = child.measuredHeight

            val horizontalSpacing = (right - left - width) / 2
            val verticalSpacing = (bottom - top - height) / 2

            child.layout(
                horizontalSpacing,
                verticalSpacing,
                horizontalSpacing + width,
                verticalSpacing + height
            )
        }
    }

    // ---- Window lifecycle (detach only pauses, never destroys) ----

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        // The view-tree owners are only reachable now, so this is where the screen
        // becomes reachable and a deferred loadAd() can proceed.
        resolveAdViewModel()
        pendingLoadRequest?.let {
            pendingLoadRequest = null
            performLoad()
            return
        }

        // react-native-screens re-parents this same view on screen recreation instead of
        // inflating a new one, so no loadAd() follows: restore the retained ad, not a new one.
        // isDestroyed covers the same gap for plain Android hosts - a RecyclerView row that
        // scrolled off long enough to be torn down reattaches without a loadAd() either.
        if ((!watchFragmentLifecycle || isDestroyed) &&
            webView == null && adUnitId.isNotEmpty() && !isLoading
        ) {
            if (adViewModel?.retained(adUnitId) != null) {
                performLoad()
            }
            return
        }

        if (isDestroyed) return

        try {
            webView?.onResume()
        } catch (_: Exception) {
            //
        }
        jsInterface?.onHostAttached()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)

        if (webView == null || isDestroyed) return

        if (visibility == VISIBLE) {
            try {
                webView?.onResume()
            } catch (_: Exception) {
                //
            }
        } else {
            try {
                webView?.onPause()
            } catch (_: Exception) {
                //
            }
            releaseImeSession(mainHandler)
        }
    }

    override fun onDetachedFromWindow() {
        if (!isDestroyed) {
            jsInterface?.onHostDetached()
            try {
                webView?.onPause()
            } catch (_: Exception) {
                //
            }
        }
        
        val hostDecorView = context.findActivity()?.window?.decorView

        super.onDetachedFromWindow()
        mainHandler?.post {
            // Repairs the host window's layout pass, which detaching a rendered ad WebView
            // can wedge: every view from here up to the DecorView is left flagged as
            // "layout already requested" while ViewRootImpl has no pass scheduled.
            // requestLayout() only walks up while the parent is unflagged, so a later
            // sibling - the next screen the host shows - stops at the first flagged
            // ancestor, never reaches ViewRootImpl, and measures 0x0 forever.
            // The DecorView's parent IS ViewRootImpl, so asking there skips the flagged
            // chain and schedules the pass that clears it. Posted so it lands after the
            // host finishes swapping this view out; running it inline would repair
            // nothing, because the damage is not done yet.
            hostDecorView?.requestLayout()

            if (isAttachedToWindow) return@post

            destroyInternal()
            releaseImeSession(mainHandler)
        }
    }
}
