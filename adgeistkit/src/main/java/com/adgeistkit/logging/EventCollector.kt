package com.adgeistkit.logging

import android.os.Build
import android.util.Log
import com.adgeistkit.BuildConfig
import java.util.Collections

object EventCollector {

    private const val TAG = "EventCollector"
    private const val MAX_EVENTS = 200
    private const val MAX_STACK_FRAMES = 5

    private val events: MutableList<SdkEvent> = Collections.synchronizedList(mutableListOf())

    private var appId: String = ""
    private var packageName: String = ""
    private var isInitialized = false

    fun initialize(appId: String, packageName: String) {
        this.appId = appId
        this.packageName = packageName
        this.isInitialized = true
        Log.d(TAG, "EventCollector initialized")
    }

    fun logError(tag: String, t: Throwable) {
        val sdkPackage = BuildConfig.LIBRARY_PACKAGE_NAME
        val stackFrames = t.stackTrace
            .filter { it.className.startsWith(sdkPackage) }
            .take(MAX_STACK_FRAMES)
            .map { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }

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
            sdkVersion = BuildConfig.VERSION_NAME,
            deviceModel = "${Build.MANUFACTURER}/${Build.MODEL}",
            osVersion = Build.VERSION.RELEASE,
            appId = appId,
            packageName = packageName,
            params = params
        )

        synchronized(events) {
            if (events.size >= MAX_EVENTS) {
                events.removeAt(0)
            }
            events.add(event)
        }

        Log.d(TAG, "[${event.event}] ${event.params}")
    }
}
