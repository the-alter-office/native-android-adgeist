package com.adgeistkit

import android.util.Log

internal object AdgeistLog {

    @Volatile
    var enabled: Boolean = false
}

internal inline fun logD(tag: String, message: () -> String) {
    if (AdgeistLog.enabled) Log.d(tag, message())
}

internal inline fun logI(tag: String, message: () -> String) {
    if (AdgeistLog.enabled) Log.i(tag, message())
}

internal inline fun logV(tag: String, message: () -> String) {
    if (AdgeistLog.enabled) Log.v(tag, message())
}
