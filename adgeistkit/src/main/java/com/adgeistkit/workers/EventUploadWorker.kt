package com.adgeistkit.workers

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.adgeistkit.data.network.PostHogClient
import com.adgeistkit.logging.EventBuffer
import com.adgeistkit.logging.SdkEvent
import com.adgeistkit.logging.SdkShield
import com.google.gson.Gson

class EventUploadWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val TAG = "EventUploadWorker"
    }

    private val gson = Gson()

    override fun doWork(): Result {
        return SdkShield.runSafelyWithReturn("EventUploadWorker.doWork", Result.retry()) {
            doWorkInternal()
        }
    }

    private fun doWorkInternal(): Result {
        // Ensure EventBuffer is initialized even if AdgeistCore hasn't run
        // (e.g. WorkManager restarted the process for a periodic upload)
        EventBuffer.initialize(applicationContext)

        val events = EventBuffer.readAll()
        if (events.isEmpty()) {
            Log.d(TAG, "No events to upload")
            return Result.success()
        }

        val uploadCount = events.size
        Log.d(TAG, "Uploading $uploadCount events to PostHog")

        val batch = events.map { toPostHogEvent(it) }

        return if (PostHogClient.captureBatch(batch)) {
            Log.i(TAG, "Uploaded $uploadCount events to PostHog successfully")
            EventBuffer.removeFirst(uploadCount)
            Result.success()
        } else {
            Log.w(TAG, "PostHog upload failed, will retry")
            Result.retry()
        }
    }

    private fun toPostHogEvent(event: SdkEvent): Map<String, Any?> {
        val deviceId = (event.context["user"] as? Map<*, *>)?.get("deviceId") as? String

        @Suppress("UNCHECKED_CAST")
        val properties = gson.fromJson(gson.toJson(event), Map::class.java) as Map<String, Any?>

        return mapOf(
            "event" to "sdk_error_captured",
            "distinct_id" to (deviceId ?: "unknown"),
            "properties" to properties
        )
    }
}
