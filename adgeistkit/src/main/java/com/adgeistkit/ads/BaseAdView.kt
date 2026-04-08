package com.adgeistkit.ads

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.ConsoleMessage.MessageLevel
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresPermission
import com.adgeistkit.AdgeistCore.Companion.getInstance
import com.adgeistkit.R
import com.adgeistkit.request.AdRequest
import com.adgeistkit.data.models.FixedAdResponse
import com.adgeistkit.data.network.FetchCreative
import com.adgeistkit.logging.SdkShield
import com.google.gson.Gson
import kotlin.math.max

open class BaseAdView : ViewGroup {
    companion object {
        private const val TAG = "BaseAdView"
    }

    /**
     * Required parameters for ad rendering configuration
     */
    var adSize: AdSize? = null
    var adUnitId: String = ""
    var adType: AdType = AdType.BANNER
    var adIsResponsive: Boolean = false
    var isTestMode: Boolean = false

    /**
     * Metadata and media type for ad tracking
     */
    var metaData: String = ""
    var mediaType: String? = null

    /**
     * WebView and JavaScript bridge instances
     */
    internal var webView: WebView? = null
    private var jsInterface: JsBridge? = null

    /**
     * Listener for ad lifecycle events
     */
    var listener: AdListener? = null

    private var isLoading: Boolean = false
    private var isDestroyed = false
    private var mainHandler: Handler? = null

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

    /**
     * Initializes the BaseAdView with context and attributes.
     * Sets up the main thread handler and parses XML attributes if provided.
     *
     * @param context The Android context
     * @param attrs AttributeSet from XML layout (nullable)
     */
    private fun initialize(context: Context, attrs: AttributeSet?) {
        mainHandler = Handler(Looper.getMainLooper())

        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.AdView)
            try {
                // Parse adUnitId from XML
                val xmlAdUnitId = typedArray.getString(R.styleable.AdView_adUnitId)
                if (xmlAdUnitId != null && !xmlAdUnitId.isEmpty()) {
                    adUnitId = xmlAdUnitId
                }
            } finally {
                typedArray.recycle()
            }
        }
    }

    /**
     * Sets the ad listener to receive ad lifecycle events.
     *
     * @param listener AdListener implementation or null to remove listener
     */
    fun setAdListener(listener: AdListener?) {
        this.listener = listener
    }

    /**
     * Converts density-independent pixels (dp) to actual pixels.
     *
     * @param dp Value in density-independent pixels
     * @return Value in pixels based on device density
     */
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    /**
     * Converts pixels to density-independent pixels (dp).
     *
     * @param px Value in pixels
     * @return Value in density-independent pixels
     */
    private fun pxToDp(px: Int): Int {
        return (px / resources.displayMetrics.density).toInt()
    }

    /**
     * Loads an ad with the specified AdRequest.
     * This is the main entry point for publishers to request and display ads.
     * Note: If an ad is already loading, this call will be ignored.
     *
     * @param adRequest The AdRequest containing ad configuration (test mode, etc.)
     * @throws SecurityException if INTERNET permission is not granted
     */
    @RequiresPermission("android.permission.INTERNET")
    fun loadAd(adRequest: AdRequest) {
        SdkShield.runSafely("BaseAdView.loadAd") {
            if (isLoading) {
                Log.w(TAG, "loadAd ignored - ad is already loading")
                return@runSafely
            }

            if (adUnitId == null || adUnitId.isEmpty()) {
                Log.e(TAG, "Ad unit ID is null or empty")
                listener?.onAdFailedToLoad("Ad unit ID is null or empty")
                return@runSafely
            }

            // Reset destroyed flag to allow reloading
            isDestroyed = false
            isLoading = true

            // Destroy any existing WebView before loading new ad
            if (webView != null) {
                safelyDestroyWebView()
            }

            // Wait a bit before loading new ad to ensure cleanup completes
            mainHandler?.postDelayed({
                if (!isDestroyed) {
                    startAdLoad(adRequest)
                }
            }, 400)
        }
    }

    /**
     * Initiates the actual ad loading process by fetching creative from the server.
     *
     * @param adRequest The AdRequest containing configuration parameters
     */
    private fun startAdLoad(adRequest: AdRequest) {
        try {
            val adgeist = getInstance()
            val fetchCreative: FetchCreative = adgeist.getCreative()

            isTestMode = adRequest.isTestMode

            fetchCreative.fetchCreative(
                adUnitId, "FIXED", isTestMode
            ) { result ->
                mainHandler?.post {
                    if (isDestroyed) return@post
                    isLoading = false

                    if (!result.isSuccess) {
                        Log.e(TAG, "API error: ${result.errorMessage}, statusCode: ${result.statusCode}")
                        listener?.onAdFailedToLoad(result.errorMessage)
                        return@post
                    }

                    try {
                        val campaignDetails = result.data as FixedAdResponse

                        if (campaignDetails.creativesV1.isNullOrEmpty()) {
                            Log.e(TAG, "Empty creative list")
                            listener?.onAdFailedToLoad("Empty creative")
                            return@post
                        }

                        metaData = campaignDetails.metaData
                        val propertiesForAdCard = mutableMapOf<String, Any?>()

                        propertiesForAdCard["adspaceType"] = adType.value
                        propertiesForAdCard["adElementId"] = "adgeist_ads_iframe_$adUnitId"
                        propertiesForAdCard["name"] = campaignDetails.advertiser?.name ?: "-"

                        val options = campaignDetails.displayOptions

                        propertiesForAdCard["isResponsive"] = options?.isResponsive ?: false
                        propertiesForAdCard["responsiveType"] = options?.responsiveType ?: "Square"

                        val creativeDataFromApiResponse = campaignDetails.creativesV1[0]

                        propertiesForAdCard["title"] = creativeDataFromApiResponse.title
                        propertiesForAdCard["description"] = creativeDataFromApiResponse.description
                        propertiesForAdCard["ctaUrl"] = creativeDataFromApiResponse.ctaUrl

                        Log.d(TAG, "measuredWidth: ${pxToDp(measuredWidth)}, measuredHeight: ${pxToDp(measuredHeight)}")
                        if (adIsResponsive) {
                            propertiesForAdCard["width"] = pxToDp(measuredWidth)
                            propertiesForAdCard["height"] = pxToDp(measuredHeight)
                        } else {
                            propertiesForAdCard["width"] = adSize!!.width
                            propertiesForAdCard["height"] = adSize!!.height
                        }

                        // Add primaryCreative
                        val primaryCreative = mutableMapOf<String, String?>()
                        primaryCreative["src"] = creativeDataFromApiResponse.primary?.fileUrl
                        primaryCreative["thumbnailUrl"] = creativeDataFromApiResponse.primary?.thumbnailUrl
                        primaryCreative["type"] = creativeDataFromApiResponse.primary?.type

                        // Add companionCreative
                        val companionCreative = creativeDataFromApiResponse.companions?.map { companion ->
                            mapOf(
                                "src" to companion.fileUrl,
                                "thumbnailUrl" to companion.thumbnailUrl,
                                "type" to companion.type
                            )
                        } ?: emptyList()

                        val mediaList = mutableListOf<Map<String, String?>>()
                        mediaList.add(primaryCreative)
                        mediaList.addAll(companionCreative)
                        propertiesForAdCard["media"] = mediaList

                        val creativeJson = Gson().toJson(propertiesForAdCard)
                        renderAdWithAdCard(creativeJson)
                    } catch (err: Exception) {
                        Log.e(TAG, "Parsing error: ${err.message}", err)
                        listener?.onAdFailedToLoad(err.message ?: "Error")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "startAdLoad exception: ${e.message}", e)
            listener?.onAdFailedToLoad(e.message ?: "Unknown error")
            mainHandler?.post {
                isLoading = false
            }
        }
    }

    /**
     * Creates and configures a new WebView, sets up JavaScript bridge,
     *
     * @param creativeJsonData JSON string containing creative data for rendering
     */
    private fun renderAdWithAdCard(creativeJsonData: String) {
        if (isDestroyed) return

        removeAllViews()

        webView = WebView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
        }

        jsInterface = JsBridge(this, context)
        listener?.onAdOpened()

        // Enable WebView debugging (you can inspect in Chrome DevTools)
        // chrome://inspect/#devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView!!.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return SdkShield.runSafelyWithReturn("BaseAdView.shouldOverrideUrlLoading", false) {
                    openInBrowser(context, url)
                    jsInterface!!.recordClickListener()
                    true
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                return SdkShield.runSafelyWithReturn("BaseAdView.shouldOverrideUrlLoading", false) {
                    val url = request.url.toString()
                    openInBrowser(context, url)
                    jsInterface!!.recordClickListener()
                    true
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                Log.i(TAG, "✅ WebView page finished loading: $url")
            }

            override fun onLoadResource(view: WebView, url: String) {
                super.onLoadResource(view, url)
                Log.d(TAG, "📦 Loading resource: $url")
            }
        }

        // Set WebChromeClient to capture console logs
        webView!!.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                val logLevel = consoleMessage.messageLevel().name
                val message = consoleMessage.message()
                val source = consoleMessage.sourceId()
                val line = consoleMessage.lineNumber()

                val fullLog = String.format("[%s] %s (%s:%d)", logLevel, message, source, line)
                when (consoleMessage.messageLevel()) {
                    MessageLevel.ERROR -> Log.e(TAG, "JS Error: $fullLog")
                    MessageLevel.WARNING -> Log.w(TAG, "JS Warning: $fullLog")
                    else -> Log.d(TAG,"🔵 JS Log: $fullLog")
                }
                return true
            }
        }

        // JavaScript bridge interface accessible as 'Android' from WebView HTML
        webView!!.addJavascriptInterface(jsInterface!!, "Android")

        val htmlContent = buildAdCardHtml(creativeJsonData)
        webView!!.loadDataWithBaseURL(
            "https://adgeist.ai",
            htmlContent,
            "text/html",
            "UTF-8",
            null
        )

        // Add WebView to container with full dimensions
        addView(
            webView, LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
            )
        )

        // Hide companion ads initially until overflow check completes
        if (adType == AdType.COMPANION) {
            webView!!.visibility = View.INVISIBLE
        }
    }

    /**
     * Builds the HTML content for ad rendering in WebView.
     * Loads the main ad view file from assets and injects creative data.
     * This file loads the AdCard library from S3 and renders the ad.
     *
     * @param creativeJsonData JSON string with creative data
     * @return Complete HTML string ready to be loaded in WebView
     */
    private fun buildAdCardHtml(creativeJsonData: String): String {
        val escapedJson = creativeJsonData
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("`", "\\`")

        return try {
            val template = context.assets.open("ad_view.html").bufferedReader().use { it.readText() }
            template.replace("{{CREATIVE_DATA}}", escapedJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ad view from assets", e)
            return ""
        }
    }

    /**
     * Positions the child view (WebView) within the ad container.
     * Centers the ad content both horizontally and vertically.
     *
     * @param changed True if this is a different layout than the previous one
     * @param left Left position relative to parent
     * @param top Top position relative to parent
     * @param right Right position relative to parent
     * @param bottom Bottom position relative to parent
     */
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

    /**
     * Measures the dimensions of the ad view based on ad size or child view.
     * Handles responsive sizing and fixed ad sizes.
     *
     * @param widthMeasureSpec Width measurement specification from parent
     * @param heightMeasureSpec Height measurement specification from parent
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val child = getChildAt(0)
        var width: Int
        var height: Int

        if (child != null && child.visibility != GONE) {
            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            width = child.measuredWidth
            height = child.measuredHeight
        } else {
            if (adIsResponsive) {
                Log.d(TAG, "Ad is responsive - using available space for measurement")
                width = android.view.View.MeasureSpec.getSize(widthMeasureSpec)
                height = android.view.View.MeasureSpec.getSize(heightMeasureSpec)
            } else if (adSize != null) {
                width = adSize!!.getWidthInPixels(context)
                height = adSize!!.getHeightInPixels(context)
            } else {
                width = 0
                height = 0
            }
        }

        width = max(width.toDouble(), suggestedMinimumWidth.toDouble()).toInt()
        height = max(height.toDouble(), suggestedMinimumHeight.toDouble()).toInt()

        Log.d(TAG, "onMeasure - width: ${resolveSize(width, widthMeasureSpec)}, height: ${resolveSize(height, heightMeasureSpec)}")
        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec)
        )
    }

    /**
     * Opens a URL in the device's default browser.
     *
     * @param context Android context for launching intent
     * @param url URL to open in browser
     */
    private fun openInBrowser(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Failed to open external URL: $url", e
            )
        }
    }

    /**
     * Called when the view is attached to a window.
     * Resumes WebView rendering if available.
     */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        SdkShield.runSafely("BaseAdView.onAttachedToWindow") {
            if (webView != null && !isDestroyed) {
                webView!!.onResume()
            }
        }
    }

    /**
     * Called when window visibility changes.
     * Pauses/resumes WebView rendering to conserve resources.
     *
     * @param visibility New visibility state (VISIBLE, INVISIBLE, or GONE)
     */
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        SdkShield.runSafely("BaseAdView.onWindowVisibilityChanged") {
            if (webView == null || isDestroyed) return@runSafely

            if (visibility == VISIBLE) {
                webView!!.onResume()
            } else {
                webView!!.onPause()
            }
        }
    }

    /**
     * Called when the view is detached from a window.
     * Triggers WebView cleanup and notifies listener.
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        SdkShield.runSafely("BaseAdView.onDetachedFromWindow") {
            onDestroyWebView()
        }
    }

    /**
     * Sets the ad dimensions for fixed-size ads.
     *
     * @param adSize The desired ad size
     * @throws IllegalArgumentException if adSize is null
     */
    fun setAdDimension(adSize: AdSize) {
        requireNotNull(adSize) { "AdSize cannot be null" }
        this.adSize = adSize
        requestLayout()
    }

    val isCollapsible: Boolean
        get() = false

    /**
     * Destroys the ad view and releases all resources.
     */
    fun destroy() {
        isLoading = false
        safelyDestroyWebView()
    }

    /**
     * Removes this view from its parent ViewGroup.
     */
    fun removeFromParent() {
        try {
            (parent as? ViewGroup)?.removeView(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing from parent: ${e.message}", e)
        }
    }

    /**
     * Safely destroys the WebView to prevent memory leaks.
     * Performs cleanup in the correct order:
     * 1. Removes JavaScript interface
     * 2. Stops loading and pauses rendering
     * 3. Clears history and cache
     * 4. Removes from view hierarchy
     * 5. Loads blank page
     * 6. Destroys WebView instance
     */
    private fun safelyDestroyWebView() {
        if (isDestroyed) return
        isDestroyed = true

        val webViewToDestroy = webView
        webView = null
        jsInterface?.destroyListeners()
        jsInterface = null

        if (webViewToDestroy == null) return

        mainHandler?.post {
            try {
                try {
                    webViewToDestroy.removeJavascriptInterface("Android")
                } catch (e: Exception) { /* ignore */
                }

                webViewToDestroy.stopLoading()
                webViewToDestroy.onPause()
                webViewToDestroy.clearHistory()
                webViewToDestroy.clearCache(true)
                (webViewToDestroy.parent as? ViewGroup)?.removeView(webViewToDestroy)
                removeAllViews()

                try {
                    webViewToDestroy.loadUrl("about:blank")
                } catch (e: Exception) { /* ignore */
                }

                mainHandler?.postDelayed({
                    try {
                        webViewToDestroy.destroy()
                    } catch (e: Exception) {
                        Log.e(TAG, "WebView final destroy failed", e)
                    }
                }, 600)
            } catch (e: Exception) {
                Log.e(TAG, "WebView cleanup error", e)
            }
        }
    }

    /**
     * Internal method called when WebView is being destroyed.
     */
    private fun onDestroyWebView() {
        listener?.onAdClosed()
        safelyDestroyWebView()
    }
}
