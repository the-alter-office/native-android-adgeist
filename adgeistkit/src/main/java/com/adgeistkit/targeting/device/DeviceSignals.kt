package com.adgeistkit.targeting.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import android.annotation.SuppressLint
import android.nfc.NfcAdapter

class DeviceSignals(private val context: Context) {
    companion object {
        // READ_PHONE_STATE is not declared by this SDK; telephony details are
        // collected only when the host app has declared and been granted it.
        fun hasPhoneStatePermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
        }

        // getDataNetworkType accepts READ_BASIC_PHONE_STATE (normal permission,
        // declared by this SDK) on API 33+, or READ_PHONE_STATE from the host app.
        fun canReadNetworkType(context: Context): Boolean {
            if (hasPhoneStatePermission(context)) return true
            return Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(
                        context,
                        "android.permission.READ_BASIC_PHONE_STATE"
                    ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getDeviceType(): String {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
        return when {
            uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION -> "DESKTOP"
            else -> "MOBILE"
        }
    }

    fun getDeviceBrand(): String = Build.MANUFACTURER

    fun getCpuType(): String {
        return try {
            Build.SUPPORTED_ABIS.joinToString(", ")
        } catch (e: Exception) {
            "Unavailable (Android Privacy Restrictions)"
        }
    }

    fun getAvailableProcessors(): Int {
        return try {
            Runtime.getRuntime().availableProcessors()
        } catch (e: Exception) {
            -1
        }
    }

    fun getOperatingSystem(): String {
        return "ANDROID"
    }

    fun getOSVersion(): String {
        return Build.VERSION.RELEASE
    }

    fun getScreenDimensions(): Pair<Int, Int> {
        val displayMetrics: DisplayMetrics = Resources.getSystem().displayMetrics
        return Pair(displayMetrics.widthPixels, displayMetrics.heightPixels)
    }

    @SuppressLint("MissingPermission")
    fun getNetworkType(): String? {
        return try {
            if (canReadNetworkType(context)) {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    when (telephonyManager.dataNetworkType) {
                        TelephonyManager.NETWORK_TYPE_GPRS,
                        TelephonyManager.NETWORK_TYPE_EDGE,
                        TelephonyManager.NETWORK_TYPE_UMTS,
                        TelephonyManager.NETWORK_TYPE_HSDPA,
                        TelephonyManager.NETWORK_TYPE_HSUPA,
                        TelephonyManager.NETWORK_TYPE_HSPA -> "3G"
                        TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                        TelephonyManager.NETWORK_TYPE_NR -> "5G"
                        else -> "Unavailable (Android Privacy Restrictions)"
                    }
                } else {
                    @Suppress("DEPRECATION")
                    when (telephonyManager.networkType) {
                        TelephonyManager.NETWORK_TYPE_GPRS,
                        TelephonyManager.NETWORK_TYPE_EDGE,
                        TelephonyManager.NETWORK_TYPE_UMTS,
                        TelephonyManager.NETWORK_TYPE_HSDPA,
                        TelephonyManager.NETWORK_TYPE_HSUPA,
                        TelephonyManager.NETWORK_TYPE_HSPA -> "3G"
                        TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                        else -> "Unavailable (Android Privacy Restrictions)"
                    }
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getNetworkProvider(): String? {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            telephonyManager.networkOperatorName.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    fun isGpuCapable(): Boolean {
        val pm = context.packageManager
        val hasOpenGLES = pm.hasSystemFeature(PackageManager.FEATURE_OPENGLES_EXTENSION_PACK)
        val hasVulkan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, 1)
        } else {
            false
        }
        return hasOpenGLES || hasVulkan
    }

    fun isNfcCapable(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)
    }

    fun isVrCapable(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE) ||
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE)
    }

    fun isScreenReaderPresent(): Boolean {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return accessibilityManager.isTouchExplorationEnabled
    }

    fun getScreenPixelRatio(): Float {
        return context.resources.displayMetrics.density
    }

    fun getScreenDensity(): Int {
        return context.resources.displayMetrics.densityDpi
    }

    fun isTouchScreenCapable(): Boolean {
        return context.resources.configuration.touchscreen != Configuration.TOUCHSCREEN_NOTOUCH
    }

    fun isNFCEnabled(): Boolean {
        return try {
            val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
            nfcAdapter?.isEnabled == true
        } catch (_: Exception) {
            false
        }
    }

    fun getCoreArchitecture(): String? {

        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull()

        val mapSet = mapOf(
            "armeabi-v7a" to "ARM",
            "arm64-v8a" to "ARM64",
            "x86" to "x86",
            "x86_64" to "x86-64",
        )

        return mapSet[abi] ?: "Unknown"
    }


    fun getAllDeviceInfo(): Map<String, Any?> {
        val (width, height) = getScreenDimensions()
        return mapOf(
            "deviceType" to getDeviceType(),
            "deviceBrand" to getDeviceBrand(),

            "screenWidth" to width,
            "screenHeight" to height,
            "screenPixelRatio" to getScreenPixelRatio(),
            "screenDensity" to getScreenDensity(),

            "osName" to getOperatingSystem(),
            "osVersion" to getOSVersion(),

            "supportedArchitectures" to getCpuType(),
            "architecture" to getCoreArchitecture(),
            "noOfProcessors" to getAvailableProcessors(),

            "networkType" to getNetworkType(),
            "networkConnectionType" to getNetworkProvider(),

            "isScreenReaderEnabled" to isScreenReaderPresent(),
            "isNfcCapable" to isNfcCapable(),
            "isNfcEnabled" to isNFCEnabled(),
            "isVrCapable" to isVrCapable(),

            "isGpuCapable" to isGpuCapable(),
            "isTouchScreenCapable" to isTouchScreenCapable(),
        )
    }
}
