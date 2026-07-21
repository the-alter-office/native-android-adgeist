package com.adgeistkit.ads

import android.app.Activity
import android.webkit.WebView

/**
 * A live ad decoupled from any AdView instance: the rendered WebView plus its
 * JS bridge, tracking state, and creative metadata. When navigation discards
 * an AdView, the session survives and the same placement adopts it on return.
 */
internal class AdSession(
    val webView: WebView,
    val jsInterface: JsBridge,
    val metaData: String,
    val mediaType: String?,
    // Sessions never cross activities: the WebView holds this activity's context
    val hostActivity: Activity?,
    // The AdView currently presenting this session
    var hostView: BaseAdView?
)

/**
 * Registry of live ad sessions keyed by "adUnitId|placement", so a session is
 * resumed only when the same screen/slot loads the same ad unit again.
 * Main-thread only. Sessions are removed when their WebView is destroyed:
 * explicit destroy(), host screen popped, activity destroyed, or a refresh.
 */
internal object AdSessionStore {

    private val sessions = mutableMapOf<String, AdSession>()

    fun put(sessionKey: String, session: AdSession) {
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
}
