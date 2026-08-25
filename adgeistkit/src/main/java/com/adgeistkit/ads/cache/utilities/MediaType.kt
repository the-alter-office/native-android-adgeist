package com.adgeistkit.ads.cache.utilities

import android.webkit.MimeTypeMap

internal object MediaType {

    private const val FALLBACK_TYPE = "application/octet-stream"

    private val GENERIC_TYPES = setOf(
        FALLBACK_TYPE,
        "binary/octet-stream",
        "application/binary",
    )

    fun extensionOf(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val extension = path.substringAfterLast('/').substringAfterLast('.', "")
        return if (extension.length in 1..5 && extension.all { it.isLetterOrDigit() }) {
            extension.lowercase()
        } else {
            ""
        }
    }

    /**
     * The extension wins over [contentType] on purpose: S3 often serves creatives as
     * octet-stream, and Chromium will not play a <video> source with a generic type.
     */
    fun mimeTypeOf(url: String, contentType: String? = null): String {
        val extension = extensionOf(url)

        if (extension.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)?.let { return it }
        }

        val header = contentType?.substringBefore(';')?.trim()?.lowercase()

        if (!header.isNullOrEmpty() && header !in GENERIC_TYPES) return header

        return FALLBACK_TYPE
    }
}
