package com.adgeistkit.constants

import com.adgeistkit.ads.AdSize

internal object Messages {
    object Listener {
        const val AD_UNIT_ID_EMPTY = "Ad unit ID is null or empty"
        const val AD_ALREADY_LOADING = "Ad unit ID is already loading"
        const val LOAD_BEFORE_INITIALIZE =
            "AdgeistCore is not initialized. Call AdgeistCore.initialize() before loadAd()."
        const val GENERIC_ERROR = "Error"
        const val UNKNOWN_ERROR = "Unknown error occurred"

        const val SHELL_BLANK = "Ad failed to render: ad page assets could not be read"
        const val SHELL_STALLED_HANDSHAKE =
            "Ad failed to render: the ad page loaded but never reported ready"

        fun shellRendererLost(didCrash: Boolean): String =
            "Ad failed to render: the WebView renderer process was lost (crashed: $didCrash)"

        const val FETCH_CONNECT_FAILED = "Failed to connect to server"
        const val FETCH_EMPTY_RESPONSE = "Server returned empty response"
        const val FETCH_REQUEST_FAILED = "Request failed"
        const val FETCH_PARSE_CREATIVE_FAILED = "Failed to parse creative data"
        const val FETCH_NO_VALID_CREATIVE = "No valid ad creative available"
        const val FETCH_PARSE_RESPONSE_FAILED = "Failed to parse ad response"

        const val EMPTY_CREATIVE = "Empty creative"
        const val AD_SIZE_NOT_SET =
            "adSize not set. Call setAdDimension() or set adIsResponsive = true before loadAd()"

        fun adSizeMismatch(adUnitId: String, requested: AdSize, resolved: AdSize): String =
            "Ad unit '$adUnitId': the requested size $requested does not match the creative " +
                "size $resolved returned for this ad unit. The AdView has been resized to " +
                "$resolved. Set the ad unit's size to $resolved to avoid a layout shift."

        fun companionOverflow(viewWidth: Int, viewHeight: Int): String =
            "For companion ads, you should have minimum 320x320 dimensions. But available " +
                "space is ${viewWidth}x${viewHeight}. So we are collapsing the ad, we won't " +
                "track impressions, clicks etc for this ad."
    }

    object Exceptions {
        const val CONTEXT_NULL = "Context cannot be null"
        const val AD_SIZE_NULL = "AdSize cannot be null"
    }
}
