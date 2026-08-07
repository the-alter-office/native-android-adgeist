package com.adgeistkit.ads.identity

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import java.util.UUID
import com.adgeistkit.ads.session.AdSessionStore

/**
 * Identity of one screen *instance*, retained in that screen's ViewModelStore -
 * the only object Android hands back to a recreated screen, so the only reliable
 * place to keep it. [screenToken] survives recreation and differs per instance;
 * [onCleared] fires exactly once, when the screen is finished for good.
 */
internal class AdSlotToken : ViewModel() {

    val screenToken: String = UUID.randomUUID().toString()

    // Holds no View, Fragment or Activity: this object outlives configuration
    // changes, so capturing one would pin a destroyed Activity.
    override fun onCleared() {
        Log.d(TAG, "Screen '$screenToken' finished - destroying its ad sessions")
        AdSessionStore.destroyAllForScreen(screenToken)
    }

    companion object {
        private const val TAG = "AdSlotToken"

        // Explicit key and factory so the SDK never collides with a host's own
        // ViewModel and never depends on the owner supplying a default factory.
        private const val STORE_KEY = "com.adgeistkit.ads.AdSlotToken"

        private val FACTORY = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AdSlotToken() as T
        }

        fun of(owner: ViewModelStoreOwner): AdSlotToken =
            ViewModelProvider(owner.viewModelStore, FACTORY)[STORE_KEY, AdSlotToken::class.java]
    }
}
