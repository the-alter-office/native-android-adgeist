package com.adgeistkit.ads.session

import android.app.Activity
import android.content.MutableContextWrapper
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.webkit.WebView
import java.lang.ref.WeakReference
import com.adgeistkit.ads.render.AdWebViewTeardown
import com.adgeistkit.ads.JsBridge
import com.adgeistkit.ads.BaseAdView
import com.adgeistkit.ads.AdSessionConfig

/**
 * A live ad decoupled from any AdView: the rendered WebView plus its JS bridge,
 * tracking state and creative metadata. Parked here when an AdView is discarded so
 * the same screen can adopt it on return, re-fetching and re-counting nothing.
 */
internal class AdSession(
    val webView: WebView,
    val contextWrapper: MutableContextWrapper,
    val jsInterface: JsBridge,
    val metaData: String,
    val mediaType: String?,
    var hostActivity: Activity?,
    val screenToken: String?,
    val renderedAtMs: Long,
    var lastAdoptedAtMs: Long,
    var hostView: BaseAdView?
) {
    var evictionRunnable: Runnable? = null
    val isParked: Boolean get() = hostView == null && hostActivity == null
}

/**
 * Registry of live ad sessions keyed by "adUnitId|screenToken", so a session is
 * only resumed by the screen instance that created it. Also holds the in-screen
 * slot claims that cap one AdView per ad unit per screen. Main thread only.
 */
internal object AdSessionStore {

    private const val TAG = "AdSessionStore"

    // Only for parked sessions with no screenToken. Token-backed sessions get no
    // timer at all: `AdSlotToken.onCleared` says exactly when their screen died,
    // and a timer would discard ads the user is about to navigate back to.
    private const val LEGACY_PARK_TIMEOUT_MS = 60_000L

    private val sessions = mutableMapOf<String, AdSession>()

    // sessionKey -> the AdView holding that slot. Weak, so a destroyed AdView (and
    // the Activity it references) is never kept alive by this map.
    private val slotClaims = mutableMapOf<String, WeakReference<BaseAdView>>()

    private val handler = Handler(Looper.getMainLooper())

    fun put(sessionKey: String, session: AdSession) {
        sessions[sessionKey]?.takeIf { it.webView !== session.webView && it.isParked }?.let {
            Log.d(TAG, "Replacing parked ad session '$sessionKey'")
            destroyDetachedWebView(it)
        }
        sessions[sessionKey] = session
    }

    fun get(sessionKey: String): AdSession? = sessions[sessionKey]

    fun remove(sessionKey: String) {
        sessions.remove(sessionKey)
    }

    /** Removes the session only if it is backed by the given WebView. */
    fun removeIfHosts(sessionKey: String, webView: WebView) {
        if (sessions[sessionKey]?.webView === webView) {
            sessions.remove(sessionKey)
        }
    }

    /** Whether a surviving session may still be shown. Config changes skip this. */
    fun isFresh(session: AdSession): Boolean {
        val ttl = AdSessionConfig.sessionTtlMs
        if (ttl <= 0L) return false
        return SystemClock.elapsedRealtime() - session.renderedAtMs < ttl
    }

    fun markAdopted(session: AdSession) {
        session.lastAdoptedAtMs = SystemClock.elapsedRealtime()
    }

    /**
     * Claims the slot for [view] unless another AdView is on screen holding it.
     */
    fun claimSlot(sessionKey: String, view: BaseAdView): BaseAdView? {
        val current = slotClaims[sessionKey]?.get()
        if (current != null && current !== view && current.isAttachedToWindow) {
            return current
        }
        slotClaims[sessionKey] = WeakReference(view)
        return null
    }

    /** Releases the claim only if [view] still holds it. */
    fun releaseSlot(sessionKey: String, view: BaseAdView) {
        if (slotClaims[sessionKey]?.get() === view) {
            slotClaims.remove(sessionKey)
        }
    }

    /**
     * Releases every claim [view] holds, under any key.
     */
    fun releaseSlotsHeldBy(view: BaseAdView) {
        val held = slotClaims.filterValues { it.get() === view }.keys
        held.forEach { slotClaims.remove(it) }
    }

    /**
     * Detaches a session from its dying host and keeps it registered for adoption.
     * Unconditional: deciding "coming back" vs "gone forever" is
     * `AdSlotToken.onCleared`'s job, not a guess made here.
     */
    fun park(sessionKey: String, session: AdSession) {
        session.hostView = null
        session.hostActivity = null
        // Stop pinning the dying activity while parked
        session.contextWrapper.baseContext = session.contextWrapper.baseContext.applicationContext

        if (session.screenToken == null) {
            val webView = session.webView
            val runnable = Runnable {
                val current = sessions[sessionKey]
                if (current?.webView === webView && current.isParked) {
                    Log.d(TAG, "Evicting parked ad session '$sessionKey' - never re-adopted")
                    sessions.remove(sessionKey)
                    destroyDetachedWebView(current)
                }
            }
            session.evictionRunnable = runnable
            handler.postDelayed(runnable, LEGACY_PARK_TIMEOUT_MS)
        }

        Log.d(TAG, "Parked ad session '$sessionKey'")
        enforceParkedCap()
    }

    fun cancelEviction(session: AdSession) {
        session.evictionRunnable?.let { handler.removeCallbacks(it) }
        session.evictionRunnable = null
    }

    /** The authoritative teardown: the owning screen is finished, so its ads go. */
    fun destroyAllForScreen(screenToken: String) {
        val doomed = sessions.filter { it.value.screenToken == screenToken }
        doomed.forEach { (sessionKey, session) ->
            sessions.remove(sessionKey)
            slotClaims.remove(sessionKey)
            // A parked session has no host view left to run the teardown
            val host = session.hostView
            if (host != null) host.destroyInternal() else destroyDetachedWebView(session)
            Log.d(TAG, "Destroyed ad session '$sessionKey' - screen finished")
        }
    }

    /** Caps live off-screen WebViews, discarding the least recently adopted. */
    private fun enforceParkedCap() {
        val cap = AdSessionConfig.maxParkedSessions
        val parked = sessions.entries.filter { it.value.isParked }
        if (parked.size <= cap) return

        parked.sortedBy { it.value.lastAdoptedAtMs }
            .take(parked.size - cap)
            .forEach { (sessionKey, session) ->
                Log.d(TAG, "Evicting parked ad session '$sessionKey' - over the cap ($cap)")
                sessions.remove(sessionKey)
                slotClaims.remove(sessionKey)
                destroyDetachedWebView(session)
            }
    }

    /** Staged teardown for a session no AdView is hosting. */
    fun destroyDetachedWebView(session: AdSession) {
        cancelEviction(session)
        session.jsInterface.destroyListeners()
        AdWebViewTeardown.destroy(session.webView, handler)
    }
}
