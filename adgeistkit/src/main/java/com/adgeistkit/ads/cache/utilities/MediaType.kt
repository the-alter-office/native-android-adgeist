package com.adgeistkit.ads.cache.utilities

import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileInputStream

internal object MediaType {

    fun extensionOf(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val extension = path.substringAfterLast('/').substringAfterLast('.', "")
        return if (extension.length in 1..5 && extension.all { it.isLetterOrDigit() }) {
            extension.lowercase()
        } else {
            ""
        }
    }

    fun mimeTypeOf(file: File, url: String): String {
        val extension = extensionOf(url)
        if (extension.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)?.let { return it }
        }
        return sniffMimeType(file) ?: "application/octet-stream"
    }

    private fun sniffMimeType(file: File): String? = try {
        val header = ByteArray(12)
        val read = FileInputStream(file).use { it.read(header) }
        when {
            read >= 12 && header.matches(4, "ftyp") -> "video/mp4"
            read >= 8 && header[0] == 0x89.toByte() && header.matches(1, "PNG") -> "image/png"
            read >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() -> "image/jpeg"
            read >= 4 && header.matches(0, "GIF8") -> "image/gif"
            read >= 12 && header.matches(0, "RIFF") && header.matches(8, "WEBP") -> "image/webp"
            else -> null
        }
    } catch (_: Exception) {
        null
    }

    private fun ByteArray.matches(offset: Int, ascii: String): Boolean =
        ascii.indices.all { index -> this[offset + index] == ascii[index].code.toByte() }
}
