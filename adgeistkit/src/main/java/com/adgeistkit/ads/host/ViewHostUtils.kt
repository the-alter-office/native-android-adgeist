package com.adgeistkit.ads.host

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.adgeistkit.ads.viewmodel.AdViewModel
import com.adgeistkit.constants.Logs

private const val TAG = "ViewHostUtils"

@Volatile private var warnedActivityScope = false

internal fun Context.findActivity(): Activity? {
    var current: Context? = this

    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    
    return null
}

internal fun View.findAdViewModel(
    watchFragmentLifecycle: Boolean,
    injectedOwner: ViewModelStoreOwner? = null,
): AdViewModel? {
    val owner = injectedOwner
        ?: findScreenOwner(watchFragmentLifecycle)
        ?: return null

    if (injectedOwner == null && watchFragmentLifecycle && owner is Activity) {
        warnActivityScope()
    }

    return try {
        AdViewModel.of(owner)
    } catch (e: Exception) {
        Log.w(TAG, Logs.Warning.VIEW_MODEL_UNREACHABLE, e)
        null
    }
}

private fun View.findScreenOwner(watchFragmentLifecycle: Boolean): ViewModelStoreOwner? {
    if (!watchFragmentLifecycle) {
        return context.findActivity() as? ViewModelStoreOwner
    }

    val treeOwner = findViewTreeViewModelStoreOwner()

    // Compose gap: AndroidView bridges LifecycleOwner into the view tree but not
    // ViewModelStoreOwner, so treeOwner is only the Activity while the real screen scope is
    // the bridged lifecycle owner - a NavBackStackEntry, which is a ViewModelStoreOwner too.
    if (treeOwner == null || treeOwner is Activity) {
        (findViewTreeLifecycleOwner() as? ViewModelStoreOwner)
            ?.takeIf { it !== treeOwner }
            ?.let { return it }
    }

    if (treeOwner != null) return treeOwner

    if (!isAttachedToWindow) return null

    return context.findActivity() as? ViewModelStoreOwner
}

private fun warnActivityScope() {
    if (warnedActivityScope) return
    warnedActivityScope = true

    Log.w(TAG, Logs.Warning.ACTIVITY_SCOPE_RETENTION)
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
            Log.e(TAG, Logs.Error.imeReleaseFailed(e.message), e)
        }
    }
}
