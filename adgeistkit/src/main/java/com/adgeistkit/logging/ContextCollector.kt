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

    private var deviceMeta: DeviceMeta? = null
    private var deviceIdentifier: DeviceIdentifier? = null
    private var networkUtils: NetworkUtils? = null
    private var additionalDeviceInfo: AdditionalTemporaryDeviceInfo? = null
    private var appContext: Context? = null

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
        this.additionalDeviceInfo = AdditionalTemporaryDeviceInfo(context.applicationContext)
        this.isInitialized = true

        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to add lifecycle observer")
        }

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
        val context = appContext ?: return emptyMap()

        val packageName = context.packageName
        var appVersion = ""
        var appVersionCode = ""

        try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
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
        val meta = deviceMeta ?: return emptyMap()
        val additional = additionalDeviceInfo

        val (screenWidth, screenHeight) = meta.getScreenDimensions()

        val deviceContext = mutableMapOf<String, Any?>(
            "osName" to meta.getOperatingSystem(),
            "osVersion" to meta.getOSVersion(),
            "deviceModel" to Build.MODEL,
            "deviceBrand" to meta.getDeviceBrand(),
            "deviceType" to meta.getDeviceType(),
            "screenWidth" to screenWidth,
            "screenHeight" to screenHeight,
            "networkType" to meta.getNetworkType(),
            "networkProvider" to meta.getNetworkProvider(),
            "supportedArchitectures" to meta.getCpuType(),
        )

        // Merge additional temporary device info (memory, density, locale, timezone)
        additional?.let { deviceContext.putAll(it.getAll()) }

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
        val utils = networkUtils ?: return emptyMap()
        val identifier = deviceIdentifier

        val userIP = utils.getLocalIpAddress()
            ?: utils.getWifiIpAddress()

        return mapOf(
            "userIP" to (userIP ?: "unknown"),
            "deviceId" to (identifier?.getDeviceIdentifier() ?: "pending")
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
