package com.adgeistkit.ads

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
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
import com.adgeistkit.ads.network.AdRequest
import com.adgeistkit.data.models.FixedAdResponse
import com.adgeistkit.data.network.FetchCreative
import com.google.gson.Gson
import kotlin.math.max

open class BaseAdView : ViewGroup {
    companion object {
        private const val TAG = "BaseAdView"
    }

    var adSize: AdSize? = null
    var adUnitId: String = ""
    var adType: String = "banner"
    var customOrigin: String? = null
    var appId: String? = null

    var mediaType: String? = null

    var isTestMode: Boolean = false
    var metaData: String = ""

    private var webView: WebView? = null
    private var jsInterface: JsBridge? = null

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

                // Parse adSize from XML
                val sizeIndex = typedArray.getInt(R.styleable.AdView_adSize, -1)
                adSize = getAdSizeFromIndex(sizeIndex)
            } finally {
                typedArray.recycle()
            }
        }

        if (adSize == null) {
            adSize = AdSize.BANNER
        }
    }

    fun setAdListener(listener: AdListener?) {
        this.listener = listener
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun pxToDp(px: Int): Int {
        return (px / resources.displayMetrics.density).toInt()
    }

    private fun getAdSizeFromIndex(index: Int): AdSize {
        return when (index) {
            0 -> AdSize.BANNER
            1 -> AdSize.LARGE_BANNER
            2 -> AdSize.MEDIUM_RECTANGLE
            3 -> AdSize.FULL_BANNER
            4 -> AdSize.LEADERBOARD
            5 -> AdSize.WIDE_SKYSCRAPER
            else -> AdSize.BANNER
        }
    }

    private fun getMetaValue(key: String): String? {
        try {
            val context = context

            val ai = context.packageManager
                .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)

            val bundle = ai.metaData
            return bundle?.getString(key)
        } catch (e: Exception) {
            Log.e("BaseAdView", "Meta-data read failed for: $key", e)
            return null
        }
    }

    @RequiresPermission("android.permission.INTERNET")
    fun loadAd(adRequest: AdRequest) {
        if (isLoading) {
            Log.w(TAG, "Ad is already loading")
            return
        }

        if (adUnitId == null || adUnitId.isEmpty()) {
            Log.e(TAG, "Ad unit ID is null or empty")
            return
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

    private fun startAdLoad(adRequest: AdRequest) {
        try {
            val adgeist = getInstance()
            val fetchCreative: FetchCreative = adgeist.getCreative()

            val apiKey = getMetaValue("com.adgeistkit.ads.API_KEY") ?: ""
            val origin = customOrigin ?: getMetaValue("com.adgeistkit.ads.ORIGIN") ?: ""
            val publisherId = appId ?: getMetaValue("com.adgeistkit.ads.APP_ID") ?: ""
            val packageName = context.packageName

            isTestMode = adRequest.isTestMode

            fetchCreative.fetchCreative(
                apiKey, origin, adUnitId, publisherId, "FIXED", isTestMode
            ) { creativeData ->

                mainHandler?.post {

                    if (isDestroyed) return@post
                    isLoading = false

                    if (creativeData == null) {
                        listener?.onAdFailedToLoad("No creative returned")
                        return@post
                    }

                    try {
                        val fixed = creativeData as FixedAdResponse

                        if (fixed.creatives.isNullOrEmpty()) {
                            listener?.onAdFailedToLoad("Empty creative")
                            return@post
                        }

                        val c = fixed.creatives[0]
                        mediaType = c.type
                        metaData = fixed.metaData

                        val simpleCreative = mutableMapOf<String, Any?>()
                        simpleCreative["adElementId"] = "adgeist_ads_iframe_$adUnitId"
                        simpleCreative["title"] = c.title
                        simpleCreative["description"] = c.description
                        simpleCreative["name"] = fixed.advertiser?.name ?: "-"
                        simpleCreative["ctaUrl"] = c.ctaUrl
                        simpleCreative["fileUrl"] = c.fileUrl
                        simpleCreative["type"] = c.type

                        val options = fixed.displayOptions

                        simpleCreative["isResponsive"] = options?.isResponsive ?: false
                        simpleCreative["responsiveType"] = options?.responsiveType ?: "Square"
                        simpleCreative["width"] = adSize!!.width ?: 300
                        simpleCreative["height"] = adSize!!.height ?: 300
                        simpleCreative["adspaceType"] = adType

                        val mediaList = mutableListOf<Map<String, String?>>()
                        if (c.fileUrl != null) {
                            mediaList.add(mapOf("src" to c.fileUrl))
                        }
                        simpleCreative["media"] = mediaList
                        simpleCreative["mediaType"] = c.type ?: "image"

                        val creativeJson = Gson().toJson(simpleCreative)

                        renderAdWithAdCard(creativeJson)
                        notifyAdLoaded()

                    } catch (err: Exception) {
                        Log.e(TAG, "Error parsing creativeData", err)
                        listener?.onAdFailedToLoad(err.message ?: "Error")
                    }
                }

                null
            }

        } catch (e: Exception) {
            listener?.onAdFailedToLoad(e.message ?: "Unknown error")
            mainHandler?.post {
                isLoading = false
                Log.e(
                    TAG,
                    "Error loading ad",
                    e
                )
            }
        }
    }

    private fun renderAdWithAdCard(creativeJsonData: String) {
        if (isDestroyed) return

        removeAllViews()
        webView = WebView(context)
        webView!!.settings.javaScriptEnabled = true
        webView!!.settings.domStorageEnabled = true
        webView!!.settings.loadWithOverviewMode = true
        webView!!.settings.useWideViewPort = true

        jsInterface = JsBridge(this, context)
        listener?.onAdOpened()

        // Enable WebView debugging (you can inspect in Chrome DevTools)
        // chrome://inspect/#devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView!!.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                openInBrowser(context, url)
                jsInterface!!.recordClickListener()
                return true
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                openInBrowser(context, url)
                jsInterface!!.recordClickListener()
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                Log.i(
                    TAG,
                    "✅ WebView page finished loading: $url"
                )
            }

            override fun onLoadResource(view: WebView, url: String) {
                super.onLoadResource(view, url)
                Log.d(
                    TAG,
                    "📦 Loading resource: $url"
                )
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
                    MessageLevel.ERROR -> {
                        Log.e(
                            TAG,
                            "🔴 JS Error: $fullLog"
                        )
                        listener?.onAdFailedToLoad(fullLog)
                    }

                    MessageLevel.WARNING -> Log.w(
                        TAG,
                        "🟡 JS Warning: $fullLog"
                    )

                    MessageLevel.DEBUG, MessageLevel.LOG, MessageLevel.TIP -> Log.d(
                        TAG,
                        "🔵 JS Log: $fullLog"
                    )

                    else -> Log.d(
                        TAG,
                        "🔵 JS Log: $fullLog"
                    )
                }
                return true
            }
        }


        webView!!.addJavascriptInterface(jsInterface!!, "Android")

        val htmlContent = buildAdCardHtml(creativeJsonData)
        webView!!.loadDataWithBaseURL(
            "https://adgeist.ai",
            htmlContent,
            "text/html",
            "UTF-8",
            null
        )
        addView(
            webView, LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun buildAdCardHtml(creativeJsonData: String): String {
        val escapedJson = creativeJsonData
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "    <meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'>" +
                "    <style>" +
                "        * {" +
                "            margin: 0;" +
                "            padding: 0;" +
                "            box-sizing: border-box;" +
                "        }" +
                "        html, body {" +
                "            width: 100%;" +
                "            height: 100%;" +
                "            margin: 0;" +
                "            padding: 0;" +
                "            overflow: hidden;" +
                "        }" +
                "        body {" +
                "            display: flex;" +
                "            justify-content: center;" +
                "            align-items: center;" +
                "        }" +
                "        #ad-content {" +
                "            width: 100%;" +
                "            height: 100%;" +
                "        }" +
                "        #ad-content > * {" +
                "            width: 100%;" +
                "            height: 100%;" +
                "            object-fit: inherit;" +
                "        }" +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <div id='ad-content'></div>" +
                "    <!-- Load AdCard.js library from S3 -->" +
                "    <script src='https://cdn.adgeist.ai/adcard-beta.js'></script>" +
                "    <script>" +
                "        function initAd() {" +
                "            if (typeof AdCard === 'undefined') {" +
                "                console.error('AdCard library not loaded');" +
                "                return;" +
                "            }" +
                "            try {" +
                "                const creativeData = JSON.parse(\"" + escapedJson + "\");" +
                "                const adCard = new AdCard(creativeData);" +
                "                const html = adCard.renderHtml();" +
                "                document.getElementById('ad-content').innerHTML = html;" +
                "                document.getElementById('ad-content').addEventListener('click', function(e) {" +
                "                    console.log('Ad clicked');" +
                "                });" +
                "            } catch (error) {" +
                "                console.error('Error rendering ad:', error);" +
                "            }" +
                "        }" +
                "        if (document.readyState === 'loading') {" +
                "            document.addEventListener('DOMContentLoaded', function() {" +
                "                setTimeout(initAd, 100);" +
                "            });" +
                "        } else {" +
                "            setTimeout(initAd, 100);" +
                "        }" +
                "    </script>" +
                "</body>" +
                "</html>"
    }

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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val child = getChildAt(0)
        var width: Int
        var height: Int

        if (child != null && child.visibility != GONE) {
            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            width = child.measuredWidth
            height = child.measuredHeight
        } else {
            if (adSize != null) {
                width = adSize!!.getWidthInPixels(context)
                height = adSize!!.getHeightInPixels(context)
            } else {
                width = 0
                height = 0
            }
        }

        width = max(width.toDouble(), suggestedMinimumWidth.toDouble()).toInt()
        height = max(height.toDouble(), suggestedMinimumHeight.toDouble()).toInt()

        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec)
        )
    }

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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (webView != null && !isDestroyed) {
            try {
                webView!!.onResume()
            } catch (ignored: Exception) {
            }
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (webView == null || isDestroyed) return

        if (visibility == VISIBLE) {
            try {
                webView!!.onResume()
            } catch (ignored: Exception) {
            }
        } else {
            try {
                webView!!.onPause()
            } catch (ignored: Exception) {
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        onDestroyWebView()
    }

    fun setAdDimension(adSize: AdSize) {
        requireNotNull(adSize) { "AdSize cannot be null" }
        this.adSize = adSize
        requestLayout()
    }

    val isCollapsible: Boolean
        get() = false

    fun destroy() {
        isLoading = false
        safelyDestroyWebView()
    }

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
                        Log.d(TAG, "WebView destroyed safely")
                    } catch (e: Exception) {
                        Log.e(TAG, "Final destroy failed (harmless)", e)
                    }
                }, 600)
            } catch (e: Exception) {
                Log.e(TAG, "Error during WebView cleanup", e)
            }
        }
    }

    private fun onDestroyWebView() {
        listener?.onAdClosed()
        safelyDestroyWebView()
    }

    private fun notifyAdLoaded() {
    }
}
