package com.adgeistkit.ads.cache

import android.content.Context
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.adgeistkit.ads.cache.utilities.MediaType
import java.io.FileInputStream

internal object CreativeResourceInterceptor {

    private const val TAG = "CreativeMediaCache"

    private const val SERVE_WAIT_MS = 15_000L

    fun intercept(context: Context, request: WebResourceRequest): WebResourceResponse? {
        if (!"GET".equals(request.method, ignoreCase = true)) return null

        val url = request.url?.toString() ?: return null
        if (!CreativeMediaCache.isRegistered(url)) return null

        val file = CreativeMediaCache.ensureCached(context, url, SERVE_WAIT_MS)
        if (file == null) {
            Log.i(TAG, "MISS not cached in time, WebView will fetch it: $url")
            return null
        }

        return try {
            val mimeType = MediaType.mimeTypeOf(file, url)
            Log.i(TAG, "HIT serving from disk (${file.length()} bytes, $mimeType): $url")
            WebResourceResponse(mimeType, null, FileInputStream(file))
        } catch (e: Exception) {
            Log.w(TAG, "Could not serve $url from cache, falling back to network", e)
            null
        }
    }
}
