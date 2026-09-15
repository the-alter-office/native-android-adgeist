package com.adgeistkit.data.network

import com.adgeistkit.BuildConfig
import com.adgeistkit.benchmark.NetworkTimingListener
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Process-wide OkHttpClient shared by all SDK network calls, so every ad
 * fetch/analytics post reuses one dispatcher, thread pool, and connection pool
 * instead of spinning up a fresh client per request.
 */
internal object NetworkModule {

    val dns = CachedDns()

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(10, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .dns(dns)
            .addInterceptor(DnsInvalidatingInterceptor(dns))
            .apply {
                if (BuildConfig.DEBUG) eventListenerFactory(NetworkTimingListener.FACTORY)
            }
            .build()
    }
}
