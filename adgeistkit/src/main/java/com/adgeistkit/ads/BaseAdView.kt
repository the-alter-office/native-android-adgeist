package com.adgeistkit.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
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
import android.view.inputmethod.InputMethodManager
import android.webkit.ConsoleMessage
import android.webkit.ConsoleMessage.MessageLevel
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresPermission
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.adgeistkit.AdgeistCore.Companion.getInstance
import com.adgeistkit.R
import com.adgeistkit.request.AdRequest
import com.adgeistkit.data.models.FixedAdResponse
import com.adgeistkit.data.network.FetchCreative
import com.google.gson.Gson
import kotlin.math.max

open class BaseAdView : ViewGroup {

    companion object {
        private const val TAG = "BaseAdView"
    }

    // Ad configuration
    var adSize: AdSize? = null
    var adUnitId: String = ""
    var adType: AdType = AdType.BANNER
    var adIsResponsive: Boolean = false
    var isTestMode: Boolean = false

    // Stable identity of this ad slot; sessions are resumed only by the same
    // placement. Auto-derived (view id / host fragment) when left empty.
    var placementId: String = ""

    /**
     * When false, the host-destroy watcher ignores fragment lifecycles and only
     * tears the ad down on activity destroy. Embedders whose fragments are
     * transient wrappers (react-native-screens recreates the fragment every
     * time a screen is covered) must disable this and drive teardown explicitly
     * (e.g. RN's onDropViewInstance).
     */
    var watchFragmentLifecycle: Boolean = true

    // Creative metadata used by tracking
    var metaData: String = ""
    var mediaType: String? = null

    var listener: AdListener? = null

    // Runtime state
    internal var webView: WebView? = null
    private var jsInterface: JsBridge? = null
    private var isLoading: Boolean = false
    private var isDestroyed = false
    private var mainHandler: Handler? = null

    // Store key of the session this view created/adopted, for cleanup
    private var activeSessionKey: String? = null

    // Host destroy watcher: tears the ad down when its screen is gone for good
    private var lifecycleObserver: DefaultLifecycleObserver? = null
    private var observedLifecycle: Lifecycle? = null
    private var activityCallbacks: Application.ActivityLifecycleCallbacks? = null
    private var observedApplication: Application? = null
    private var watchingFragment = false

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

                val xmlPlacementId = typedArray.getString(R.styleable.AdView_placementId)
                if (xmlPlacementId != null && !xmlPlacementId.isEmpty()) {
                    placementId = xmlPlacementId
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

    val isCollapsible: Boolean
        get() = false

    /**
     * Shows the ad for this unit: resumes this placement's live session when
     * one survives, otherwise fetches a fresh creative.
     */
    @RequiresPermission("android.permission.INTERNET")
    fun loadAd(adRequest: AdRequest) {
        if (isLoading) {
            Log.w(TAG, "loadAd ignored - ad is already loading")
            return
        }

        if (adUnitId == null || adUnitId.isEmpty()) {
            Log.e(TAG, "Ad unit ID is null or empty")
            listener?.onAdFailedToLoad("Ad unit ID is null or empty")
            return
        }

        val key = sessionKey()
        val session = key?.let { AdSessionStore.get(it) }
        if (key != null && session != null) {
            val sameActivity =
                session.hostActivity != null && session.hostActivity === findActivity(context)
            // Steal guard: never rip the ad out of another visible slot
            val hostStillVisible =
                session.hostView !== this && session.hostView?.isAttachedToWindow == true

            if (sameActivity && !hostStillVisible) {
                if (session.hostView === this && webView != null) {
                    Log.d(TAG, "loadAd ignored - this view is already presenting the live ad")
                    return
                }
                adoptSession(key, session)
                return
            }

            if (!sameActivity) {
                // A session's WebView cannot be shown in another activity
                Log.d(TAG, "Discarding ad session from a different activity")
                AdSessionStore.remove(key)
                session.hostView?.destroy()
            } else {
                Log.d(TAG, "Session '$key' is visible in another slot - fetching a new ad instead")
            }
        }

        isLoading = true

        // The destroyed flag is reset inside the delayed block: cleanup sets
        // it, so resetting earlier would be undone and the load would never run.
        val needsCleanup = webView != null
        if (needsCleanup) {
            safelyDestroyWebView()
        }

        mainHandler?.postDelayed({
            isDestroyed = false
            startAdLoad(adRequest)
        }, if (needsCleanup) 400 else 0)
    }

    /**
     * Permanently tears the ad down. Also invoked automatically when the host
     * screen is popped or the activity is destroyed.
     */
    fun destroy() {
        if (isDestroyed) return

        isLoading = false
        unregisterHostDestroyWatcher()
        mainHandler?.removeCallbacksAndMessages(null)
        listener?.onAdClosed()
        safelyDestroyWebView()
    }

    fun removeFromParent() {
        try {
            (parent as? ViewGroup)?.removeView(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing from parent: ${e.message}", e)
        }
    }

    // ---------------------------------------------------------------------
    // Ad session management
    // ---------------------------------------------------------------------

    /**
     * Takes over a surviving session: re-parents its rendered WebView into
     * this view and rebinds tracking. Impression state carries over, so
     * analytics are not double-fired.
     */
    private fun adoptSession(key: String, session: AdSession) {
        Log.d(TAG, "Adopting live ad session '$key' - same ad, no re-fetch")

        // Make the previous host inert so it can't destroy the shared WebView
        session.hostView?.takeIf { it !== this }?.releaseSession()
        session.hostView = this
        activeSessionKey = key

        isDestroyed = false
        isLoading = false
        metaData = session.metaData
        mediaType = session.mediaType
        isTestMode = session.isTestMode
        webView = session.webView
        jsInterface = session.jsInterface

        (session.webView.parent as? ViewGroup)?.removeView(session.webView)
        removeAllViews()
        addView(
            session.webView, LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
            )
        )

        jsInterface!!.rebind(this)
        registerHostDestroyWatcher()

        try {
            session.webView.onResume()
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming adopted WebView: ${e.message}", e)
        }

        listener?.onAdLoaded()
    }

    /**
     * Detaches this view from its session WITHOUT destroying the shared
     * WebView, when another AdView adopts it. destroy() then no-ops here.
     */
    internal fun releaseSession() {
        unregisterHostDestroyWatcher()
        mainHandler?.removeCallbacksAndMessages(null)
        webView = null
        jsInterface = null
        activeSessionKey = null
        isDestroyed = true
        isLoading = false
    }

    // Placement identity, best effort: explicit placementId, else the view's
    // android:id name, else the host fragment class, else none (no retention)
    private fun resolvePlacementKey(): String {
        if (placementId.isNotEmpty()) return placementId

        if (id != View.NO_ID) {
            try {
                return "vid:" + resources.getResourceEntryName(id)
            } catch (e: Exception) {
                // Generated/unnamed id - fall through
            }
        }

        findHostFragment()?.let { fragment ->
            return "frag:" + fragment.javaClass.name
        }

        return ""
    }

    private fun sessionKey(): String? {
        val placement = resolvePlacementKey()
        if (placement.isEmpty()) return null
        return "$adUnitId|$placement"
    }

    // ---------------------------------------------------------------------
    // Loading and rendering
    // ---------------------------------------------------------------------

    private fun startAdLoad(adRequest: AdRequest) {
        try {
            val adgeist = getInstance()
            val fetchCreative: FetchCreative = adgeist.getCreative()

            isTestMode = adRequest.isTestMode

            fetchCreative.fetchCreative(
                adUnitId, "FIXED", isTestMode
            ) { result ->
                mainHandler?.post {
                    // Clear the flag even when destroyed mid-fetch, so a
                    // later loadAd isn't rejected
                    isLoading = false
                    if (isDestroyed) return@post

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

                        val primaryCreative = mutableMapOf<String, String?>()
                        primaryCreative["src"] = creativeDataFromApiResponse.primary?.fileUrl
                        primaryCreative["thumbnailUrl"] = creativeDataFromApiResponse.primary?.thumbnailUrl
                        primaryCreative["type"] = creativeDataFromApiResponse.primary?.type

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

    /** Creates the WebView, wires the JS bridge, and renders the creative. */
    private fun renderAdWithAdCard(creativeJsonData: String) {
        if (isDestroyed) return

        registerHostDestroyWatcher()
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

        // Inspectable via chrome://inspect/#devices
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
                Log.i(TAG, "✅ WebView page finished loading: $url")
            }

            override fun onLoadResource(view: WebView, url: String) {
                super.onLoadResource(view, url)
                Log.d(TAG, "📦 Loading resource: $url")
            }
        }

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
                    else -> Log.d(TAG, "🔵 JS Log: $fullLog")
                }
                return true
            }
        }

        // Exposed to the page as the 'Android' object
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

        // Companion ads stay hidden until the overflow check completes
        if (adType == AdType.COMPANION) {
            webView!!.visibility = View.INVISIBLE
        }

        // Register the session so this ad survives view recreation; views
        // without a resolvable placement identity get no retention
        val key = sessionKey()
        if (key != null) {
            activeSessionKey = key
            AdSessionStore.put(
                key,
                AdSession(
                    webView!!,
                    jsInterface!!,
                    metaData,
                    mediaType,
                    isTestMode,
                    findActivity(context),
                    this
                )
            )
            Log.d(TAG, "Registered ad session '$key'")
        }
    }

    /** Builds the ad HTML from asset templates with the creative injected. */
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
            val adCardJs = context.assets.open("adcard-beta.js").bufferedReader().use { it.readText() }
            template
                .replace("{{ADCARD_JS}}", adCardJs)
                .replace("{{CREATIVE_DATA}}", escapedJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ad view from assets", e)
            return ""
        }
    }

    // ---------------------------------------------------------------------
    // Measurement and layout
    // ---------------------------------------------------------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        var width: Int
        var height: Int

        // 1. Calculate desired dimensions based on ad settings
        if (adIsResponsive) {
            width = widthSize
            height = heightSize
        } else if (adSize != null) {
            width = adSize!!.getWidthInPixels(context)
            height = adSize!!.getHeightInPixels(context)
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

    // ---------------------------------------------------------------------
    // Window lifecycle: detach is transient, so the ad is only paused/resumed
    // ---------------------------------------------------------------------

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        registerHostDestroyWatcher()

        if (isDestroyed) return

        if (webView != null) {
            try {
                webView!!.onResume()
            } catch (e: Exception) {
                Log.e(TAG, "Error resuming WebView: ${e.message}", e)
            }
        }
        jsInterface?.onHostAttached()
        Log.d(TAG, "Attached to window - ad resumed")
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)

        if (webView == null || isDestroyed) return

        if (visibility == VISIBLE) {
            try {
                webView!!.onResume()
            } catch (e: Exception) {
                Log.e(TAG, "Error resuming WebView: ${e.message}", e)
            }
        } else {
            try {
                webView!!.onPause()
            } catch (e: Exception) {
                Log.e(TAG, "Error pausing WebView: ${e.message}", e)
            }
            releaseImeSession()
        }
    }

    override fun onDetachedFromWindow() {
        // Suspend tracking before super: the window's ViewTreeObserver is
        // unreachable afterwards
        if (!isDestroyed) {
            jsInterface?.onHostDetached()
            try {
                webView?.onPause()
            } catch (e: Exception) {
                Log.e(TAG, "Error pausing WebView: ${e.message}", e)
            }
            releaseImeSession()
            Log.d(TAG, "Detached from window - ad paused (not destroyed)")
        }
        super.onDetachedFromWindow()
    }

    /**
     * A WebView kept alive while its screen is covered can leave the IME
     * bound to an inactive input connection (its served view is gone but the
     * binding survives). Key events are routed through the IME stage before
     * the activity's view hierarchy, so they die in that dead session -
     * notably the system BACK key, which stops working app-wide. Force
     * InputMethodManagerService to rebind to whatever is currently focused
     * (or finish input entirely) so key dispatch recovers.
     */
    private fun releaseImeSession() {
        val activity = findActivity(context) ?: return
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE)
            as? InputMethodManager ?: return
        // Post so this runs after the detach pass completes and window focus
        // has settled on the newly shown screen
        mainHandler?.post {
            try {
                val focused = activity.currentFocus
                if (focused != null) {
                    imm.restartInput(focused)
                } else {
                    imm.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing IME session: ${e.message}", e)
            }
        }
    }

    // ---------------------------------------------------------------------
    // Host destroy watcher
    // ---------------------------------------------------------------------

    /**
     * Destroys the ad when its screen is gone for good. Prefers the host
     * fragment's INSTANCE lifecycle (onDestroy fires on pop/removal, not
     * while covered on the back stack), falling back to the activity.
     */
    private fun registerHostDestroyWatcher() {
        val fragment = if (watchFragmentLifecycle) findHostFragment() else null

        if (observedLifecycle != null || activityCallbacks != null) {
            // Upgrade an activity-level watcher once the view is inside a
            // fragment tree (loadAd can run before the view is added)
            if (watchingFragment || fragment == null) return
            unregisterHostDestroyWatcher()
        }

        val hostOwner: LifecycleOwner? = fragment
            ?: (findActivity(context) as? LifecycleOwner ?: findLifecycleOwner(context))

        if (hostOwner != null) {
            val observer = object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    Log.d(TAG, "Host ${if (owner is androidx.fragment.app.Fragment) "fragment (screen popped/removed)" else "activity"} destroyed - destroying ad")
                    // Unregister first: destroy() may no-op on its isDestroyed
                    // guard, which would leave this fired observer lingering
                    unregisterHostDestroyWatcher()
                    destroy()
                }
            }
            hostOwner.lifecycle.addObserver(observer)
            lifecycleObserver = observer
            observedLifecycle = hostOwner.lifecycle
            watchingFragment = fragment != null
            return
        }

        val hostActivity = findActivity(context)
        if (hostActivity != null) {
            val callbacks = object : Application.ActivityLifecycleCallbacks {
                override fun onActivityDestroyed(destroyed: Activity) {
                    if (destroyed === hostActivity) {
                        Log.d(TAG, "Host activity destroyed - destroying ad")
                        destroy()
                    }
                }

                override fun onActivityCreated(a: Activity, b: android.os.Bundle?) {}
                override fun onActivityStarted(a: Activity) {}
                override fun onActivityResumed(a: Activity) {}
                override fun onActivityPaused(a: Activity) {}
                override fun onActivityStopped(a: Activity) {}
                override fun onActivitySaveInstanceState(a: Activity, b: android.os.Bundle) {}
            }
            val application = hostActivity.application
            application.registerActivityLifecycleCallbacks(callbacks)
            activityCallbacks = callbacks
            observedApplication = application
        }
    }

    private fun unregisterHostDestroyWatcher() {
        lifecycleObserver?.let { observedLifecycle?.removeObserver(it) }
        lifecycleObserver = null
        observedLifecycle = null
        watchingFragment = false

        activityCallbacks?.let { observedApplication?.unregisterActivityLifecycleCallbacks(it) }
        activityCallbacks = null
        observedApplication = null
    }

    // ---------------------------------------------------------------------
    // Teardown
    // ---------------------------------------------------------------------

    /**
     * Staged WebView teardown: flag first (async callbacks bail), drop the
     * session, then gracefully shut the WebView down before native destroy.
     */
    private fun safelyDestroyWebView() {
        if (isDestroyed) return
        isDestroyed = true

        val webViewToDestroy = webView
        webView = null
        jsInterface?.destroyListeners()
        jsInterface = null

        if (webViewToDestroy == null) return

        activeSessionKey?.let { AdSessionStore.removeIfHosts(it, webViewToDestroy) }
        activeSessionKey = null

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

                // Grace period lets in-flight render/JS work settle before
                // the native destroy
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

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun findHostFragment(): androidx.fragment.app.Fragment? {
        return try {
            androidx.fragment.app.FragmentManager.findFragment(this)
        } catch (e: Exception) {
            null
        }
    }

    private fun findActivity(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    private fun findLifecycleOwner(context: Context): LifecycleOwner? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is LifecycleOwner) return current
            current = current.baseContext
        }
        return null
    }

    private fun openInBrowser(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open external URL: $url", e)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun pxToDp(px: Int): Int {
        return (px / resources.displayMetrics.density).toInt()
    }
}
