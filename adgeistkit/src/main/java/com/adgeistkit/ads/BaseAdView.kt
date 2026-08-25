package com.adgeistkit.ads

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import com.adgeistkit.request.AdRequest
import com.adgeistkit.data.models.FixedAdResponse
import kotlin.math.max
import com.adgeistkit.ads.cache.CreativeMediaCache
import com.adgeistkit.ads.host.findAdViewModel
import com.adgeistkit.ads.host.pxToDp
import com.adgeistkit.ads.host.releaseImeSession
import com.adgeistkit.ads.render.AdCardHtml
import com.adgeistkit.ads.render.AdCreativePayload
import com.adgeistkit.ads.render.AdWebViewFactory
import com.adgeistkit.ads.render.AdWebViewTeardown
import com.adgeistkit.ads.tracking.AdTrackingState
import com.adgeistkit.ads.viewmodel.AdViewModel
import com.adgeistkit.ads.viewmodel.RetainedAd
import com.adgeistkit.data.network.FetchCreative

open class BaseAdView : ViewGroup {

    companion object {
        private const val TAG = "BaseAdView"
    }

    // ---- Ad configuration ----

    var adSize: AdSize? = null
    var adUnitId: String = ""
    var adType: AdType = AdType.BANNER
    var adIsResponsive: Boolean = false

    /**
     * False scopes identity and teardown to the Activity instead of the fragment,
     * for embedders whose fragments are transient wrappers (react-native-screens
     * recreates one every time a screen is covered).
     */
    var watchFragmentLifecycle: Boolean = true

    var viewModelStoreOwner: ViewModelStoreOwner? = null
        set(value) {
            if (value === field) return
            field = value
            adViewModel = null
        }

    // ---- Creative metadata (read by tracking) ----

    var metaData: String = ""

    // ---- Collaborators ----

    var listener: AdListener? = null

    // ---- Runtime state ----

    internal var webView: WebView? = null
    private var jsInterface: JsBridge? = null
    private var isLoading: Boolean = false
    private var isDestroyed = false
    private var mainHandler: Handler? = null

    private var adViewModel: AdViewModel? = null
    internal var tracking: AdTrackingState = AdTrackingState()
        private set

    // loadAd() before attach cannot reach the screen yet, so the request waits here
    // until onAttachedToWindow()
    private var pendingLoadRequest: AdRequest? = null

    // Benchmarking
    private var benchmarkLoadStart: Long = 0
    private var benchmarkFetchStart: Long = 0
    @Volatile private var benchmarkFetchEnd: Long = 0
    private var benchmarkRenderStart: Long = 0
    private var benchmarkEngineEnd: Long = 0
    private var benchmarkAddViewEnd: Long = 0
    @Volatile private var benchmarkJsReady: Long = 0
    @Volatile private var benchmarkFirstFrameReported: Boolean = false
    private var benchmarkFromCache: Boolean = false

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

    fun setAdListener(listener: AdListener?) {
        this.listener = listener
    }

    fun setAdDimension(adSize: AdSize) {
        requireNotNull(adSize) { "AdSize cannot be null" }
        this.adSize = adSize
        requestLayout()
    }

    @RequiresPermission("android.permission.INTERNET")
    fun loadAd(adRequest: AdRequest) {
        if (adUnitId.isEmpty()) {
            listener?.onAdFailedToLoad("Ad unit ID is null or empty")
            return
        }

        if (isLoading) {
            listener?.onAdFailedToLoad("Ad unit ID is already loading")
            return
        }

        benchmarkLoadStart = SystemClock.elapsedRealtime()
        benchmarkFirstFrameReported = false
        benchmarkFromCache = false

        if (!resolveAdViewModel() && !isAttachedToWindow) {
            // The view-tree owners are unreachable before attach. Resumed from onAttachedToWindow();
            pendingLoadRequest = adRequest
            return
        }

        performLoad()
    }

    private fun performLoad() {
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
        }
    }

    private fun restoreRetained(retained: RetainedAd) {
        benchmarkFromCache = true
        isLoading = true

        if (webView != null) {
            safelyDestroyWebView()
        }

        tracking = retained.tracking
        prefetchCreativeMedia(retained.response)

        mainHandler?.post {
            doOnLayout {
                if (!isAttachedToWindow) {
                    isLoading = false
                    return@doOnLayout
                }

                isLoading = false
                isDestroyed = false

                val payload = AdCreativePayload.build(
                    response = retained.response,
                    adUnitId = adUnitId,
                    adType = adType,
                    adIsResponsive = adIsResponsive,
                    adSize = adSize,
                    measuredWidthDp = pxToDp(measuredWidth),
                    measuredHeightDp = pxToDp(measuredHeight),
                )

                when (payload) {
                    is AdCreativePayload.Result.Failure -> {
                        listener?.onAdFailedToLoad(payload.message)
                    }

                    is AdCreativePayload.Result.Success -> {
                        metaData = payload.metaData
                        renderAdWithAdCard(payload.creativeJson)
                    }
                }
            }
        }
    }

    fun destroyAd() {
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

    fun removeFromParent() {
        try {
            (parent as? ViewGroup)?.removeView(this)
        } catch (_: Exception) {
            //
        }
    }

    private fun safelyDestroyWebView() {
        if (isDestroyed) return
        isDestroyed = true

        val webViewToDestroy = webView
        webView = null
        jsInterface?.destroyListeners()
        jsInterface = null

        if (webViewToDestroy == null) return

        val handler = mainHandler ?: return
        handler.post {
            AdWebViewTeardown.destroy(webViewToDestroy, handler) {
                Log.d(TAG, "WebView destroyed")
            }
            removeAllViews()
        }
    }

    // ---- Benchmarking ----

    internal fun markJsReady() {
        benchmarkJsReady = SystemClock.elapsedRealtime()
    }

    internal fun reportFirstFrame() {
        val firstFrame = SystemClock.elapsedRealtime()

        if (benchmarkFirstFrameReported) return
        benchmarkFirstFrameReported = true

        mainHandler?.post {
            if (benchmarkJsReady == 0L) benchmarkJsReady = benchmarkAddViewEnd

            val fetchTime = if (benchmarkFromCache) 0 else benchmarkFetchEnd - benchmarkFetchStart
            val prepFrom = if (benchmarkFromCache) benchmarkLoadStart else benchmarkFetchEnd
            val prepTime = benchmarkRenderStart - prepFrom
            val engineTime = benchmarkEngineEnd - benchmarkRenderStart
            val addViewTime = benchmarkAddViewEnd - benchmarkEngineEnd
            val jsStartupTime = benchmarkJsReady - benchmarkAddViewEnd
            val paintTime = firstFrame - benchmarkJsReady
            val totalTime = firstFrame - benchmarkLoadStart

            Log.i("Ad Benchmark", """
                🎬 Ad Render Cycle (Unit: $adUnitId, cached: $benchmarkFromCache):
                - Creative Fetch:            ${fetchTime}ms
                - Payload Prep + Layout:     ${prepTime}ms
                - WebView Engine Init:       ${engineTime}ms
                - Add to View Hierarchy:     ${addViewTime}ms
                - JS Runtime Startup:        ${jsStartupTime}ms
                - Media Decode + Paint:      ${paintTime}ms
                ------------------------------------
                - Total Time to First Frame: ${totalTime}ms
            """.trimIndent())
        }
    }

    // ---- Loading and rendering ----

    private fun startAdLoad() {
        val adgeist = getInstance()
        val fetchCreative: FetchCreative = adgeist.getCreative()

        benchmarkFetchStart = SystemClock.elapsedRealtime()

        fetchCreative.fetchCreative(
            adUnitId, "FIXED"
        ) { result ->
            benchmarkFetchEnd = SystemClock.elapsedRealtime()

            mainHandler?.post {
                isLoading = false
                if (isDestroyed) return@post

                if (!result.isSuccess) {
                    Log.e(TAG, "API error: ${result.errorMessage}, statusCode: ${result.statusCode}")
                    listener?.onAdFailedToLoad(result.errorMessage)
                    return@post
                }

                try {
                    val campaignDetails = result.data as FixedAdResponse
                    prefetchCreativeMedia(campaignDetails)

                    val payload = AdCreativePayload.build(
                        response = campaignDetails,
                        adUnitId = adUnitId,
                        adType = adType,
                        adIsResponsive = adIsResponsive,
                        adSize = adSize,
                        measuredWidthDp = pxToDp(measuredWidth),
                        measuredHeightDp = pxToDp(measuredHeight),
                    )

                    when (payload) {
                        is AdCreativePayload.Result.Failure -> {
                            listener?.onAdFailedToLoad(payload.message)
                        }

                        is AdCreativePayload.Result.Success -> {
                            metaData = payload.metaData

                            val retainedAd = RetainedAd(campaignDetails)
                            tracking = retainedAd.tracking
                            adViewModel?.retain(adUnitId, retainedAd)

                            renderAdWithAdCard(payload.creativeJson)
                        }
                    }
                } catch (err: Exception) {
                    Log.e(TAG, "Parsing error: ${err.message}", err)
                    listener?.onAdFailedToLoad(err.message ?: "Error")
                }
            }
        }
    }

    private fun prefetchCreativeMedia(response: FixedAdResponse) {
        val urls = mutableListOf<String?>()
        response.creativesV1.firstOrNull()?.let { creative ->
            creative.primary?.let { media ->
                urls.add(media.fileUrl)
                urls.add(media.thumbnailUrl)
            }
            creative.companions?.forEach { companion ->
                urls.add(companion.fileUrl)
                urls.add(companion.thumbnailUrl)
            }
        }
        CreativeMediaCache.prefetch(context, urls)
    }

    /** Creates the WebView, wires the JS bridge, and renders the creative. */
    private fun renderAdWithAdCard(creativeJsonData: String) {
        if (isDestroyed) return

        benchmarkRenderStart = SystemClock.elapsedRealtime()
        removeAllViews()

        val bridge = JsBridge(this, context)
        jsInterface = bridge
        listener?.onAdOpened()

        val created = AdWebViewFactory.create(context, bridge)
        benchmarkEngineEnd = SystemClock.elapsedRealtime()

        val adWebView = created.webView
        webView = adWebView

        // Inject JS signal at the end of the HTML to detect JS Runtime ready
        val benchmarkScript = "<script>Android.postMessage(JSON.stringify({type:'BENCHMARK', message:'JS_READY'}));</script>"
        val htmlContent = AdCardHtml.build(context.assets, creativeJsonData) + benchmarkScript

        adWebView.loadDataWithBaseURL(
            "https://adgeist.ai",
            htmlContent,
            "text/html",
            "UTF-8",
            null
        )

        addView(adWebView, AdWebViewFactory.matchParentLayoutParams())
        benchmarkAddViewEnd = SystemClock.elapsedRealtime()

        // Companion ads stay hidden until the overflow check completes
        if (adType == AdType.COMPANION) {
            adWebView.visibility = View.INVISIBLE
        }
    }

    // ---- Measurement and layout ----

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        var width: Int
        var height: Int

        // 1. Calculate desired dimensions based on ad settings
        val size = adSize
        if (adIsResponsive) {
            width = widthSize
            height = heightSize
        } else if (size != null) {
            width = size.getWidthInPixels(context)
            height = size.getHeightInPixels(context)
        } else {
            width = 0
            height = 0
        }

        // 2. Respect minimum sizes (from XML or background)
        width = max(width.toDouble(), suggestedMinimumWidth.toDouble()).toInt()
        height = max(height.toDouble(), suggestedMinimumHeight.toDouble()).toInt()

        // 3. Resolve against parent constraints
        val resolvedWidth = resolveSize(width, widthMeasureSpec)
        val resolvedHeight = resolveSize(height, heightMeasureSpec)

        // 4. Force the child (WebView) to fill this view's resolved size
        val child = getChildAt(0)
        if (child != null && child.visibility != GONE) {
            val childWidthSpec = MeasureSpec.makeMeasureSpec(resolvedWidth, MeasureSpec.EXACTLY)
            val childHeightSpec = MeasureSpec.makeMeasureSpec(resolvedHeight, MeasureSpec.EXACTLY)
            child.measure(childWidthSpec, childHeightSpec)
        }

        setMeasuredDimension(resolvedWidth, resolvedHeight)
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
        super.onDetachedFromWindow()
        mainHandler?.post {
            if (isAttachedToWindow) return@post

            destroyInternal()
            releaseImeSession(mainHandler)
        }
    }
}
