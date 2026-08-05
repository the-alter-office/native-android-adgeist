package com.adgeistkit.ads.identity

import android.util.Log
import android.view.View
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.adgeistkit.ads.host.findActivity

/**
 * Resolves and holds [screenToken]: the identity of the screen *instance* this ad
 * slot belongs to, which keys the session store so a session is only ever resumed
 * by the screen instance that created it.
 *
 * See AD_LIFECYCLE.md, "How the SDK knows which screen it is on", and [AdSlotToken].
 */
internal class AdSlotIdentity(private val view: View) {

    companion object {
        private const val TAG = "AdSlotIdentity"
        private const val SLOT_UNNAMED = "unnamed AdView"
    }

    // ---- Resolved identity ----

    var screenToken: String? = null
        private set

    private var resolved = false

    // ---- Resolution ----

    /**
     * Resolves identity once, needing a ViewModelStoreOwner in the view tree.
     *
     * @param watchFragmentLifecycle false scopes identity to the Activity.
     * @return false when not resolvable yet, so the caller retries after attach.
     */
    fun resolve(watchFragmentLifecycle: Boolean): Boolean {
        if (resolved) return true

        val owner = findSlotOwner(watchFragmentLifecycle) ?: return false
        screenToken = try {
            AdSlotToken.of(owner).screenToken
        } catch (e: Exception) {
            // A detached or already-destroyed fragment can refuse its
            // ViewModelStore; fall back to the legacy key rather than crashing
            // the host app.
            Log.w(TAG, "Could not resolve a screen token; falling back to view id", e)
            null
        }
        resolved = true
        Log.d(TAG, "Slot identity resolved - token=$screenToken")
        return true
    }

    /** Accepts identity from a host the SDK cannot infer it from (Compose, RN). */
    fun set(token: String) {
        screenToken = token
        resolved = true
    }

    // The store that defines "this screen": the nearest view-tree owner, which is
    // the host Fragment, or the Activity when there is no fragment.
    private fun findSlotOwner(watchFragmentLifecycle: Boolean): ViewModelStoreOwner? {
        if (!watchFragmentLifecycle) {
            return view.context.findActivity() as? ViewModelStoreOwner
        }
        return view.findViewTreeViewModelStoreOwner()
            ?: view.context.findActivity() as? ViewModelStoreOwner
    }

    // ---- Derived names ----

    /** Readable name for this slot, used in duplicate-slot error messages. */
    fun slotLabel(): String = resourceEntryName()?.let { "R.id.$it" } ?: SLOT_UNNAMED

    /** @return null when the slot has no identity at all, so gets no retention. */
    fun sessionKey(adUnitId: String): String? {
        screenToken?.let { return "$adUnitId|$it" }

        // No ViewModelStoreOwner anywhere (bare Activity, or an AdView added
        // straight to a window): these sessions have no token, so the store evicts
        // them on a timeout and they get no cross-screen persistence.
        return resourceEntryName()?.let { "$adUnitId|vid:$it" }
    }

    private fun resourceEntryName(): String? {
        if (view.id == View.NO_ID) return null
        return try {
            view.resources.getResourceEntryName(view.id)
        } catch (e: Exception) {
            null // generated/unnamed id
        }
    }
}
