package com.adgeistkit.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Retries analytics posts that failed for lack of connectivity, once connectivity
 * returns.
 */
internal object AnalyticsRetryQueue {

    private const val TAG = "AnalyticsRetryQueue"

    // Bounds memory if the device stays offline for a long time; oldest events
    // are dropped first since they are the least relevant by the time this fires.
    private const val MAX_QUEUED = 200

    private val pending = ConcurrentLinkedQueue<Request>()

    // Resends in flight from the current flush - the listener must not be torn
    // down while one of these could still fail and need to re-queue itself.
    private val inFlight = AtomicInteger(0)

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    @Synchronized
    fun enqueue(context: Context, client: OkHttpClient, request: Request) {
        if (pending.size >= MAX_QUEUED) {
            Log.w(TAG, "Retry queue full ($MAX_QUEUED) - dropping the oldest queued event")
            pending.poll()
        }
        pending.add(request)
        startListening(context, client)
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
