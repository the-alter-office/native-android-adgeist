package com.adgeistkit.constants

import okhttp3.Protocol
import okhttp3.TlsVersion

internal object Logs {
    object Error {
        const val NOT_INITIALIZED = "ERROR: AdgeistCore not initialized"
        const val INITIALIZATION_FAILED = "CRITICAL: AdgeistCore initialization failed"
        const val UNCAUGHT_COROUTINE_EXCEPTION = "Uncaught exception in SDK coroutine"

        fun parsingError(reason: String?): String = "Parsing error: $reason"

        fun invalidJson(json: String): String = "Invalid JSON: $json"

        fun invalidVideoStatusJson(json: String): String = "Invalid JSON in postVideoStatus: $json"

        fun adOverflow(contentWidth: Int, contentHeight: Int, viewWidth: Int, viewHeight: Int): String =
            "Ad overflow: content ${contentWidth}x${contentHeight} > view ${viewWidth}x${viewHeight}"

        fun imeReleaseFailed(reason: String?): String = "Error releasing IME session: $reason"

        fun externalUrlOpenFailed(url: String): String = "Failed to open external URL: $url"

        fun jsError(fullLog: String): String = "JS Error: $fullLog"

        const val SHELL_ASSETS_LOAD_FAILED = "Failed to load ad view from assets"

        const val RETRY_PERSIST_FAILED = "Failed to persist retry item - it will not be retried"
        const val CONNECTIVITY_CALLBACK_FAILED = "Could not register connectivity callback"
        const val RETRY_QUEUE_READ_FAILED = "Failed to read the retry queue"
        const val RETRY_QUEUE_PERSISTED_READ_FAILED = "Failed to read persisted retry queue"
        const val RETRY_QUEUE_UPDATE_FAILED = "Failed to update the retry queue"
    }

    object Warning {
        const val APP_ID_EMPTY =
            "WARNING: adgeistAppID is empty. Set com.adgeistkit.ads.ADGEIST_APP_ID in AndroidManifest.xml"

        fun connectFailed(elapsedMs: Long, reason: String?): String =
            "Connect failed after ${elapsedMs}ms: $reason"

        const val VIEW_MODEL_UNREACHABLE =
            "Could not reach the screen's ViewModel; this ad will not be retained"
        const val ACTIVITY_SCOPE_RETENTION =
            "Retaining this ad against the Activity's ViewModelStore - no per-screen scope was " +
                "found. Two placements of the same ad unit on different screens will share one " +
                "retained ad. Set AdView.viewModelStoreOwner to the screen's owner " +
                "(LocalViewModelStoreOwner.current under Compose) to scope it correctly."

        fun blockedClickUrl(url: String): String = "Blocked non-http(s) ad click URL: $url"

        fun jsWarning(fullLog: String): String = "JS Warning: $fullLog"

        fun advertisingIdFailed(reason: String?): String = "Failed to get Advertising ID: $reason"

        fun fallbackDeviceIdFailed(reason: String?): String =
            "Failed to read or create fallback device ID: $reason"

        fun deviceIdentifierFailed(reason: String?): String =
            "Failed to resolve device identifier: $reason"

        fun retryGivenUp(maxReattempts: Int, url: String): String =
            "Giving up after $maxReattempts re-attempts: $url"
    }

    object Debug {
        const val CORE_INITIALIZED = "AdgeistCore initialized successfully"
        const val WEBVIEW_DESTROYED = "WebView destroyed"

        fun requestFailed(domain: String, reason: String?): String = "Request Failed: $domain - $reason"

        fun warmUpFailed(reason: String?): String = "Warm-up failed: $reason"

        fun warmUpComplete(code: Int): String = "Warm-up complete ($code)"

        fun pageFinished(url: String): String = "✅ WebView page finished loading: $url"

        fun loadingResource(url: String): String = "📦 Loading resource: $url"

        fun jsLog(fullLog: String): String = "🔵 JS Log: $fullLog"

        fun dnsResolved(domainName: String, elapsedMs: Long): String = "DNS $domainName: ${elapsedMs}ms"

        fun tlsHandshake(elapsedMs: Long, tlsVersion: TlsVersion?): String =
            "TLS handshake: ${elapsedMs}ms ($tlsVersion)"

        fun tcpConnected(hostAddress: String?, tcpMs: Long, totalMs: Long, protocol: Protocol?): String =
            "TCP $hostAddress: ${tcpMs}ms | " +
                "TCP+TLS: ${totalMs}ms | $protocol"

        const val CONNECTION_REUSED = "Connection reused from pool - no DNS/TCP/TLS cost"
    }
}
