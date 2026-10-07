package com.adgeistkit.ads

import com.adgeistkit.utilities.AdgeistEmbedderApi

public abstract class AdListener {
    public open fun onAdEvent(event: AdgeistEvent) {
    }

    @AdgeistEmbedderApi
    public open fun onAdSizeResolved(adSize: AdSize) {
    }
}
