package com.adgeistkit.constants

import com.adgeistkit.ads.AdSize
import com.adgeistkit.ads.AdgeistEventCode

internal object Messages {
    object Listener {
        const val AD_LOADED = "Ad loaded successfully"
        const val AD_CLOSED = "Ad closed"
        const val AD_CLICKED = "Ad clicked"

        const val AD_NO_FILL = "No ad available for this ad unit"
        const val AD_NETWORK_ERROR = "Ad request failed: network unavailable or server error"

        const val SDK_NOT_INITIALIZED =
            "AdgeistCore is not initialized. Call AdgeistCore.initialize() before loadAd()."
        const val AD_UNIT_ID_EMPTY = "Ad unit ID is null or empty"
        const val AD_SIZE_NOT_SET =
            "adSize not set. Call setAdDimension() or set adIsResponsive = true before loadAd()"
        const val RESPONSIVE_SIZE_UNDETERMINED =
            "This responsive AdView cannot determine its width or height"
        const val AD_ALREADY_LOADING = "Ad unit ID is already loading"
        const val AD_REQUEST_REJECTED =
            "Ad request rejected by server: check ad unit ID and ADGEIST_APP_ID"
        const val AD_SIZE_MISMATCH =
            "The requested ad size does not match the creative size returned for this ad unit"
        const val COMPANION_MIN_SIZE_NOT_MET =
            "For companion ads, you should have minimum 320x320 dimensions"
        const val AD_RENDER_FAILED =
            "Ad failed to render, possibly due to a WebView error. Contact AdGeist support with the event code and data.reason"
        const val RENDER_ASSETS_UNREADABLE = "Ad failed to render: ad page assets could not be read"
        const val RENDER_PAGE_NOT_READY =
            "Ad failed to render: the ad page loaded but never reported ready"
        const val RENDER_PROCESS_LOST =
            "Ad failed to render: the WebView renderer process was lost"
        const val AD_RESPONSE_PARSE_FAILED =
            "SDK could not parse the ad response: it does not match the format this SDK version expects. Retry later; if it keeps happening, contact AdGeist support with the event code"

        fun forCode(code: AdgeistEventCode): String = when (code) {
            AdgeistEventCode.AL1 -> AD_LOADED
            AdgeistEventCode.AL2 -> AD_CLOSED
            AdgeistEventCode.AI1 -> AD_CLICKED
            AdgeistEventCode.AE1 -> AD_NO_FILL
            AdgeistEventCode.AE2 -> AD_NETWORK_ERROR
            AdgeistEventCode.AE3 -> AD_RENDER_FAILED
            AdgeistEventCode.AE4 -> AD_RESPONSE_PARSE_FAILED
            AdgeistEventCode.AW1 -> SDK_NOT_INITIALIZED
            AdgeistEventCode.AW2 -> AD_UNIT_ID_EMPTY
            AdgeistEventCode.AW3 -> AD_SIZE_NOT_SET
            AdgeistEventCode.AW4 -> RESPONSIVE_SIZE_UNDETERMINED
            AdgeistEventCode.AW5 -> AD_ALREADY_LOADING
            AdgeistEventCode.AW6 -> AD_REQUEST_REJECTED
            AdgeistEventCode.AW7 -> AD_SIZE_MISMATCH
            AdgeistEventCode.AW8 -> COMPANION_MIN_SIZE_NOT_MET
        }

        fun renderProcessLost(didCrash: Boolean): String =
            "$RENDER_PROCESS_LOST (crashed: $didCrash)"

        fun adSizeMismatch(adUnitId: String, requested: AdSize, resolved: AdSize): String =
            "Ad unit '$adUnitId': the requested size $requested does not match the creative " +
                "size $resolved returned for this ad unit. The AdView has been resized to " +
                "$resolved. Set the ad unit's size to $resolved to avoid a layout shift."

        fun responsiveSizeUndetermined(
            adUnitId: String,
            widthUndetermined: Boolean,
            heightUndetermined: Boolean
        ): String {
            val axis = when {
                widthUndetermined && heightUndetermined -> "width or height"
                widthUndetermined -> "width"
                else -> "height"
            }

            val remedy = when {
                widthUndetermined && heightUndetermined ->
                    "Give the AdView a fixed width and height in your layout, or set " +
                        "adIsResponsive = false and call setAdDimension(AdSize(width, height))."

                widthUndetermined ->
                    "Call setAdDimension(AdSize.width(...)) or give the AdView a fixed width " +
                        "in your layout."

                else ->
                    "Call setAdDimension(AdSize.height(...)) or give the AdView a fixed height " +
                        "in your layout."
            }

            return "Ad unit '$adUnitId': this responsive AdView cannot determine its $axis. Its " +
                "parent supplies no fixed $axis and no AdSize supplies one, so it measures " +
                "0 and the ad will not be visible. $remedy"
        }

        fun companionMinSizeNotMet(viewWidth: Int, viewHeight: Int): String =
            "$COMPANION_MIN_SIZE_NOT_MET. But available " +
                "space is ${viewWidth}x${viewHeight}. So we are collapsing the ad, we won't " +
                "track impressions, clicks etc for this ad."
    }

    object Exceptions {
        const val AD_SIZE_NULL = "AdSize cannot be null"
    }
}
