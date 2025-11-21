package com.adgeistkit.ads

abstract class AdListener {
    open fun onAdClicked() {
    }

    open fun onAdClosed() {
    }

    open fun onAdFailedToLoad(var1: String) {
    }

    open fun onAdImpression() {
    }

    open fun onAdLoaded() {
    }

    open fun onAdOpened() {
    }
}