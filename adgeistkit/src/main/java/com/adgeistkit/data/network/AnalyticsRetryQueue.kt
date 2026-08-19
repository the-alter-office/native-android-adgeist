package com.adgeistkit.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal object AnalyticsRetryQueue {

    private const val TAG = "AnalyticsRetryQueue"

    // Oldest events are dropped first once the backend is down long enough to fill this.
    private const val MAX_QUEUED = 200

    private const val RETRY_INTERVAL_MS = 30_000L

    private val pending = ConcurrentLinkedQueue<Request>()

    // Resends in flight; the network listener stays registered while this is nonzero.
    private val inFlight = AtomicInteger(0)

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val handler = Handler(Looper.getMainLooper())
    private var retryScheduled = false

    @Synchronized
    fun enqueue(context: Context, client: OkHttpClient, request: Request) {
        if (pending.size >= MAX_QUEUED) {
            Log.w(TAG, "Retry queue full ($MAX_QUEUED) - dropping the oldest queued event")
            pending.poll()
        }
        pending.add(request)
        startListening(context, client)
        scheduleRetry(context, client)
    }

    /** Fallback for failures that aren't connectivity drops, so they aren't stuck forever. */
    @Synchronized
    private fun scheduleRetry(context: Context, client: OkHttpClient) {
        if (retryScheduled) return
        retryScheduled = true
        handler.postDelayed({
            retryScheduled = false
            flush(context, client)
            if (pending.isNotEmpty()) {
                scheduleRetry(context, client)
            }
        }, RETRY_INTERVAL_MS)
    }

    @Synchronized
    private fun startListening(context: Context, client: OkHttpClient) {
        if (networkCallback != null) return

        val connectivityManager = connectivityManager(context) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                flush(context, client)
            }
        }
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(networkRequest, callback)
            networkCallback = callback
        } catch (e: Exception) {
            Log.e(TAG, "Could not register connectivity callback", e)
        }
    }

    /** Only tears the listener down once nothing is queued and nothing is in flight. */
    @Synchronized
    private fun maybeStopListening(context: Context) {
        if (pending.isNotEmpty() || inFlight.get() > 0) return

        val callback = networkCallback ?: return
        try {
            connectivityManager(context)?.unregisterNetworkCallback(callback)
        } catch (e: Exception) { /* already unregistered */
        }
        networkCallback = null
    }

    private fun connectivityManager(context: Context): ConnectivityManager? =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager

    /** Called from [com.adgeistkit.AdgeistCore.destroy] - nothing else can stop this singleton. */
    @Synchronized
    fun shutdown(context: Context) {
        handler.removeCallbacksAndMessages(null)
        retryScheduled = false
        pending.clear()
        networkCallback?.let {
            try {
                connectivityManager(context)?.unregisterNetworkCallback(it)
            } catch (_: Exception) { /* already unregistered */
            }
        }
        networkCallback = null
    }

    private fun flush(context: Context, client: OkHttpClient) {
        var request = pending.poll()
        while (request != null) {
            inFlight.incrementAndGet()
            resend(context, client, request)
            request = pending.poll()
        }
        maybeStopListening(context)
    }

    private fun resend(context: Context, client: OkHttpClient, request: Request) {
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d(TAG, "Retry still failing: ${e.message}")
                pending.add(request)
                inFlight.decrementAndGet()
                maybeStopListening(context)
                scheduleRetry(context, client)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        Log.d(TAG, "Retry rejected by server (${it.code}) - not retried again")
                    }
                }
                inFlight.decrementAndGet()
                maybeStopListening(context)
            }
        })
    }
}
