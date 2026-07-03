package com.adgeistkit.logging

import okhttp3.Headers
import org.json.JSONArray
import org.json.JSONObject

class HttpRequestLog {

    companion object {
        private const val MAX_ENTRIES = 5
        private const val MAX_PAYLOAD_SIZE = 1024 // 1KB
        private const val REDACTED = "[REDACTED]"
        private val REDACTED_HEADERS = setOf(
            "authorization", "cookie", "set-cookie", "x-api-key", "x-user-id", "x-forwarded-for"
        )
        private val REDACTED_PAYLOAD_KEYS = setOf(
            "deviceid", "ip", "userip", "ipaddress", "adid", "idfa", "gaid"
        )
    }

    private val entries = mutableListOf<Map<String, Any?>>()

    @Synchronized
    fun record(
        method: String,
        url: String,
        statusCode: Int? = null,
        latencyMs: Long,
        requestHeaders: Headers? = null,
        responseHeaders: Headers? = null,
        requestPayload: String? = null,
        responsePayload: String? = null,
        errorMessage: String? = null
    ) {
        val entry = mapOf(
            "timestamp" to System.currentTimeMillis(),
            "method" to method,
            "url" to url,
            "statusCode" to statusCode,
            "latencyMs" to latencyMs,
            "requestHeaders" to sanitizeHeaders(requestHeaders),
            "responseHeaders" to sanitizeHeaders(responseHeaders),
            "requestPayload" to sanitizePayload(requestPayload)?.take(MAX_PAYLOAD_SIZE),
            "responsePayload" to sanitizePayload(responsePayload)?.take(MAX_PAYLOAD_SIZE),
            "requestBodySize" to requestPayload?.length,
            "responseBodySize" to responsePayload?.length,
            "errorMessage" to errorMessage
        )

        if (entries.size >= MAX_ENTRIES) {
            entries.removeAt(0)
        }
        entries.add(entry)
    }

    @Synchronized
    fun snapshot(): List<Map<String, Any?>> = entries.toList()

    @Synchronized
    fun clear() = entries.clear()

    private fun sanitizeHeaders(headers: Headers?): Map<String, String>? {
        if (headers == null) return null
        return headers.names().associateWith { name ->
            if (REDACTED_HEADERS.contains(name.lowercase())) REDACTED
            else headers[name] ?: ""
        }
    }

    private fun sanitizePayload(payload: String?): String? {
        if (payload == null) return null
        return try {
            when {
                payload.trimStart().startsWith("{") -> redactJson(JSONObject(payload)).toString()
                payload.trimStart().startsWith("[") -> redactJson(JSONArray(payload)).toString()
                else -> payload
            }
        } catch (e: Exception) {
            payload
        }
    }

    private fun redactJson(value: Any?): Any? {
        return when (value) {
            is JSONObject -> {
                val redacted = JSONObject()
                value.keys().forEach { key ->
                    redacted.put(
                        key,
                        if (REDACTED_PAYLOAD_KEYS.contains(key.lowercase())) REDACTED
                        else redactJson(value.get(key))
                    )
                }
                redacted
            }
            is JSONArray -> {
                val redacted = JSONArray()
                for (i in 0 until value.length()) {
                    redacted.put(redactJson(value.get(i)))
                }
                redacted
            }
            else -> value
        }
    }
}
