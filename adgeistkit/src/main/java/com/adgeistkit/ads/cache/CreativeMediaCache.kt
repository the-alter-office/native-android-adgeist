package com.adgeistkit.ads.cache

import android.content.Context
import android.util.Log
import com.adgeistkit.ads.cache.utilities.CacheFiles
import com.adgeistkit.data.network.NetworkModule
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

internal object CreativeMediaCache {

    private const val TAG = "CreativeMediaCache"

    private const val MAX_CACHE_BYTES = 64L * 1024 * 1024
    private const val TRIM_TARGET_BYTES = 48L * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 10L * 1024 * 1024
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
        val target = CacheFiles.fileFor(appContext, url) ?: return null

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
        val dir = CacheFiles.cacheDir(context) ?: return

        dir.listFiles()?.forEach { file ->
            if (!file.delete()) Log.w(TAG, "Could not delete cached creative ${file.name}")
        }
    }

    private fun isCacheable(url: String?): Boolean =
        url != null && (url.startsWith("https://") || url.startsWith("http://"))

    private fun startDownload(appContext: Context, url: String): CountDownLatch? {
        val latch = CountDownLatch(1)
        inFlight.putIfAbsent(url, latch)?.let { return it }

        return try {
            downloadExecutor.execute {
                try {
                    val target = CacheFiles.fileFor(appContext, url)
                    
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

}
