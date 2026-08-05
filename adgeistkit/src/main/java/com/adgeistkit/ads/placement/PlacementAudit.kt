package com.adgeistkit.ads.placement

import android.util.Log

/**
 * Enforces that an ad unit is integrated on exactly one screen (Rule A): the first
 * screen to load a unit claims it, and any other screen fails before a request is
 * made. Keyed by screen *label*, not token, so repeat instances of one screen
 * (tabs, two product pages) stay legal. Main thread only.
 *
 * See AD_LIFECYCLE.md, "One ad unit, one screen (enforced)".
 */
internal object PlacementAudit {

    private const val TAG = "PlacementAudit"

    // adUnitId -> label of the owning screen. Held for the whole process and never
    // released: releasing on pop would let "load on Home, back, load on Settings"
    // through, which is the case that matters most.
    private val claims = mutableMapOf<String, String>()

    /** @return null when the load may proceed, else the message for `onAdFailedToLoad`. */
    fun claim(adUnitId: String, screenLabel: String): String? {
        val owner = claims.getOrPut(adUnitId) { screenLabel }
        if (owner == screenLabel) return null

        val message = "Ad unit '$adUnitId' is already placed on screen '$owner'. " +
            "An ad unit may be integrated on exactly one screen; " +
            "'$screenLabel' must use its own ad unit."
        Log.e(TAG, message)
        return message
    }
}
