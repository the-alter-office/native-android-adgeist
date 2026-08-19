package com.adgeistkit.ads.cache

import android.content.Context
import android.util.Log
import android.webkit.MimeTypeMap
import com.adgeistkit.data.network.NetworkModule
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

internal object CreativeMediaCache {

    private const val TAG = "CreativeMediaCache"

    private const val DIR_NAME = "adgeist_creatives"

    private const val MAX_CACHE_BYTES = 128L * 1024 * 1024
    private const val TRIM_TARGET_BYTES = 96L * 1024 * 1024

    private const val MAX_ENTRY_BYTES = 32L * 1024 * 1024

    private const val CALL_TIMEOUT_SECONDS = 30L

    private const val REGISTRY_LIMIT = 256

    private const val STALE_PART_MS = 60L * 60 * 1000

    private const val COPY_BUFFER_BYTES = 32 * 1024

    private const val PART_SUFFIX = ".part"

    private val downloadExecutor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "adgeist-creative-cache").apply { isDaemon = true }
    }

    private val downloadClient by lazy {
        NetworkModule.httpClient.newBuilder()
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private val inFlight = ConcurrentHashMap<String, CountDownLatch>()


    private val registry: MutableMap<String, Boolean> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Boolean>(REGISTRY_LIMIT, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) =
                size > REGISTRY_LIMIT
        }
    )


    fun register(urls: Collection<String?>) {
        urls.filterNotNull().forEach { url -> if (isCacheable(url)) registry[url] = true }
    }

    fun prefetch(context: Context, urls: Collection<String?>) {
        val appContext = context.applicationContext
        register(urls)

        urls.filterNotNull().filter { isCacheable(it) }.distinct().forEach { url ->
            startDownload(appContext, url)
        }
    }

    fun isRegistered(url: String): Boolean = registry[url] != null


    fun ensureCached(context: Context, url: String, waitMs: Long): File? {
        val appContext = context.applicationContext
        val target = fileFor(appContext, url) ?: return null

        if (target.length() > 0L) {
            // Keeps the entry fresh for the LRU trim
            target.setLastModified(System.currentTimeMillis())
            return target
        }

        val latch = startDownload(appContext, url) ?: return null
        if (waitMs <= 0L) return null

        return try {
            val finished = latch.await(waitMs, TimeUnit.MILLISECONDS)
            if (finished && target.length() > 0L) target else null
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        }
    }

    fun clear(context: Context) {
        val dir = cacheDir(context) ?: return
        dir.listFiles()?.forEach { file ->
            if (!file.delete()) Log.w(TAG, "Could not delete cached creative ${file.name}")
        }
    }

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

    private fun isCacheable(url: String?): Boolean =
        url != null && (url.startsWith("https://") || url.startsWith("http://"))

    private fun startDownload(appContext: Context, url: String): CountDownLatch? {
        val latch = CountDownLatch(1)
        inFlight.putIfAbsent(url, latch)?.let { return it }

        return try {
            downloadExecutor.execute {
                try {
                    val target = fileFor(appContext, url)
                    if (target != null && target.length() == 0L) download(url, target)
                } finally {
                    inFlight.remove(url)
                    latch.countDown()
                }
            }
            latch
        } catch (e: RejectedExecutionException) {
            Log.w(TAG, "Creative download rejected for $url", e)
            inFlight.remove(url)
            latch.countDown()
            null
        }
    }

    private fun download(url: String, target: File): Boolean {
        val temp = File(target.parentFile, target.name + PART_SUFFIX)
        try {
            val request = Request.Builder().url(url).get().build()
            downloadClient.newCall(request).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) {
                    Log.w(TAG, "Creative download failed (${response.code}) for $url")
                    return false
                }
                if (body.contentLength() > MAX_ENTRY_BYTES) {
                    Log.i(TAG, "Creative too large to cache (${body.contentLength()} bytes): $url")
                    return false
                }

                var written = 0L
                body.byteStream().use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(COPY_BUFFER_BYTES)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            written += read
                            if (written > MAX_ENTRY_BYTES) {
                                Log.i(TAG, "Creative outgrew the cache entry limit: $url")
                                return false
                            }
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                    }
                }

                if (written == 0L) {
                    Log.w(TAG, "Creative download was empty: $url")
                    return false
                }

                // Published under its final name only once complete, so a torn
                // download is never served
                if (!temp.renameTo(target)) {
                    Log.w(TAG, "Could not publish cached creative for $url")
                    return false
                }

                Log.d(TAG, "Cached creative ($written bytes): $url")
                trim(target.parentFile)
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Creative download error for $url: ${e.message}")
            return false
        } finally {
            if (temp.exists() && !temp.delete()) {
                Log.w(TAG, "Could not delete partial download ${temp.name}")
            }
        }
    }

    /** Sweeps abandoned partial downloads, then drops LRU entries over budget. */
    private fun trim(dir: File?) {
        val entries = dir?.listFiles() ?: return
        val now = System.currentTimeMillis()

        val complete = entries.filter { file ->
            if (file.name.endsWith(PART_SUFFIX)) {
                // Only a killed process leaves these behind; a live download owns its own
                if (now - file.lastModified() > STALE_PART_MS) file.delete()
                false
            } else {
                true
            }
        }

        var total = complete.sumOf { it.length() }
        if (total <= MAX_CACHE_BYTES) return

        complete.sortedBy { it.lastModified() }.forEach { file ->
            if (total <= TRIM_TARGET_BYTES) return
            val size = file.length()
            if (file.delete()) {
                total -= size
                Log.d(TAG, "Trimmed cached creative ${file.name}")
            }
        }
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
    } catch (e: Exception) {
        null
    }

    private fun ByteArray.matches(offset: Int, ascii: String): Boolean =
        ascii.indices.all { index -> this[offset + index] == ascii[index].code.toByte() }

    private fun fileFor(context: Context, url: String): File? {
        val dir = cacheDir(context) ?: return null
        return File(dir, keyFor(url))
    }

    private fun cacheDir(context: Context): File? = try {
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
        val extension = extensionOf(url)
        return if (extension.isEmpty()) hex else "$hex.$extension"
    }
}
