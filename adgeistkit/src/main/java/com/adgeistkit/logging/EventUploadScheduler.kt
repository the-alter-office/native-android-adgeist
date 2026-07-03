package com.adgeistkit.logging

import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.adgeistkit.workers.EventUploadWorker
import java.util.concurrent.TimeUnit

object EventUploadScheduler {

    private const val TAG = "EventUploadScheduler"
    private const val THRESHOLD = 5
    private const val PERIODIC_HOURS = 4L
    private const val UNIQUE_PERIODIC = "adgeist_event_upload_periodic"
    private const val UNIQUE_IMMEDIATE = "adgeist_event_upload_immediate"

    private lateinit var appContext: Context
    private var isInitialized = false

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            enqueueImmediate()
        }
    }

    fun initialize(context: Context) {
        this.appContext = context.applicationContext
        this.isInitialized = true

        startPeriodicUpload(context)
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)

        Log.d(TAG, "EventUploadScheduler initialized")
    }

    fun checkThreshold() {
        if (!isInitialized) return
        if (EventBuffer.eventCount() >= THRESHOLD) {
            enqueueImmediate()
        }
    }

    private fun startPeriodicUpload(context: Context) {
        val request = PeriodicWorkRequestBuilder<EventUploadWorker>(PERIODIC_HOURS, TimeUnit.HOURS)
            .setConstraints(networkConstraint)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun enqueueImmediate() {
        if (!isInitialized) return

        val count = EventBuffer.eventCount()
        if (count == 0) return

        val request = OneTimeWorkRequestBuilder<EventUploadWorker>()
            .setConstraints(networkConstraint)
            .build()

        try {
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                UNIQUE_IMMEDIATE,
                ExistingWorkPolicy.REPLACE,
                request
            )
        } catch (e: IllegalStateException) {
            Log.w(TAG, "WorkManager not initialized, skipping upload")
        }
    }
}
