package com.adgeistkit.logging

data class SdkEvent(
    val event: String,
    val timestamp: Long,
    val params: Map<String, Any>,
    val context: Map<String, Any?>
)
