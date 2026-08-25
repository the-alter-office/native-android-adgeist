package com.adgeistkit.ads.cache

import android.content.Context
import android.util.Log
import com.adgeistkit.data.network.NetworkModule
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

internal object CreativeMediaCache {

    private const val TAG = "CreativeMediaCache"

    private const val REGISTRY_LIMIT = 256
    private const val COPY_BUFFER_BYTES = 32 * 1024

    private val downloadExecutor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "adgeist-creative-cache").apply { isDaemon = true }
    }

    @Volatile
    private var clientRef: OkHttpClient? = null

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

    fun cachedResponse(context: Context, url: String, waitMs: Long): Response? {
        val appContext = context.applicationContext

        readFromCache(appContext, url)?.let { return it }

        val latch = startDownload(appContext, url) ?: return null

        if (waitMs <= 0L) return null

        return try {
            val finished = latch.await(waitMs, TimeUnit.MILLISECONDS)

            if (finished) readFromCache(appContext, url) else null
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        }
    }

    fun clear(context: Context) {
        try {
            mediaClient(context.applicationContext).cache?.evictAll()
        } catch (_: Exception) {
            //
        }

        registry.clear()
    }

    private fun isCacheable(url: String?): Boolean =
        url != null && (url.startsWith("https://") || url.startsWith("http://"))

    private fun mediaClient(appContext: Context): OkHttpClient =
        clientRef ?: synchronized(this) {
            clientRef ?: buildClient(appContext).also { clientRef = it }
        }

    private fun buildClient(appContext: Context): OkHttpClient {
        val dir = File(appContext.cacheDir, CreativeCacheConfig.CACHE_DIR)

        return NetworkModule.httpClient.newBuilder()
            .cache(Cache(dir, CreativeCacheConfig.MAX_CACHE_BYTES))
            .build()
    }

    private fun readFromCache(appContext: Context, url: String): Response? {
        val request = Request.Builder()
            .url(url)
            .cacheControl(CacheControl.FORCE_CACHE)
            .build()

        return try {
            val response = mediaClient(appContext).newCall(request).execute()

            if (response.isSuccessful && response.body != null) {
                response
            } else {
                response.close()
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun startDownload(appContext: Context, url: String): CountDownLatch? {
        val latch = CountDownLatch(1)
        inFlight.putIfAbsent(url, latch)?.let { return it }

        return try {
            downloadExecutor.execute {
                try {
                    download(appContext, url)
                } finally {
                    inFlight.remove(url)
                    latch.countDown()
                }
            }
            latch
        } catch (_: RejectedExecutionException) {
            inFlight.remove(url)
            latch.countDown()
            null
        }
    }

    private fun download(appContext: Context, url: String) {
        readFromCache(appContext, url)?.let { cached ->
            cached.close()
            return
        }

        val request = Request.Builder().url(url).get().build()

        try {
            mediaClient(appContext).newCall(request).execute().use { response ->
                val body = response.body

                if (!response.isSuccessful || body == null) {
                    Log.w(TAG, "Creative download failed (${response.code}) for $url")
                    return
                }

                val length = response.header("Content-Length")?.toLongOrNull() ?: -1L

                if (length < 0L || length > CreativeCacheConfig.MAX_ENTRY_BYTES) {
                    Log.i(TAG, "Creative not cacheable (length=$length): $url")
                    return
                }

                var written = 0L
                val buffer = ByteArray(COPY_BUFFER_BYTES)

                body.byteStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        written += read
                    }
                }
            }
        } catch (_: Exception) {
        }
    }
}
