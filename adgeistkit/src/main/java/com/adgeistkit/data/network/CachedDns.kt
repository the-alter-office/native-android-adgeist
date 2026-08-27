package com.adgeistkit.data.network

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

internal class CachedDns(private val delegate: Dns = Dns.SYSTEM) : Dns {

    private val cache = ConcurrentHashMap<String, List<InetAddress>>()

    override fun lookup(hostname: String): List<InetAddress> {
        cache[hostname]?.let { return it }

        return try {
            delegate.lookup(hostname).also { cache[hostname] = it }
        } catch (e: UnknownHostException) {
            cache.remove(hostname)
            throw e
        }
    }

    fun invalidate(hostname: String) {
        cache.remove(hostname)
    }

    fun invalidateAll() {
        cache.clear()
    }
}
