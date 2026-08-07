package com.adgeistkit.ads.host

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner

private const val TAG = "ViewHostUtils"

/** The Fragment this view sits in, or null when it is not inside one. */
internal fun View.findHostFragment(): Fragment? {
    return try {
        FragmentManager.findFragment(this)
    } catch (e: Exception) {
        null
    }
}

/** Walks the Context chain for the hosting Activity. */
internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** Walks the Context chain for a LifecycleOwner, for hosts that are not Activities. */
internal fun Context.findLifecycleOwner(): LifecycleOwner? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        current = current.baseContext
    }
    return null
}

internal fun View.pxToDp(px: Int): Int = (px / resources.displayMetrics.density).toInt()

/**
 * Recovers key dispatch after an ad WebView is hidden.
 *
 * A WebView kept alive while its screen is covered can leave the IME bound to a
 * dead input connection. Key events pass through the IME stage before the view
 * hierarchy, so they die there - and that includes the system BACK key, which then
 * stops working app-wide. Forcing the IME to rebind to whatever now has focus (or
 * to finish input) clears it.
 */
internal fun View.releaseImeSession(handler: Handler?) {
    val activity = context.findActivity() ?: return
    val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE)
        as? InputMethodManager ?: return
    // Posted so it runs after the detach pass, once window focus has settled
    handler?.post {
        try {
            val focused = activity.currentFocus
            if (focused != null) {
                imm.restartInput(focused)
            } else {
                imm.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing IME session: ${e.message}", e)
        }
    }
}
