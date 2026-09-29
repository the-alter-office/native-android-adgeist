package com.adgeistkit.benchmark

import android.util.Log
import com.adgeistkit.constants.General
import com.adgeistkit.constants.Logs
import com.adgeistkit.utilities.logD
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Protocol
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

internal class NetworkTimingListener : EventListener() {

    companion object {
        private const val TAG = "NetworkMetrics Benchmark"

        val FACTORY = object : Factory {
            override fun create(call: Call): EventListener = NetworkTimingListener()
        }
    }

    private var dnsStart = 0L
    private var connectStart = 0L
    private var tlsStart = 0L

    private fun msSince(startNanos: Long): Long = (System.nanoTime() - startNanos) / General.Timing.NANOS_PER_MILLI

    override fun dnsStart(call: Call, domainName: String) {
        dnsStart = System.nanoTime()
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        logD(TAG) { Logs.Debug.dnsResolved(domainName, msSince(dnsStart)) }
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        connectStart = System.nanoTime()
    }

    override fun secureConnectStart(call: Call) {
        tlsStart = System.nanoTime()
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        logD(TAG) { Logs.Debug.tlsHandshake(msSince(tlsStart), handshake?.tlsVersion) }
    }

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?
    ) {
        val totalMs = msSince(connectStart)
        val tcpMs = if (tlsStart != 0L) (tlsStart - connectStart) / General.Timing.NANOS_PER_MILLI else totalMs
        logD(TAG) {
            Logs.Debug.tcpConnected(inetSocketAddress.address?.hostAddress, tcpMs, totalMs, protocol)
        }
    }

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException
    ) {
        Log.w(TAG, Logs.Warning.connectFailed(msSince(connectStart), ioe.message))
    }

    override fun connectionAcquired(call: Call, connection: Connection) {
        if (connectStart == 0L) {
            logD(TAG) { Logs.Debug.CONNECTION_REUSED }
        }
    }
}
