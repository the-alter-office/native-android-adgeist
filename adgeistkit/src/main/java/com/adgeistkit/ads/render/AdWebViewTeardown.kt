package com.adgeistkit.ads.render

import android.os.Handler
import android.view.ViewGroup
import android.webkit.WebView


internal object AdWebViewTeardown {

    private const val TAG = "AdWebViewTeardown"
    private const val DESTROY_GRACE_MS = 600L

    fun destroy(webView: WebView, handler: Handler, onComplete: (() -> Unit)? = null) {
        try {
            try {
                webView.removeJavascriptInterface("Android")
            } catch (e: Exception) { /* nothing left to unbind */
            }

            webView.stopLoading()
            webView.onPause()
            webView.clearHistory()
            (webView.parent as? ViewGroup)?.removeView(webView)

            try {
                webView.loadUrl("about:blank")
            } catch (e: Exception) { /* already unusable */
            }

            handler.postDelayed({
                try {
                    webView.destroy()
                } catch (e: Exception) {
                    //
                } finally {
                    onComplete?.invoke()
                }
            }, DESTROY_GRACE_MS)
        } catch (e: Exception) {
            onComplete?.invoke()
        }
    }
}
