package com.adgeistkit.ads.viewmodel

import com.adgeistkit.ads.tracking.AdTrackingState
import com.adgeistkit.data.models.FixedAdResponse

/**
 * One ad kept alive across screen recreation: the server response it was built from,
 * plus what it has already reported. Everything needed to show the same ad again
 * without buying another one, and without counting it twice.
 */
internal class RetainedAd(
    val response: FixedAdResponse,
    val tracking: AdTrackingState = AdTrackingState(),
)
