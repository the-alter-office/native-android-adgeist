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
import androidx.work.workDataOf
import com.adgeistkit.workers.EventUploadWorker
import java.util.concurrent.TimeUnit

object EventUploadScheduler {

    private const val TAG = "EventUploadScheduler"
    private const val THRESHOLD = 50
    private const val PERIODIC_HOURS = 4L
    private const val UNIQUE_PERIODIC = "adgeist_event_upload_periodic"
    private const val UNIQUE_IMMEDIATE = "adgeist_event_upload_immediate"

    private var backendDomain: String = ""
    private var appId: String = ""
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

    fun initialize(context: Context, backendDomain: String, appId: String) {
        this.appContext = context.applicationContext
        this.backendDomain = backendDomain
        this.appId = appId
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
            .setInputData(buildInputData())
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

        Log.d(TAG, "Enqueueing immediate upload ($count events)")

        val request = OneTimeWorkRequestBuilder<EventUploadWorker>()
            .setConstraints(networkConstraint)
            .setInputData(buildInputData())
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

    private fun buildInputData() = workDataOf(
        EventUploadWorker.KEY_BACKEND_DOMAIN to backendDomain,
        EventUploadWorker.KEY_APP_ID to appId
    )
}
