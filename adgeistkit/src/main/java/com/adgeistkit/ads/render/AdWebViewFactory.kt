package com.adgeistkit.ads.render

import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.ConsoleMessage.MessageLevel
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.adgeistkit.ads.JsBridge

internal object AdWebViewFactory {

    private const val TAG = "AdWebView"

    /** [contextWrapper] is swappable so the WebView can be rebound to a recreated Activity. */
    class Created(
        val webView: WebView,
        val contextWrapper: MutableContextWrapper,
        val allocEndAt: Long,
    )

    fun warmup(context: Context) {
        try {
            WebView(context.applicationContext)
        } catch (_: Exception) {
        }
    }

    fun create(context: Context, bridge: JsBridge): Created {
        val wrapper = MutableContextWrapper(context)
        val webView = WebView(wrapper)
        val allocEndAt = SystemClock.elapsedRealtime()

        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true

        if (com.adgeistkit.BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView.webViewClient = AdWebViewClient(bridge)
        webView.webChromeClient = AdWebChromeClient()

        webView.addJavascriptInterface(bridge, "Android")

        return Created(webView, wrapper, allocEndAt)
    }

    fun matchParentLayoutParams() = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )

    private fun openInBrowser(context: Context, url: String) {
        try {
            val uri = Uri.parse(url)
            // Creative-supplied URLs are untrusted: only hand http(s) to the
            // system, never intent://, market://, or custom app schemes
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") {
                Log.w(TAG, "Blocked non-http(s) ad click URL: $url")
                return
            }
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open external URL: $url", e)
        }
    }

    private class AdWebViewClient(private val bridge: JsBridge) : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            openInBrowser(view.context, url)
            bridge.recordClickListener()
            return true
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
            openInBrowser(view.context, request.url.toString())
            bridge.recordClickListener()
            return true
        }

        /**
         * Serves creative media from the device cache. Called on a WebView resource
         * thread, never the main thread, so blocking on the cache here is safe.
         * Returning null hands the request back to the WebView.
         */
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            // The WebView outlives its first host, so only the application context is safe
            val cached = CreativeResourceInterceptor.intercept(
                view.context.applicationContext,
                request
            )
            return cached ?: super.shouldInterceptRequest(view, request)
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

    private class AdWebChromeClient : WebChromeClient() {
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
}
