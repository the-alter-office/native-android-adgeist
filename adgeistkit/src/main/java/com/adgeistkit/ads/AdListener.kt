package com.adgeistkit.ads

public abstract class AdListener {
    public open fun onAdClicked() {
    }

    public open fun onAdClosed() {
    }

    public open fun onAdFailedToLoad(var1: String) {
    }

    public open fun onAdImpression() {
    }

    public open fun onAdLoaded() {
    }

    public open fun onAdOpened() {
    }
}