package com.adgeistkit.data.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

internal class DnsInvalidatingInterceptor(private val dns: CachedDns) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        return try {
            chain.proceed(chain.request())
        } catch (e: IOException) {
            dns.invalidate(chain.request().url.host)
            throw e
        }
    }
}
