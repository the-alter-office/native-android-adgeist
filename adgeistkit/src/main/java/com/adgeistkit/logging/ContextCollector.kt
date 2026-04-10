package com.adgeistkit.logging

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.adgeistkit.BuildConfig
import com.adgeistkit.core.SdkFramework
import com.adgeistkit.core.device.DeviceIdentifier
import com.adgeistkit.core.device.DeviceMeta
import com.adgeistkit.core.device.NetworkUtils
import java.util.UUID

object ContextCollector {

    private const val TAG = "ContextCollector"

    private lateinit var deviceMeta: DeviceMeta
    private lateinit var deviceIdentifier: DeviceIdentifier
    private lateinit var networkUtils: NetworkUtils
    private lateinit var additionalDeviceInfo: AdditionalTemporaryDeviceInfo
    private lateinit var appContext: Context

    private var publisherId: String = ""
    private var framework: SdkFramework = SdkFramework.KOTLIN
    private var isInitialized = false

    // Session state
    private val sessionId: String = UUID.randomUUID().toString()
    @Volatile private var lastKnownState: String = "INITIALIZING"
    @Volatile private var appState: String = "foreground"

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            appState = "foreground"
        }
        override fun onStop(owner: LifecycleOwner) {
            appState = "background"
        }
    }

    fun initialize(
        context: Context,
        publisherId: String,
        deviceMeta: DeviceMeta,
        deviceIdentifier: DeviceIdentifier,
        networkUtils: NetworkUtils,
        framework: SdkFramework = SdkFramework.KOTLIN
    ) {
        this.appContext = context.applicationContext
        this.publisherId = publisherId
        this.framework = framework
        this.deviceMeta = deviceMeta
        this.deviceIdentifier = deviceIdentifier
        this.networkUtils = networkUtils
        this.additionalDeviceInfo = AdditionalTemporaryDeviceInfo(appContext)
        this.isInitialized = true

        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)

        Log.d(TAG, "ContextCollector initialized")
    }

    fun updateState(state: String) {
        lastKnownState = state
    }

    fun getSdkContext(): Map<String, Any> {
        return mapOf(
            "sdkName" to "AdgeistKit",
            "sdkVersion" to BuildConfig.VERSION_NAME,
            "platform" to "ANDROID",
            "language/framework" to framework.value
        )
    }

    fun getAppContext(): Map<String, Any> {
        if (!isInitialized) return emptyMap()

        val packageName = appContext.packageName
        var appVersion = ""
        var appVersionCode = ""

        try {
            val packageInfo = appContext.packageManager.getPackageInfo(packageName, 0)
            appVersion = packageInfo.versionName ?: ""
            appVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toString()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Failed to get package info: ${e.message}")
        }

        return mapOf(
            "publisherId" to publisherId,
            "packageName" to packageName,
            "appVersion" to appVersion,
            "appVersionCode" to appVersionCode
        )
    }

    fun getDeviceContext(): Map<String, Any?> {
        if (!isInitialized) return emptyMap()

        val (screenWidth, screenHeight) = deviceMeta.getScreenDimensions()

        val deviceContext = mutableMapOf<String, Any?>(
            "osName" to deviceMeta.getOperatingSystem(),
            "osVersion" to deviceMeta.getOSVersion(),
            "deviceModel" to Build.MODEL,
            "deviceBrand" to deviceMeta.getDeviceBrand(),
            "deviceType" to deviceMeta.getDeviceType(),
            "screenWidth" to screenWidth,
            "screenHeight" to screenHeight,
            "networkType" to deviceMeta.getNetworkType(),
            "networkProvider" to deviceMeta.getNetworkProvider(),
            "supportedArchitectures" to deviceMeta.getCpuType(),
        )

        // Merge additional temporary device info (memory, density, locale, timezone)
        deviceContext.putAll(additionalDeviceInfo.getAll())

        return deviceContext
    }

    fun getSessionContext(): Map<String, Any> {
        return mapOf(
            "sessionId" to sessionId,
            "lastKnownState" to lastKnownState,
            "appState" to appState
        )
    }

    fun getUserContext(): Map<String, Any?> {
        if (!isInitialized) return emptyMap()

        val userIP = networkUtils.getLocalIpAddress()
            ?: networkUtils.getWifiIpAddress()

        return mapOf(
            "userIP" to (userIP ?: "unknown"),
            "deviceId" to (deviceIdentifier.getDeviceIdentifier() ?: "pending")
        )
    }

    fun getFullContext(): Map<String, Any?> {
        return mapOf(
            "sdk" to getSdkContext(),
            "app" to getAppContext(),
            "device" to getDeviceContext(),
            "session" to getSessionContext(),
            "user" to getUserContext()
        )
    }
}
