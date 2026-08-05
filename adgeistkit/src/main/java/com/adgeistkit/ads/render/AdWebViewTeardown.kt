package com.adgeistkit.ads.render

import android.os.Handler
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView

/**
 * The single staged shutdown for an ad WebView, shared by the host AdView's
 * teardown and by discarding a parked session.
 *
 * A WebView cannot just be dropped: in-flight loads, JS timers and the injected
 * bridge keep running, and calling `destroy()` underneath them crashes in native
 * code. Hence this order - cut the bridge, stop the page, detach, blank it - and the
 * grace period before the native destroy.
 */
internal object AdWebViewTeardown {

    private const val TAG = "AdWebViewTeardown"
    private const val DESTROY_GRACE_MS = 600L

    fun destroy(webView: WebView, handler: Handler) {
        try {
            try {
                webView.removeJavascriptInterface("Android")
            } catch (e: Exception) { /* nothing left to unbind */
            }

            webView.stopLoading()
            webView.onPause()
            webView.clearHistory()
            webView.clearCache(true)
            (webView.parent as? ViewGroup)?.removeView(webView)

            try {
                webView.loadUrl("about:blank")
            } catch (e: Exception) { /* already unusable */
            }

            handler.postDelayed({
                try {
                    webView.destroy()
                } catch (e: Exception) {
                    Log.e(TAG, "WebView final destroy failed", e)
                }
            }, DESTROY_GRACE_MS)
        } catch (e: Exception) {
            Log.e(TAG, "WebView cleanup error", e)
        }
    }
}
