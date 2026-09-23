package com.adgeistkit.data.network

import android.os.SystemClock
import com.adgeistkit.utilities.logD
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

internal object ConnectionWarmer {

    private const val TAG = "ConnectionWarmer"
    private const val MIN_INTERVAL_MS = 60_000L

    private val lastWarmed = AtomicLong(0)

    fun warm(baseUrl: String) {
        val now = SystemClock.elapsedRealtime()
        val previous = lastWarmed.get()

        if (previous != 0L && now - previous < MIN_INTERVAL_MS) return
        if (!lastWarmed.compareAndSet(previous, now)) return

        val request = try {
            Request.Builder().url(baseUrl).head().build()
        } catch (_: IllegalArgumentException) {
            return
        }

        NetworkModule.httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                logD(TAG) { "Warm-up failed: ${e.message}" }
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
                logD(TAG) { "Warm-up complete (${response.code})" }
            }
        })
    }
}
