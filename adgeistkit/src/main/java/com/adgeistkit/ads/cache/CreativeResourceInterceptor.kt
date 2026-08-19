package com.adgeistkit.ads.cache

import android.content.Context
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

internal object CreativeResourceInterceptor {

    private const val TAG = "CreativeMediaCache"

    private const val SERVE_WAIT_MS = 15_000L

    private const val RANGE_HEADER = "range"

    internal sealed interface Range {
        object Absent : Range

        object Unsatisfiable : Range

        data class Slice(val start: Long, val end: Long) : Range
    }

    fun intercept(context: Context, request: WebResourceRequest): WebResourceResponse? {
        if (!"GET".equals(request.method, ignoreCase = true)) return null

        val url = request.url?.toString() ?: return null
        // Only media from an ad response is ours to serve
        if (!CreativeMediaCache.isRegistered(url)) return null

        val file = CreativeMediaCache.ensureCached(context, url, SERVE_WAIT_MS)
        if (file == null) {
            Log.i(TAG, "MISS not cached in time, WebView will fetch it: $url")
            return null
        }

        return try {
            val rangeHeader = rangeHeaderOf(request)
            Log.i(TAG, "HIT serving from disk (${file.length()} bytes, range=$rangeHeader): $url")
            respond(file, url, rangeHeader)
        } catch (e: Exception) {
            Log.w(TAG, "Could not serve $url from cache, falling back to network", e)
            null
        }
    }

    private fun respond(file: File, url: String, rangeHeader: String?): WebResourceResponse {
        val mimeType = CreativeMediaCache.mimeTypeOf(file, url)
        val fileLength = file.length()

        return when (val range = parseRange(rangeHeader, fileLength)) {
            is Range.Absent -> WebResourceResponse(
                mimeType,
                null,
                200,
                "OK",
                mapOf(
                    // Advertised even on a full response, so the player knows it may seek
                    "Accept-Ranges" to "bytes",
                    "Content-Length" to fileLength.toString(),
                ),
                FileInputStream(file)
            )

            is Range.Unsatisfiable -> WebResourceResponse(
                mimeType,
                null,
                416,
                "Requested Range Not Satisfiable",
                mapOf(
                    "Accept-Ranges" to "bytes",
                    "Content-Range" to "bytes */$fileLength",
                ),
                ByteArrayInputStream(ByteArray(0))
            )

            is Range.Slice -> {
                val sliceLength = range.end - range.start + 1
                // One fresh stream per request: the WebView may have several ranges of
                // the same file open at once, and it closes each stream itself
                val stream = FileInputStream(file)
                stream.channel.position(range.start)

                WebResourceResponse(
                    mimeType,
                    null,
                    206,
                    "Partial Content",
                    mapOf(
                        "Accept-Ranges" to "bytes",
                        // The total must be the real file size, not the slice size
                        "Content-Range" to "bytes ${range.start}-${range.end}/$fileLength",
                        "Content-Length" to sliceLength.toString(),
                    ),
                    BoundedInputStream(stream, sliceLength)
                )
            }
        }
    }

    private fun rangeHeaderOf(request: WebResourceRequest): String? =
        request.requestHeaders
            ?.entries
            ?.firstOrNull { it.key.equals(RANGE_HEADER, ignoreCase = true) }
            ?.value

    /**
     * Handles the three forms a player sends: `bytes=start-end`, `bytes=start-`
     * (to the end of the file), and `bytes=-suffixLength` (the last N bytes).
     * Malformed headers are ignored rather than rejected, per HTTP.
     */
    internal fun parseRange(header: String?, fileLength: Long): Range {
        if (header == null || fileLength <= 0L) return Range.Absent

        val spec = header.trim()
        if (!spec.startsWith("bytes=", ignoreCase = true)) return Range.Absent

        // Multi-range requests are legal but never sent for media; honour the first only
        val firstSpec = spec.substring("bytes=".length).substringBefore(',').trim()
        val separator = firstSpec.indexOf('-')
        if (separator < 0) return Range.Absent

        val rawStart = firstSpec.substring(0, separator).trim()
        val rawEnd = firstSpec.substring(separator + 1).trim()

        val start: Long
        val end: Long
        if (rawStart.isEmpty()) {
            val suffixLength = rawEnd.toLongOrNull() ?: return Range.Absent
            if (suffixLength <= 0L) return Range.Unsatisfiable
            start = maxOf(0L, fileLength - suffixLength)
            end = fileLength - 1
        } else {
            start = rawStart.toLongOrNull() ?: return Range.Absent
            if (start < 0L) return Range.Absent
            if (start >= fileLength) return Range.Unsatisfiable
            // An open-ended or overlong request is clamped to the last byte
            end = rawEnd.toLongOrNull()?.coerceAtMost(fileLength - 1) ?: (fileLength - 1)
        }

        if (end < start) return Range.Unsatisfiable
        return Range.Slice(start, end)
    }

    private class BoundedInputStream(
        private val delegate: InputStream,
        private var remaining: Long,
    ) : InputStream() {

        override fun read(): Int {
            if (remaining <= 0L) return -1
            val value = delegate.read()
            if (value != -1) remaining--
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining <= 0L) return -1
            val toRead = minOf(length.toLong(), remaining).toInt()
            val read = delegate.read(buffer, offset, toRead)
            if (read > 0) remaining -= read
            return read
        }

        override fun available(): Int = minOf(delegate.available().toLong(), remaining).toInt()

        override fun close() = delegate.close()
    }
}
