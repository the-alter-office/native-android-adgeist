package com.adgeistkit.ads.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.adgeistkit.constants.General
import java.util.UUID

internal class AdViewModel : ViewModel() {

    companion object {
        private const val TAG = "AdViewModel"

        // Explicit key and factory so the SDK never collides with a host's own
        // ViewModel and never depends on the owner supplying a default factory.
        private val FACTORY = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AdViewModel() as T
        }

        fun of(owner: ViewModelStoreOwner): AdViewModel =
            ViewModelProvider(owner.viewModelStore, FACTORY)[General.Storage.VIEW_MODEL_STORE_KEY, AdViewModel::class.java]
    }

    /**
     * Distinguishes one screen instance from another in logs - two tabs of the same
     * fragment, or the same screen twice on the back stack.
     */
    val screenId: String = UUID.randomUUID().toString()

    private val ads = mutableMapOf<String, RetainedAd>()

    fun retained(adUnitId: String): RetainedAd? = ads[adUnitId]

    fun retain(adUnitId: String, ad: RetainedAd) {
        ads[adUnitId] = ad
    }

    fun release(adUnitId: String) {
        ads.remove(adUnitId)
    }

    override fun onCleared() {
        ads.clear()
    }
}
