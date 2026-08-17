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
import com.adgeistkit.AdgeistCore.Companion.getInstance
import com.adgeistkit.R
import com.adgeistkit.request.AdRequest
import com.adgeistkit.data.models.FixedAdResponse
import kotlin.math.max
import com.adgeistkit.ads.cache.CreativeCache
import com.adgeistkit.ads.host.pxToDp
import com.adgeistkit.ads.host.releaseImeSession
import com.adgeistkit.ads.render.AdCardHtml
import com.adgeistkit.ads.render.AdCreativePayload
import com.adgeistkit.ads.render.AdWebViewFactory
import com.adgeistkit.ads.render.AdWebViewTeardown
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

    // Benchmarking
    private var benchmarkStartTime: Long = 0
    private var benchmarkEngineEnd: Long = 0
    private var benchmarkAddedEnd: Long = 0
    private var benchmarkTeardownStartTime: Long = 0

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
            Log.w(TAG, "loadAd ignored - ad is already loading")
            return
        }

        performLoad(adRequest)
    }

    private fun performLoad(adRequest: AdRequest) {
        isLoading = true

        if (webView != null) {
            safelyDestroyWebView()
        }

        mainHandler?.post {
            isDestroyed = false
            startAdLoad(adRequest)
        }
    }

    fun destroyAd() {
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

    fun removeFromParent() {
        try {
            (parent as? ViewGroup)?.removeView(this)
        } catch (e: Exception) {
            //
        }
    }

    private fun safelyDestroyWebView() {
        if (isDestroyed) return
        benchmarkTeardownStartTime = SystemClock.elapsedRealtime()
        isDestroyed = true

        val webViewToDestroy = webView
        webView = null
        jsInterface?.destroyListeners()
        jsInterface = null

        if (webViewToDestroy == null) return


        val handler = mainHandler ?: return
        handler.post {
            AdWebViewTeardown.destroy(webViewToDestroy, handler) {
                val duration = SystemClock.elapsedRealtime() - benchmarkTeardownStartTime
                Log.i("Ad Benchmark Teardown", "Ad Benchmark Teardown: WebView Teardown (Unit: $adUnitId): ${duration}ms (inc. grace period)")
            }
            removeAllViews()
        }
    }

    // ---- Benchmarking ----

    internal fun reportJsReady() {
        val jsReadyTime = SystemClock.elapsedRealtime()
        val totalTime = jsReadyTime - benchmarkStartTime
        val engineTime = benchmarkEngineEnd - benchmarkStartTime
        val addViewTime = benchmarkAddedEnd - benchmarkEngineEnd
        val jsStartupTime = jsReadyTime - benchmarkAddedEnd

        Log.i("Ad Benchmark Load", """
            🚀 Ad Load Benchmark (Unit: $adUnitId):
            - Engine Start (WebView Init): ${engineTime}ms
            - Add to View Hierarchy: ${addViewTime}ms
            - JS Runtime Startup: ${jsStartupTime}ms
            -----------------------------------
            - Total Ready Time: ${totalTime}ms
        """.trimIndent())
    }

    // ---- Loading and rendering ----

    private fun startAdLoad(adRequest: AdRequest) {
        val adgeist = getInstance()
        val fetchCreative: FetchCreative = adgeist.getCreative()

        fetchCreative.fetchCreative(
            adUnitId, "FIXED"
        ) { result ->
            mainHandler?.post {
                isLoading = false
                if (isDestroyed) return@post

                if (!result.isSuccess) {
                    Log.e(TAG, "API error: ${result.errorMessage}, statusCode: ${result.statusCode}")
                    listener?.onAdFailedToLoad(result.errorMessage)
                    return@post
                }

                try {
                    // Parse the static JSON
                    val campaignDetails = result.data as FixedAdResponse

                    // Downloads start here, in parallel with WebView creation and HTML
                    // assembly, so the media is often already on disk when the page asks
                    // for it. Never blocks this thread.
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
                            Log.e(TAG, "Creative payload rejected: ${payload.message}")
                            listener?.onAdFailedToLoad(payload.message)
                        }

                        is AdCreativePayload.Result.Success -> {
                            metaData = payload.metaData
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

    /**
     * Hands every media URL in the response to [CreativeCache], which downloads them
     * to the app's cache directory. The page keeps requesting the original URLs; the
     * WebView's request interceptor answers them from disk.
     */
    private fun prefetchCreativeMedia(response: FixedAdResponse) {
        val urls = mutableListOf<String?>()
        response.creativesV1.forEach { creative ->
            creative.primary?.let { media ->
                urls.add(media.fileUrl)
                urls.add(media.thumbnailUrl)
            }
            creative.companions?.forEach { companion ->
                urls.add(companion.fileUrl)
                urls.add(companion.thumbnailUrl)
            }
        }
        CreativeCache.prefetch(context, urls)
    }

    /** Creates the WebView, wires the JS bridge, and renders the creative. */
    private fun renderAdWithAdCard(creativeJsonData: String) {
        if (isDestroyed) return

        benchmarkStartTime = SystemClock.elapsedRealtime()
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
        benchmarkAddedEnd = SystemClock.elapsedRealtime()

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
            Log.d(TAG, "Ad child measured $resolvedWidth $resolvedHeight")
        }

        Log.d(TAG, "onMeasure - resolvedWidth: $resolvedWidth, resolvedHeight: $resolvedHeight")
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

        if (isDestroyed) return

        try {
            webView?.onResume()
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming WebView: ${e.message}", e)
        }
        jsInterface?.onHostAttached()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)

        if (webView == null || isDestroyed) return

        if (visibility == VISIBLE) {
            try {
                webView?.onResume()
            } catch (e: Exception) {
                Log.e(TAG, "Error resuming WebView: ${e.message}", e)
            }
        } else {
            try {
                webView?.onPause()
            } catch (e: Exception) {
                Log.e(TAG, "Error pausing WebView: ${e.message}", e)
            }
            releaseImeSession(mainHandler)
        }
    }

    override fun onDetachedFromWindow() {
        if (!isDestroyed) {
            jsInterface?.onHostDetached()
            try {
                webView?.onPause()
            } catch (e: Exception) {
                //
            }
        }
        super.onDetachedFromWindow()
        mainHandler?.post {
            destroyInternal()
            releaseImeSession(mainHandler)
        }
    }
}
