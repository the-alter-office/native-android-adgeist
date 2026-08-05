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
import com.adgeistkit.data.network.FetchCreative
import kotlin.math.max
import com.adgeistkit.ads.host.HostDestroyWatcher
import com.adgeistkit.ads.host.findActivity
import com.adgeistkit.ads.host.pxToDp
import com.adgeistkit.ads.host.releaseImeSession
import com.adgeistkit.ads.identity.AdSlotIdentity
import com.adgeistkit.ads.render.AdCardHtml
import com.adgeistkit.ads.render.AdCreativePayload
import com.adgeistkit.ads.render.AdWebViewFactory
import com.adgeistkit.ads.render.AdWebViewTeardown
import com.adgeistkit.ads.session.AdSession
import com.adgeistkit.ads.session.AdSessionStore

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

    // Nothing currently sets this, so video tracking in AdActivity is inert
    var mediaType: String? = null

    // ---- Collaborators ----

    var listener: AdListener? = null

    private val identity = AdSlotIdentity(this)
    internal val screenToken: String? get() = identity.screenToken

    private val hostWatcher = HostDestroyWatcher(this) { parkForRecreation() }

    // ---- Runtime state ----

    internal var webView: WebView? = null
    private var jsInterface: JsBridge? = null
    private var isLoading: Boolean = false
    private var isDestroyed = false
    private var mainHandler: Handler? = null

    // Key of the session this view created or adopted, for cleanup
    private var activeSessionKey: String? = null

    // loadAd() before attach cannot resolve identity yet, so the request waits
    // here until onAttachedToWindow()
    private var pendingLoadRequest: AdRequest? = null

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

    /**
     * Shows the ad for this unit: resumes this slot's live session when one
     * survives, otherwise fetches a fresh creative.
     *
     * Fails via [AdListener.onAdFailedToLoad] - without making a network request -
     * when another slot on this screen is already using this ad unit.
     */
    @RequiresPermission("android.permission.INTERNET")
    fun loadAd(adRequest: AdRequest) {
        if (adUnitId.isEmpty()) {
            Log.e(TAG, "Ad unit ID is null or empty")
            listener?.onAdFailedToLoad("Ad unit ID is null or empty")
            return
        }

        if (isLoading) {
            Log.w(TAG, "loadAd ignored - ad is already loading")
            return
        }

        if (!resolveIdentity()) {
            // Not attached yet, so the view-tree owners are unreachable. Resumed
            // from onAttachedToWindow(); onAdFailedToLoad can therefore arrive a
            // frame after loadAd() rather than synchronously.
            Log.d(TAG, "loadAd deferred until attach - slot identity not resolvable yet")
            pendingLoadRequest = adRequest
            return
        }

        performLoad(adRequest)
    }

    private fun performLoad(adRequest: AdRequest) {
        val key = sessionKey()

        // At most one slot per screen may use a given ad unit. Checked before any
        // network call, so a misplaced slot costs zero ad requests.
        if (key != null) {
            AdSessionStore.claimSlot(key, this)?.let { holder ->
                val failure = "Ad unit '$adUnitId' is already placed in this screen " +
                    "(slot ${holder.slotLabel()}). Only one slot per screen may use an " +
                    "ad unit; slot ${slotLabel()} must use its own ad unit."
                Log.e(TAG, failure)
                listener?.onAdFailedToLoad(failure)
                return
            }
        }

        // Resume a surviving session. A key match is proof this is the same screen
        // instance, so no activity or host-class comparison is needed.
        val session = key?.let { AdSessionStore.get(it) }
        if (key != null && session != null) {
            if (session.hostView === this && webView != null) {
                Log.d(TAG, "loadAd ignored - this view is already presenting the live ad")
                return
            }
            // Reuse regardless of age when this view already hosts the session:
            // that is a reload in place, not a stale ad being resurrected.
            if (session.hostView === this || AdSessionStore.isFresh(session)) {
                adoptSession(key, session)
                return
            }
            Log.d(TAG, "Ad session '$key' is past its TTL - fetching a fresh creative")
            AdSessionStore.remove(key)
            // A parked session has no host view left to run the teardown
            session.hostView?.destroyInternal() ?: AdSessionStore.destroyDetachedWebView(session)
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

    // ---- Teardown ----

    /**
     * Permanently removes this ad: its session, its WebView, and this view. Call
     * [loadAd] again for a fresh one.
     */
    fun destroyAd() {
        destroyInternal()
        removeFromParent()
    }

    @Deprecated("Renamed for clarity", ReplaceWith("destroyAd()"))
    fun destroy() {
        destroyAd()
    }

    fun removeFromParent() {
        try {
            (parent as? ViewGroup)?.removeView(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing from parent: ${e.message}", e)
        }
    }

    /**
     * Tears the ad down but leaves this view in its layout - used by SDK paths where
     * removing it would fight the host's layout, or would remove the very view about
     * to render on a reload in place.
     */
    internal fun destroyInternal() {
        if (isDestroyed) return

        isLoading = false
        pendingLoadRequest = null
        unregisterHostDestroyWatcher()
        mainHandler?.removeCallbacksAndMessages(null)
        AdSessionStore.releaseSlotsHeldBy(this)
        listener?.onAdClosed()
        safelyDestroyWebView()
    }

    // Flag first so async callbacks bail, then drop the session before the staged
    // WebView shutdown.
    private fun safelyDestroyWebView() {
        if (isDestroyed) return
        isDestroyed = true

        val webViewToDestroy = webView
        webView = null
        jsInterface?.destroyListeners()
        jsInterface = null

        if (webViewToDestroy == null) return

        activeSessionKey?.let {
            AdSessionStore.removeIfHosts(it, webViewToDestroy)
            AdSessionStore.releaseSlot(it, this)
        }
        activeSessionKey = null

        val handler = mainHandler ?: return
        handler.post {
            AdWebViewTeardown.destroy(webViewToDestroy, handler)
            removeAllViews()
        }
    }

    // ---- Ad session management ----

    /**
     * Takes over a surviving session: re-parents its rendered WebView into
     * this view and rebinds tracking. Impression state carries over, so
     * analytics are not double-fired.
     */
    private fun adoptSession(key: String, session: AdSession) {
        Log.d(TAG, "Adopting live ad session '$key' - same ad, no re-fetch")

        AdSessionStore.cancelEviction(session)
        AdSessionStore.markAdopted(session)
        // Rebind the WebView to the adopting view's (possibly recreated) activity
        session.contextWrapper.baseContext = context
        session.hostActivity = context.findActivity()

        // Make the previous host inert so it can't destroy the shared WebView
        session.hostView?.takeIf { it !== this }?.releaseSession()
        session.hostView = this
        activeSessionKey = key

        isDestroyed = false
        isLoading = false
        metaData = session.metaData
        mediaType = session.mediaType
        webView = session.webView
        jsInterface = session.jsInterface

        (session.webView.parent as? ViewGroup)?.removeView(session.webView)
        removeAllViews()
        addView(
            session.webView, LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
            )
        )

        session.jsInterface.rebind(this)
        registerHostDestroyWatcher()

        try {
            session.webView.onResume()
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming adopted WebView: ${e.message}", e)
        }

        listener?.onAdLoaded()
    }

    /**
     * Detaches the session from this dying view and parks it for adoption,
     * instead of destroying it. Tracking is paused with its impression state
     * intact, so the ad neither re-fetches nor re-counts a view.
     *
     * Parking is unconditional: rotation, the screen being covered on the back
     * stack, and a pop all destroy this view, and telling them apart is
     * [AdSlotToken.onCleared]'s job. If the screen really is finished, that
     * callback tears this parked session down moments later.
     */
    private fun parkForRecreation() {
        val key = activeSessionKey
        val session = key?.let { AdSessionStore.get(it) }
        if (key == null || session == null || session.webView !== webView) {
            destroyInternal()
            return
        }

        unregisterHostDestroyWatcher()
        mainHandler?.removeCallbacksAndMessages(null)
        jsInterface?.onHostDetached()
        try {
            webView?.onPause()
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing parked WebView: ${e.message}", e)
        }
        removeAllViews()

        // Free the in-screen slot so the recreated view - or a rebound
        // RecyclerView holder - can claim it without a false duplicate error
        AdSessionStore.releaseSlot(key, this)
        AdSessionStore.park(key, session)

        // Make this view inert without touching the shared WebView.
        // No onAdClosed(): the ad is surviving, not closing.
        webView = null
        jsInterface = null
        activeSessionKey = null
        isDestroyed = true
        isLoading = false
    }

    /**
     * Detaches this view from its session WITHOUT destroying the shared
     * WebView, when another AdView adopts it. destroy() then no-ops here.
     */
    internal fun releaseSession() {
        unregisterHostDestroyWatcher()
        mainHandler?.removeCallbacksAndMessages(null)
        // No-op when the adopting view has already taken the claim over
        activeSessionKey?.let { AdSessionStore.releaseSlot(it, this) }
        webView = null
        jsInterface = null
        activeSessionKey = null
        isDestroyed = true
        isLoading = false
    }

    // ---- Slot identity ----

    private fun resolveIdentity(): Boolean = identity.resolve(watchFragmentLifecycle)

    /**
     * Supplies identity for hosts the SDK cannot infer it from: Compose
     * destinations, React Native screens, or any custom navigator. Call before
     * [loadAd].
     *
     * Pass a token that stays the same while the screen lives and is unique to
     * that instance of it - a Compose wrapper should use an
     * [androidx.lifecycle.ViewModel] scoped to the current `NavBackStackEntry`.
     */
    fun setScreenIdentity(token: String) = identity.set(token)

    internal fun slotLabel(): String = identity.slotLabel()

    private fun sessionKey(): String? = identity.sessionKey(adUnitId)

    // ---- Loading and rendering ----

    private fun startAdLoad(adRequest: AdRequest) {
        try {
            val adgeist = getInstance()
            val fetchCreative: FetchCreative = adgeist.getCreative()

            fetchCreative.fetchCreative(
                adUnitId, "FIXED"
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
                        Log.d(TAG, "measured: ${pxToDp(measuredWidth)}x${pxToDp(measuredHeight)}dp")

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

        val bridge = JsBridge(this, context)
        jsInterface = bridge
        listener?.onAdOpened()

        val created = AdWebViewFactory.create(context, bridge)
        val adWebView = created.webView
        val webViewContext = created.contextWrapper
        webView = adWebView

        val htmlContent = AdCardHtml.build(context.assets, creativeJsonData)
        adWebView.loadDataWithBaseURL(
            "https://adgeist.ai",
            htmlContent,
            "text/html",
            "UTF-8",
            null
        )

        addView(adWebView, AdWebViewFactory.matchParentLayoutParams())

        // Companion ads stay hidden until the overflow check completes
        if (adType == AdType.COMPANION) {
            adWebView.visibility = View.INVISIBLE
        }

        // Register the session so this ad survives view recreation; views with
        // no resolvable slot identity at all get no retention
        val key = sessionKey()
        if (key != null) {
            activeSessionKey = key
            val hostActivity = context.findActivity()
            val now = SystemClock.elapsedRealtime()
            AdSessionStore.put(
                key,
                AdSession(
                    adWebView,
                    webViewContext,
                    bridge,
                    metaData,
                    mediaType,
                    hostActivity,
                    screenToken,
                    now,
                    now,
                    this
                )
            )
            Log.d(TAG, "Registered ad session '$key'")
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
        // The view-tree owners are only reachable now, so this is where slot
        // identity becomes resolvable and a deferred loadAd() can proceed
        resolveIdentity()
        registerHostDestroyWatcher()

        pendingLoadRequest?.let { request ->
            pendingLoadRequest = null
            performLoad(request)
        }

        if (isDestroyed) return

        try {
            webView?.onResume()
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming WebView: ${e.message}", e)
        }
        jsInterface?.onHostAttached()
        Log.d(TAG, "Attached to window - ad resumed")
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
        // Suspend tracking before super: the window's ViewTreeObserver is
        // unreachable afterwards
        if (!isDestroyed) {
            jsInterface?.onHostDetached()
            try {
                webView?.onPause()
            } catch (e: Exception) {
                Log.e(TAG, "Error pausing WebView: ${e.message}", e)
            }
            releaseImeSession(mainHandler)
            Log.d(TAG, "Detached from window - ad paused (not destroyed)")
        }
        super.onDetachedFromWindow()
    }

    // ---- Host destroy watcher ----

    private fun registerHostDestroyWatcher() = hostWatcher.register(watchFragmentLifecycle)

    private fun unregisterHostDestroyWatcher() = hostWatcher.unregister()

}
