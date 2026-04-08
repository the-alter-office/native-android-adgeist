package com.adgeistkit.logging

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.File
import java.io.IOException

object EventBuffer {

    private const val TAG = "EventBuffer"
    private const val FILE_NAME = "adgeist_events.ndjson"
    private const val MAX_EVENTS = 200

    private val gson = Gson()
    private val lock = Any()

    private var file: File? = null
    private var isInitialized = false

    fun initialize(context: Context) {
        synchronized(lock) {
            file = File(context.cacheDir, FILE_NAME)
            isInitialized = true
            Log.d(TAG, "EventBuffer initialized: ${file?.absolutePath}")
        }
    }

    fun write(event: SdkEvent) {
        if (!isInitialized) return

        synchronized(lock) {
            try {
                val jsonLine = gson.toJson(event)
                file?.appendText(jsonLine + "\n")
                trimIfNeeded()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to write event: ${e.message}")
            }
        }
    }

    fun readAll(): List<SdkEvent> {
        if (!isInitialized) return emptyList()

        synchronized(lock) {
            val bufferFile = file ?: return emptyList()
            if (!bufferFile.exists()) return emptyList()

            return try {
                bufferFile.readLines()
                    .filter { it.isNotBlank() }
                    .mapNotNull { line ->
                        try {
                            gson.fromJson(line, SdkEvent::class.java)
                        } catch (e: JsonSyntaxException) {
                            Log.w(TAG, "Skipping corrupted line")
                            null
                        }
                    }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to read events: ${e.message}")
                emptyList()
            }
        }
    }

    fun clear() {
        if (!isInitialized) return

        synchronized(lock) {
            try {
                file?.delete()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to clear buffer: ${e.message}")
            }
        }
    }

    fun eventCount(): Int {
        if (!isInitialized) return 0

        synchronized(lock) {
            val bufferFile = file ?: return 0
            if (!bufferFile.exists()) return 0

            return try {
                bufferFile.readLines().count { it.isNotBlank() }
            } catch (e: IOException) {
                0
            }
        }
    }

    private fun trimIfNeeded() {
        try {
            val bufferFile = file ?: return
            if (!bufferFile.exists()) return

            val lines = bufferFile.readLines().filter { it.isNotBlank() }
            if (lines.size > MAX_EVENTS) {
                val trimmed = lines.takeLast(MAX_EVENTS)
                bufferFile.writeText(trimmed.joinToString("\n") + "\n")
                Log.d(TAG, "Buffer trimmed: ${lines.size} → $MAX_EVENTS events")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to trim buffer: ${e.message}")
        }
    }
}
