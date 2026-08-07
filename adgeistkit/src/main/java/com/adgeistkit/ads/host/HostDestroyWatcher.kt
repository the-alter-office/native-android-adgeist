package com.adgeistkit.ads.host

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner

/**
 * Fires [onHostDestroyed] when the ad's host view is destroyed, for any reason.
 */
internal class HostDestroyWatcher(
    private val view: View,
    private val onHostDestroyed: () -> Unit,
) {

    private companion object {
        private const val TAG = "HostDestroyWatcher"
    }

    private var lifecycleObserver: DefaultLifecycleObserver? = null
    private var observedLifecycle: Lifecycle? = null
    private var activityCallbacks: Application.ActivityLifecycleCallbacks? = null
    private var observedApplication: Application? = null

    /** True while watching the view-tree owner rather than the whole Activity. */
    private var watchingViewOwner = false

    fun register(watchFragmentLifecycle: Boolean) {
        val viewOwner = if (watchFragmentLifecycle) view.findViewTreeLifecycleOwner() else null

        if (observedLifecycle != null || activityCallbacks != null) {
            // Upgrade an activity-level watcher once the view is inside a screen's
            // view tree: registration can happen before attach, and the view-level
            // owner is the more precise park signal
            if (watchingViewOwner || viewOwner == null) return
            unregister()
        }

        val hostOwner: LifecycleOwner? = viewOwner
            ?: (view.context.findActivity() as? LifecycleOwner)
            ?: view.context.findLifecycleOwner()

        if (hostOwner != null) {
            val observer = object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    // Unregister first: the callback may bail on its own guards,
                    // which would leave this fired observer lingering
                    unregister()
                    Log.d(TAG, "Host view destroyed - notifying ad")
                    onHostDestroyed()
                }
            }
            hostOwner.lifecycle.addObserver(observer)
            lifecycleObserver = observer
            observedLifecycle = hostOwner.lifecycle
            watchingViewOwner = hostOwner === viewOwner
            return
        }

        val hostActivity = view.context.findActivity() ?: return
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityDestroyed(destroyed: Activity) {
                if (destroyed === hostActivity) {
                    Log.d(TAG, "Host activity destroyed - notifying ad")
                    onHostDestroyed()
                }
            }

            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
        }
        hostActivity.application.registerActivityLifecycleCallbacks(callbacks)
        activityCallbacks = callbacks
        observedApplication = hostActivity.application
    }

    fun unregister() {
        lifecycleObserver?.let { observedLifecycle?.removeObserver(it) }
        lifecycleObserver = null
        observedLifecycle = null
        watchingViewOwner = false

        activityCallbacks?.let { observedApplication?.unregisterActivityLifecycleCallbacks(it) }
        activityCallbacks = null
        observedApplication = null
    }
}
