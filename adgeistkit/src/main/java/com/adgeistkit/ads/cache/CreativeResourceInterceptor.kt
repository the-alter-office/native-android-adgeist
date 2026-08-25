package com.adgeistkit.ads.cache

import android.content.Context
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.adgeistkit.ads.cache.utilities.MediaType

internal object CreativeResourceInterceptor {

    private const val TAG = "CreativeMediaCache"

    private const val SERVE_WAIT_MS = 15_000L

    fun intercept(context: Context, request: WebResourceRequest): WebResourceResponse? {
        if (!"GET".equals(request.method, ignoreCase = true)) return null

        val url = request.url?.toString() ?: return null
        if (!CreativeMediaCache.isRegistered(url)) return null

        if (hasRangeHeader(request)) {
            Log.i(TAG, "RANGE request, letting the WebView fetch it: $url")
            return null
        }

        val response = CreativeMediaCache.cachedResponse(context, url, SERVE_WAIT_MS)

        if (response == null) {
            Log.i(TAG, "MISS not cached in time, WebView will fetch it: $url")
            return null
        }

        return try {
            val body = requireNotNull(response.body) { "cached response had no body" }
            val contentType = response.header("Content-Type")
            val mimeType = MediaType.mimeTypeOf(url, contentType)

            Log.i(
                TAG,
                "HIT serving from cache (${body.contentLength()} bytes, " +
                    "served=$mimeType, origin sent Content-Type=$contentType): $url"
            )

            WebResourceResponse(mimeType, null, body.byteStream())
        } catch (e: Exception) {
            Log.w(TAG, "Could not serve $url from cache, falling back to network", e)
            response.close()
            null
        }
    }

    private fun hasRangeHeader(request: WebResourceRequest): Boolean =
        request.requestHeaders?.keys?.any { it.equals("Range", ignoreCase = true) } == true
}
