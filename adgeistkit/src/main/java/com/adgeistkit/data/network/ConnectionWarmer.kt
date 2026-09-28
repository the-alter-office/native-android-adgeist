package com.adgeistkit.data.network

import android.os.SystemClock
import com.adgeistkit.constants.General
import com.adgeistkit.constants.Logs
import com.adgeistkit.utilities.logD
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

internal object ConnectionWarmer {

    private const val TAG = "ConnectionWarmer"

    private val lastWarmed = AtomicLong(0)

    fun warm(baseUrl: String) {
        val now = SystemClock.elapsedRealtime()
        val previous = lastWarmed.get()

        if (previous != 0L && now - previous < General.Timing.CONNECTION_WARM_MIN_INTERVAL_MS) return
        if (!lastWarmed.compareAndSet(previous, now)) return

        val request = try {
            Request.Builder().url(baseUrl).head().build()
        } catch (_: IllegalArgumentException) {
            return
        }

        NetworkModule.httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                logD(TAG) { Logs.Debug.warmUpFailed(e.message) }
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
                logD(TAG) { Logs.Debug.warmUpComplete(response.code) }
            }
        })
    }
}
