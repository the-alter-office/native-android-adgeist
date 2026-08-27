package com.adgeistkit.ads.tracking

/**
 * What analytics one ad has already sent. Held by the screen's cache entry and
 * shared with every [AdActivity] built for that ad, so an ad rebuilt after a
 * rotation or a return to the screen never sends a second impression.
 */
internal class AdTrackingState {
    var impressionSent: Boolean = false

    var lastClickTime: Long = 0L
}
