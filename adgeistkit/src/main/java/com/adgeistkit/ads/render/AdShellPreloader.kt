package com.adgeistkit.ads.render

import android.content.res.AssetManager
import android.os.Handler
import android.webkit.WebView
import com.adgeistkit.ads.AdgeistEvent
import com.adgeistkit.ads.AdgeistEventCode
import com.adgeistkit.ads.AdgeistEventData
import com.adgeistkit.benchmark.AdRenderBenchmark
import com.adgeistkit.data.models.AdSpaceType
import com.adgeistkit.constants.General
import com.adgeistkit.constants.Messages

internal class AdShellPreloader(
    private val benchmark: AdRenderBenchmark,
    private val handler: Handler,
    private val onShellFailed: (AdgeistEvent) -> Unit,
) {

    companion object {
        private const val BASE_URL = "https://adgeist.ai"
        private const val MIME_TYPE = "text/html"
        private const val ENCODING = "UTF-8"
    }

    private var shellReady = false
    private var heldCreativeJson: String? = null
    private var stalledHandshakeDeadline: Runnable? = null

    var submittedAdSpaceType: AdSpaceType? = null
        private set

    fun resetForNewLoad() {
        cancelStalledHandshakeDeadline()
        shellReady = false
        heldCreativeJson = null
        submittedAdSpaceType = null
    }

    fun loadShellIntoWebView(webView: WebView, assets: AssetManager): Boolean {
        val shell = AdCardHtml.build(assets)
        benchmark.onShellAssetsRead()

        if (shell.isBlank()) {
            onShellFailed(renderFailed(Messages.Listener.RENDER_ASSETS_UNREADABLE))
            return false
        }

        webView.loadDataWithBaseURL(
            BASE_URL,
            shell,
            MIME_TYPE,
            ENCODING,
            null
        )
        benchmark.onShellHandedToWebView()
        return true
    }

    fun markShellReadyAndReleaseCreative(): String? {
        if (shellReady) return null

        cancelStalledHandshakeDeadline()
        benchmark.onPreloadEnd()
        shellReady = true

        return heldCreativeJson?.also { heldCreativeJson = null }
    }

    fun submitCreativeForRender(creativeJson: String, adSpaceType: AdSpaceType): Boolean {
        benchmark.onPayloadReady()
        submittedAdSpaceType = adSpaceType

        if (shellReady) return true

        heldCreativeJson = creativeJson
        return false
    }

    fun injectCreativeIntoShell(webView: WebView, creativeJson: String) {
        webView.evaluateJavascript(
            "initAd(${AdCardHtml.quoteForJs(creativeJson)});",
            null
        )
    }

    fun onShellPageFinished() {
        if (shellReady) return

        scheduleStalledHandshakeDeadline()
    }

    fun onRendererProcessLost(didCrash: Boolean) {
        cancelStalledHandshakeDeadline()
        onShellFailed(renderFailed(Messages.Listener.renderProcessLost(didCrash)))
    }

    private fun scheduleStalledHandshakeDeadline() {
        cancelStalledHandshakeDeadline()

        val deadline = Runnable {
            stalledHandshakeDeadline = null
            if (shellReady) return@Runnable
            onShellFailed(renderFailed(Messages.Listener.RENDER_PAGE_NOT_READY))
        }

        stalledHandshakeDeadline = deadline
        handler.postDelayed(deadline, General.Timing.STALLED_HANDSHAKE_GRACE_MS)
    }

    private fun renderFailed(reason: String): AdgeistEvent =
        AdgeistEvent(AdgeistEventCode.AW9, AdgeistEventData(reason))

    private fun cancelStalledHandshakeDeadline() {
        val deadline = stalledHandshakeDeadline ?: return
        stalledHandshakeDeadline = null
        handler.removeCallbacks(deadline)
    }
}
