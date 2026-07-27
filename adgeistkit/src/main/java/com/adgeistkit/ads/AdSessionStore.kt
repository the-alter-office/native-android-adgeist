package com.adgeistkit.ads

import android.app.Activity
import android.content.MutableContextWrapper
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView

/**
 * A live ad decoupled from any AdView instance: the rendered WebView plus its
 * JS bridge, tracking state, and creative metadata. When navigation discards
 * an AdView, the session survives and the same placement adopts it on return.
 * When the host activity is destroyed for a config change, the session is
 * parked (detached, context released) and adopted by the recreated screen.
 */
internal class AdSession(
    val webView: WebView,
    // The WebView's swappable context: rebound to the new activity on adoption
    val contextWrapper: MutableContextWrapper,
    val jsInterface: JsBridge,
    val metaData: String,
    val mediaType: String?,
    // Nulled while parked so a destroyed activity is never pinned
    var hostActivity: Activity?,
    // Survives parking: recreation of the same screen class may adopt
    val hostActivityClass: Class<*>?,
    // The AdView currently presenting this session
    var hostView: BaseAdView?
) {
    var evictionRunnable: Runnable? = null
    val isParked: Boolean get() = hostView == null && hostActivity == null
}

/**
 * Registry of live ad sessions keyed by "adUnitId|placement", so a session is
 * resumed only when the same screen/slot loads the same ad unit again.
 * Main-thread only. Sessions are removed when their WebView is destroyed:
 * explicit destroy(), host screen popped, activity destroyed, or a refresh.
 * Parked sessions that are never re-adopted are evicted after a timeout.
 */
internal object AdSessionStore {

    private const val TAG = "AdSessionStore"
    private const val PARKED_TIMEOUT_MS = 10_000L

    private val sessions = mutableMapOf<String, AdSession>()
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

    /**
     * Detaches the session from its dying host (config change) and keeps it
     * registered for adoption by the recreated screen. Evicted if nothing
     * adopts it in time.
     */
    fun park(sessionKey: String, session: AdSession) {
        session.hostView = null
        session.hostActivity = null
        // Stop pinning the dying activity while parked
        session.contextWrapper.baseContext = session.contextWrapper.baseContext.applicationContext

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
        handler.postDelayed(runnable, PARKED_TIMEOUT_MS)
        Log.d(TAG, "Parked ad session '$sessionKey'")
    }

    fun cancelEviction(session: AdSession) {
        session.evictionRunnable?.let { handler.removeCallbacks(it) }
        session.evictionRunnable = null
    }

    /** Staged teardown for a session no AdView is hosting (parked/orphaned). */
    fun destroyDetachedWebView(session: AdSession) {
        cancelEviction(session)
        session.jsInterface.destroyListeners()
        val webView = session.webView
        try {
            try {
                webView.removeJavascriptInterface("Android")
            } catch (e: Exception) { /* ignore */
            }
            webView.stopLoading()
            webView.onPause()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.loadUrl("about:blank")
            handler.postDelayed({
                try {
                    webView.destroy()
                } catch (e: Exception) {
                    Log.e(TAG, "Error destroying detached WebView: ${e.message}", e)
                }
            }, 600)
        } catch (e: Exception) {
            Log.e(TAG, "Error tearing down detached WebView: ${e.message}", e)
        }
    }
}
