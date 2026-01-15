package com.adgeistkit.core.device

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.opengl.GLES20
import android.annotation.SuppressLint
import android.opengl.EGLContext

class DeviceMeta(private val context: Context) {
    companion object {
        private const val REQUEST_PHONE_STATE_PERMISSION = 1001

        fun hasPhoneStatePermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
        }

        fun requestPhoneStatePermission(activity: Activity) {
            if (!hasPhoneStatePermission(activity)) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.READ_PHONE_STATE),
                    REQUEST_PHONE_STATE_PERMISSION
                )
            }
        }
    }

    fun getDeviceType(): String {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
        return when {
            uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION -> "TV"
            Resources.getSystem().configuration.smallestScreenWidthDp >= 600 -> "Tablet"
            else -> "Mobile"
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
        return "Android"
    }

    fun getOSVersion(): String {
        return Build.VERSION.RELEASE
    }

    fun getScreenDimensions(): Pair<Int, Int> {
        val displayMetrics: DisplayMetrics = Resources.getSystem().displayMetrics
        return Pair(displayMetrics.widthPixels, displayMetrics.heightPixels)
    }

    fun getNetworkType(): String? {
        return try {
            val permission = context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
            if (permission == PackageManager.PERMISSION_GRANTED) {
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
            if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED
            ) {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                telephonyManager.networkOperatorName.takeIf { it.isNotEmpty() }
            } else {
                null
            }
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


    fun getAllDeviceInfo(): Map<String, Any?> {
        val (width, height) = getScreenDimensions()
        return mapOf(
            "deviceType" to getDeviceType(),
            "deviceBrand" to getDeviceBrand(),

            "screenWidth" to width,
            "screenHeight" to height,

            "osName" to getOperatingSystem(),
            "osVersion" to getOSVersion(),

            "supportedArchitectures" to getCpuType(),
            "noOfProcessors" to getAvailableProcessors(),

            "networkType" to getNetworkType(),
            "networkProvider" to getNetworkProvider(),

            "isScreenReaderPresent" to isScreenReaderPresent(),
            "isNfcCapable" to isNfcCapable(),
            "isVrCapable" to isVrCapable(),

            "isGpuCapable" to isGpuCapable(),
        )
    }
}
