package com.adgeistkit.logging

data class SdkEvent(
    val event: String,
    val timestamp: Long,
    val sdkVersion: String,
    val deviceModel: String,
    val osVersion: String,
    val appId: String,
    val packageName: String,
    val params: Map<String, Any>
)
