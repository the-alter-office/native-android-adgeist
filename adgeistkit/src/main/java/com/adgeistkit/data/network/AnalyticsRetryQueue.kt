package com.adgeistkit.data.network

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.adgeistkit.core.device.NetworkUtils
import com.adgeistkit.data.local.AnalyticsRetryQueueStore
import com.adgeistkit.data.local.QueuedAnalyticsRequest
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

internal object AnalyticsRetryQueue {

    private const val TAG = "AnalyticsRetryQueue"

    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "adgeist-analytics-retry").apply { isDaemon = true }
    }
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var store: AnalyticsRetryQueueStore? = null
    private val inFlight = AtomicInteger(0)

    @Volatile private var foreground = false
    private var retryScheduled = false
    private var startedActivities = 0
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lifecycleCallbacks: Application.ActivityLifecycleCallbacks? = null

    // ---- Public surface ----

    fun start(context: Context, client: OkHttpClient) {
        foreground = true

        registerConnectivityListener(context, client)
        registerLifecycleListener(context, client)

        worker.execute { flush(context, client, ignoreDueTimes = true) }
    }

    fun enqueue(
        context: Context,
        client: OkHttpClient,
        url: String,
        body: String,
        failure: Response? = null,
    ) {
        val now = System.currentTimeMillis()
        val dueAt = if (failure == null) {
            0L
        } else {
            now + RetryPolicy.nextDelayMillis(
                reattempts = 0,
                retryAfterMillis = RetryPolicy.retryAfterMillis(failure, now),
            )
        }

        worker.execute {
            try {
                store(context).insertAndTrim(url, body, dueAt)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist retry item - it will not be retried", e)
                return@execute
            }
            scheduleRetry(context, client)
        }
    }

    fun flushNow(context: Context, client: OkHttpClient) {
        worker.execute { flush(context, client) }
    }

    fun shutdown(context: Context) {
        cancelScheduledRetry()
        unregisterConnectivityListener(context)
        unregisterLifecycleListener(context)
    }

    // ---- Triggers ----

    @Synchronized
    private fun registerConnectivityListener(context: Context, client: OkHttpClient) {
        if (networkCallback != null) return

        val connectivityManager = NetworkUtils.connectivityManager(context) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                worker.execute { flush(context, client) }
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

    @Synchronized
    private fun registerLifecycleListener(context: Context, client: OkHttpClient) {
        if (lifecycleCallbacks != null) return

        val application = context.applicationContext as? Application ?: return

        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (startedActivities++ == 0) {
                    foreground = true
                    worker.execute { flush(context, client) }
                }
            }

            override fun onActivityStopped(activity: Activity) {
                // A rotation dips this to zero and back, which just cancels and reschedules.
                if (--startedActivities <= 0) {
                    startedActivities = 0
                    foreground = false
                    cancelScheduledRetry()
                }
            }

            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }

        application.registerActivityLifecycleCallbacks(callbacks)
        lifecycleCallbacks = callbacks
    }

    @Synchronized
    private fun unregisterConnectivityListener(context: Context) {
        val callback = networkCallback ?: return
        try {
            NetworkUtils.connectivityManager(context)?.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
        }
        networkCallback = null
    }

    @Synchronized
    private fun unregisterLifecycleListener(context: Context) {
        val callbacks = lifecycleCallbacks ?: return
        (context.applicationContext as? Application)?.unregisterActivityLifecycleCallbacks(callbacks)
        lifecycleCallbacks = null
        startedActivities = 0
    }

    @Synchronized
    private fun scheduleRetry(context: Context, client: OkHttpClient) {
        if (retryScheduled || !foreground || !NetworkUtils.hasValidatedInternet(context)) return

        val earliest = try {
            store(context).earliestNextAttempt()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read the retry queue", e)
            return
        } ?: return

        // Floored so a row that is already due cannot spin the timer; the triggers send those.
        val delay = (earliest - System.currentTimeMillis())
            .coerceAtLeast(RetryPolicy.MIN_RETRY_INTERVAL_MS)

        retryScheduled = true
        handler.postDelayed({
            synchronized(this) { retryScheduled = false }
            worker.execute { flush(context, client) }
        }, delay)
    }

    @Synchronized
    private fun cancelScheduledRetry() {
        handler.removeCallbacksAndMessages(null)
        retryScheduled = false
    }

    // ---- Sending ----

    private fun flush(context: Context, client: OkHttpClient, ignoreDueTimes: Boolean = false) {
        if (inFlight.get() > 0) return

        val rows = try {
            val store = store(context)
            if (ignoreDueTimes) store.getAll() else store.getDue(System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read persisted retry queue", e)
            return
        }

        if (rows.isEmpty()) {
            scheduleRetry(context, client)
            return
        }

        inFlight.set(rows.size)
        rows.forEach { resend(context, client, it) }
    }

    private fun resend(context: Context, client: OkHttpClient, row: QueuedAnalyticsRequest) {
        val request = Request.Builder()
            .url(row.url)
            .header("Content-Type", "application/json")
            .post(row.body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // We never reached the server, so the row keeps its re-attempt count and due time.
                worker.execute { finishOne(context, client) }
            }

            override fun onResponse(call: Call, response: Response) {
                val now = System.currentTimeMillis()

                val retryAfter = response.use {
                    when {
                        it.isSuccessful -> null
                        RetryPolicy.isRetryable(it.code) -> RetryPolicy.retryAfterMillis(it, now) ?: 0L
                        else -> null
                    }
                }

                worker.execute {
                    applyOutcome(context, row, retryAfter, now)
                    finishOne(context, client)
                }
            }
        })
    }

    private fun applyOutcome(
        context: Context,
        row: QueuedAnalyticsRequest,
        retryAfterMillis: Long?,
        nowMillis: Long,
    ) {
        try {
            if (retryAfterMillis == null) {
                store(context).deleteById(row.id)
                return
            }

            val reattempts = row.reattempts + 1

            if (reattempts >= RetryPolicy.MAX_REATTEMPTS) {
                Log.w(TAG, "Giving up after ${RetryPolicy.MAX_REATTEMPTS} re-attempts: ${row.url}")
                store(context).deleteById(row.id)
            } else {
                store(context).markRetry(
                    row.id,
                    nowMillis + RetryPolicy.nextDelayMillis(reattempts, retryAfterMillis),
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update the retry queue", e)
        }
    }

    private fun finishOne(context: Context, client: OkHttpClient) {
        if (inFlight.decrementAndGet() > 0) return

        scheduleRetry(context, client)
    }

    // ---- Helpers ----

    private fun store(context: Context): AnalyticsRetryQueueStore =
        store ?: synchronized(this) {
            store ?: AnalyticsRetryQueueStore(context.applicationContext).also { store = it }
        }
}
