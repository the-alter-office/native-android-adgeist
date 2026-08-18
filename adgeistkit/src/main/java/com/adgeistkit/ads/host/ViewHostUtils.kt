package com.adgeistkit.ads.host

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.adgeistkit.ads.viewmodel.AdViewModel

private const val TAG = "ViewHostUtils"

internal fun Context.findActivity(): Activity? {
    var current: Context? = this

    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    
    return null
}

internal fun View.findAdViewModel(watchFragmentLifecycle: Boolean): AdViewModel? {
    val owner = findScreenOwner(watchFragmentLifecycle) ?: return null

    return try {
        AdViewModel.of(owner)
    } catch (e: Exception) {
        Log.w(TAG, "Could not reach the screen's ViewModel; this ad will not be retained", e)
        null
    }
}

private fun View.findScreenOwner(watchFragmentLifecycle: Boolean): ViewModelStoreOwner? {
    if (!watchFragmentLifecycle) {
        return context.findActivity() as? ViewModelStoreOwner
    }

    findViewTreeViewModelStoreOwner()?.let { return it }

    if (!isAttachedToWindow) return null

    return context.findActivity() as? ViewModelStoreOwner
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
