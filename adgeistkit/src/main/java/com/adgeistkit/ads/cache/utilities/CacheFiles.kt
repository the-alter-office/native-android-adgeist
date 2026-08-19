package com.adgeistkit.ads.cache.utilities

import android.content.Context
import android.util.Log
import java.io.File
import java.security.MessageDigest

internal object CacheFiles {

    private const val TAG = "CacheFiles"

    private const val DIR_NAME = "adgeist_creatives"

    fun fileFor(context: Context, url: String): File? {
        val dir = cacheDir(context) ?: return null

        return File(dir, keyFor(url))
    }

    fun cacheDir(context: Context): File? = try {
        File(context.cacheDir, DIR_NAME)
            .apply { if (!exists()) mkdirs() }
            .takeIf { it.isDirectory }
    } catch (e: Exception) {
        Log.w(TAG, "Creative cache directory unavailable", e)
        null
    }

    private fun keyFor(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        val hex = digest.joinToString("") { "%02x".format(it) }
        val extension = MediaType.extensionOf(url)

        return if (extension.isEmpty()) hex else "$hex.$extension"
    }
}
