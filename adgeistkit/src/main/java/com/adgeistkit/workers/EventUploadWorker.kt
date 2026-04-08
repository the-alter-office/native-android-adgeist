package com.adgeistkit.workers

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.adgeistkit.logging.EventBuffer
import com.adgeistkit.logging.SdkShield
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream

class EventUploadWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val TAG = "EventUploadWorker"
        const val KEY_BACKEND_DOMAIN = "backend_domain"
        const val KEY_APP_ID = "app_id"
        private const val UPLOAD_ENDPOINT = "/v1/sdk-events"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    override fun doWork(): Result {
        return SdkShield.runSafelyWithReturn("EventUploadWorker.doWork", Result.retry()) {
            doWorkInternal()
        }
    }

    private fun doWorkInternal(): Result {
        val backendDomain = inputData.getString(KEY_BACKEND_DOMAIN)
        val appId = inputData.getString(KEY_APP_ID) ?: ""

        if (backendDomain.isNullOrEmpty()) {
            Log.e(TAG, "Missing backend domain, cannot upload")
            return Result.failure()
        }

        // Ensure EventBuffer is initialized even if AdgeistCore hasn't run
        // (e.g. WorkManager restarted the process for a periodic upload)
        EventBuffer.initialize(applicationContext)

        val events = EventBuffer.readAll()
        if (events.isEmpty()) {
            Log.d(TAG, "No events to upload")
            return Result.success()
        }

        val uploadCount = events.size
        Log.d(TAG, "Uploading $uploadCount events")

        return try {
            val jsonArray = gson.toJson(events)
            val compressed = gzip(jsonArray.toByteArray(Charsets.UTF_8))

            val request = Request.Builder()
                .url("$backendDomain$UPLOAD_ENDPOINT")
                .header("Content-Type", "application/json")
                .header("Content-Encoding", "gzip")
                .header("X-App-Id", appId)
                .post(compressed.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()

            response.use {
                when {
                    response.isSuccessful -> {
                        Log.i(TAG, "Uploaded $uploadCount events successfully")
                        EventBuffer.removeFirst(uploadCount)
                        Result.success()
                    }
                    response.code in 400..499 -> {
                        Log.e(TAG, "Client error (${response.code}), dropping batch")
                        EventBuffer.removeFirst(uploadCount)
                        Result.failure()
                    }
                    else -> {
                        Log.w(TAG, "Server error (${response.code}), will retry")
                        Result.retry()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed, will retry: ${e.message}")
            Result.retry()
        }
    }

    private fun gzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream(data.size)
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }
}
