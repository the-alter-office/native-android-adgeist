package com.adgeistkit.ads.identity

import android.util.Log
import android.view.View
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.adgeistkit.ads.host.findHostFragment
import com.adgeistkit.ads.host.findActivity

/**
 * Resolves and holds the two identifiers every ad slot needs: [screenToken] for the
 * screen *instance* (keys the session store) and [screenLabel] for the screen
 * *class or route* (keys the placement guard and reporting).
 *
 * See AD_LIFECYCLE.md, "How the SDK knows which screen it is on", and [AdSlotToken].
 */
internal class AdSlotIdentity(private val view: View) {

    companion object {
        private const val TAG = "AdSlotIdentity"

        /** Label used when neither a fragment nor an activity can be found. */
        const val LABEL_UNKNOWN = "unknown"

        private const val SLOT_UNNAMED = "unnamed AdView"
    }

    // ---- Resolved identity ----

    var screenToken: String? = null
        private set

    var screenLabel: String? = null
        private set

    private var resolved = false

    // ---- Resolution ----

    /**
     * Resolves identity once, needing a ViewModelStoreOwner in the view tree.
     *
     * @param placementId host-supplied label override; empty to auto-derive.
     * @param watchFragmentLifecycle false scopes identity to the Activity.
     * @return false when not resolvable yet, so the caller retries after attach.
     */
    fun resolve(placementId: String, watchFragmentLifecycle: Boolean): Boolean {
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
        screenLabel = resolveScreenLabel(placementId, watchFragmentLifecycle)
        resolved = true
        Log.d(TAG, "Slot identity resolved - label=$screenLabel token=$screenToken")
        return true
    }

    /** Accepts identity from a host the SDK cannot infer it from (Compose, RN). */
    fun set(token: String, label: String) {
        screenToken = token
        screenLabel = label
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

    private fun resolveScreenLabel(placementId: String, watchFragmentLifecycle: Boolean): String {
        if (placementId.isNotEmpty()) return "custom:$placementId"

        if (watchFragmentLifecycle) {
            view.findHostFragment()?.let { return "frag:" + it.javaClass.name }
        }
        view.context.findActivity()?.let { return "act:" + it.javaClass.name }
        return LABEL_UNKNOWN
    }

    // ---- Derived names ----

    /** Readable name for this slot, used in duplicate errors and reporting. */
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
