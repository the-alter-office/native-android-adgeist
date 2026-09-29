package com.adgeistkit.ads.render

import android.os.Handler
import android.view.ViewGroup
import android.webkit.WebView
import com.adgeistkit.constants.General

internal object AdWebViewTeardown {

    fun destroy(webView: WebView, handler: Handler, onComplete: (() -> Unit)? = null) {
        try {
            try {
                webView.removeJavascriptInterface("Android")
            } catch (_: Exception) {
            }

            webView.stopLoading()
            webView.onPause()
            webView.clearHistory()
            (webView.parent as? ViewGroup)?.removeView(webView)

            try {
                webView.loadUrl("about:blank")
            } catch (_: Exception) {
            }

            handler.postDelayed({
                try {
                    webView.destroy()
                } catch (_: Exception) {
                    //
                } finally {
                    onComplete?.invoke()
                }
            }, General.Timing.WEBVIEW_DESTROY_GRACE_MS)
        } catch (_: Exception) {
            onComplete?.invoke()
        }
    }
}
