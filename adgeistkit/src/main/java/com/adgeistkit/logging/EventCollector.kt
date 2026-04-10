package com.adgeistkit.logging

import android.util.Log
import java.util.Collections

object EventCollector {

    private const val TAG = "EventCollector"
    private const val MAX_EVENTS = 200
    private const val MAX_STACK_FRAMES = 5

    private val events: MutableList<SdkEvent> = Collections.synchronizedList(mutableListOf())

    private var isInitialized = false

    fun initialize() {
        this.isInitialized = true
        Log.d(TAG, "EventCollector initialized")
    }

    fun logError(tag: String, t: Throwable) {
        val stackFrames = t.stackTrace.take(MAX_STACK_FRAMES)

        val params = mutableMapOf<String, Any>(
            "component" to tag,
            "exceptionClass" to t.javaClass.simpleName,
            "message" to (t.message ?: ""),
            "stackTrace" to stackFrames,
            "threadName" to Thread.currentThread().name
        )

        addEvent("sdk_error", params)
    }

    fun logEvent(name: String, params: Map<String, Any> = emptyMap()) {
        addEvent(name, params)
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

    private fun addEvent(name: String, params: Map<String, Any>) {
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

        Log.d(TAG, "${event}")
    }
}
