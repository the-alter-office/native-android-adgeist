package com.adgeistkit.logging

data class SdkEvent(
    val timestamp: Long,
    val type: String? = null,
    val severity: String? = null,
    val errorCode: String? = null,
    val errorCategory: String? = null,
    val exception: Map<String, Any?>? = null,
    val context: Map<String, Any?>,
    val detectionMethod: String? = null,
)
