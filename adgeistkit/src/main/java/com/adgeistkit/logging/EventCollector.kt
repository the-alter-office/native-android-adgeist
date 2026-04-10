package com.adgeistkit.logging

import android.util.Log
import java.util.Collections

object EventCollector {

    private const val TAG = "EventCollector"
    private const val MAX_EVENTS = 200

    private val events: MutableList<SdkEvent> = Collections.synchronizedList(mutableListOf())

    private var isInitialized = false

    fun initialize() {
        this.isInitialized = true
        Log.d(TAG, "EventCollector initialized")
    }

    fun logError(tag: String, t: Throwable) {
        val errorPayload = ExceptionPayloadBuilder.build(tag, t)

        val event = SdkEvent(
            type = errorPayload["type"] as? String,
            severity = errorPayload["severity"] as? String,
            errorCode = errorPayload["errorCode"] as? String,
            errorCategory = errorPayload["errorCategory"] as? String,
            exception = errorPayload["exception"] as? Map<String, Any?>,
            context = ContextCollector.getFullContext(),
            detectionMethod = errorPayload["detectionMethod"] as? String,
            timestamp = System.currentTimeMillis(),
        )

        synchronized(events) {
            if (events.size >= MAX_EVENTS) {
                events.removeAt(0)
            }
            events.add(event)
        }

        EventBuffer.write(event)
        EventUploadScheduler.checkThreshold()

        Log.d(TAG, "$event")
    }

    fun logEvent(name: String, params: Map<String, Any> = emptyMap()) {
        val event = SdkEvent(
            event = name,
            timestamp = System.currentTimeMillis(),
            params = params,
            context = ContextCollector.getFullContext()
        )

        synchronized(events) {
            if (events.size >= MAX_EVENTS) {
                events.removeAt(0)
            }
            events.add(event)
        }

        EventBuffer.write(event)
        EventUploadScheduler.checkThreshold()

        Log.d(TAG, "[${event.event}] ${event.params}")
    }

    fun getEvents(): List<SdkEvent> {
        synchronized(events) {
            return events.toList()
        }
    }

    fun clear() {
        synchronized(events) {
            events.clear()
        }
    }

}
