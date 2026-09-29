package com.adgeistkit.ads.render

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.ConsoleMessage.MessageLevel
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import com.adgeistkit.ads.JsBridge
import com.adgeistkit.constants.Logs
import com.adgeistkit.utilities.logD
import com.adgeistkit.utilities.logI

@SuppressLint("StaticFieldLeak")
internal object AdWebViewFactory {

    private const val TAG = "AdWebView"

    class Created(
        val webView: WebView,
        val allocEndAt: Long,
    )

    private var warmed: WebView? = null

    fun warmup(context: Context) {
        if (warmed != null) return

        warmed = try {
            WebView(context.applicationContext)
        } catch (_: Exception) {
            null
        }
    }

    fun create(context: Context, bridge: JsBridge): Created {
        val webView = warmed?.also { warmed = null } ?: WebView(context)
        val allocEndAt = SystemClock.elapsedRealtime()

        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true

        if (com.adgeistkit.BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView.webViewClient = AdWebViewClient(bridge)
        webView.webChromeClient = AdWebChromeClient()

        webView.addJavascriptInterface(bridge, "Android")

        return Created(webView, allocEndAt)
    }

    fun matchParentLayoutParams() = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )

    private val blockedDeepLinkSchemes = setOf("javascript", "file", "content", "data", "intent")

    private fun handleClick(context: Context, url: String, bridge: JsBridge) {
        val deepLinkUrl = bridge.deepLinkUrl
        if (deepLinkUrl.isNullOrBlank() || !openDeepLink(context, deepLinkUrl)) {
            openInBrowser(context, url)
        }
        bridge.recordClickListener()
    }

    private fun openDeepLink(context: Context, url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme in blockedDeepLinkSchemes) return false

        return try {
            context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }


    private fun openInBrowser(context: Context, url: String) {
        try {
            val uri = Uri.parse(url)
            // Creative-supplied URLs are untrusted: only hand http(s) to the
            // system, never intent://, market://, or custom app schemes
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") {
                Log.w(TAG, Logs.Warning.blockedClickUrl(url))
                return
            }
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, Logs.Error.externalUrlOpenFailed(url), e)
        }
    }

    private class AdWebViewClient(private val bridge: JsBridge) : WebViewClient() {
        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            handleClick(view.context, url, bridge)
            return true
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
            handleClick(view.context, request.url.toString(), bridge)
            return true
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            logI(TAG) { Logs.Debug.pageFinished(url) }
            bridge.onShellPageFinished()
        }

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            bridge.onRenderProcessGone(detail.didCrash())
            return true
        }

        override fun onLoadResource(view: WebView, url: String) {
            super.onLoadResource(view, url)
            logD(TAG) { Logs.Debug.loadingResource(url) }
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
                MessageLevel.ERROR -> Log.e(TAG, Logs.Error.jsError(fullLog))
                MessageLevel.WARNING -> Log.w(TAG, Logs.Warning.jsWarning(fullLog))
                else -> logD(TAG) { Logs.Debug.jsLog(fullLog) }
            }
            return true
        }
    }
}
