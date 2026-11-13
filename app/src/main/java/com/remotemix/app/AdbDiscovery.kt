package com.remotemix.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal data class AdbEndpoint(val host: String, val port: Int, val tls: Boolean, val name: String) {
    val key get() = "$host:$port"
}

/** Short-lived mDNS discovery plus bounded checks of common TCP ADB ports on the current LAN. */
internal class AdbDiscovery(
    context: Context,
    private val knownHosts: List<String>,
    private val onFound: (AdbEndpoint) -> Unit,
    private val onComplete: () -> Unit
) {
    private val appContext = context.applicationContext
    private val nsd = appContext.getSystemService(NsdManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newFixedThreadPool(24)
    private val stopped = AtomicBoolean(false)
    private val listeners = mutableListOf<NsdManager.DiscoveryListener>()
    private val finish = Runnable {
        if (!stopped.get()) {
            stop()
            onComplete()
        }
    }

    fun start() {
        discover("_adb._tcp.", tls = false)
        discover("_adb-tls-connect._tcp.", tls = true)
        val hosts = (knownHosts + runCatching { localSubnetHosts() }.getOrDefault(emptyList()))
            .distinct().filter { it.isNotBlank() }
        hosts.forEach { host ->
            worker.execute {
                for (port in listOf(5555, 5556)) {
                    if (stopped.get()) return@execute
                    val available = runCatching {
                        Socket().use { it.connect(InetSocketAddress(host, port), 250) }
                    }.isSuccess
                    if (available) report(AdbEndpoint(host, port, false, "ADB TCP 端口"))
                }
            }
        }
        main.postDelayed(finish, 9_000)
    }

    private fun discover(type: String, tls: Boolean) {
        val manager = nsd ?: return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (stopped.get()) return
                runCatching { manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val host = resolved.host?.hostAddress ?: return
                        if (resolved.port in 1..65535) report(AdbEndpoint(host, resolved.port, tls, resolved.serviceName))
                    }
                }) }
            }
        }
        listeners += listener
        runCatching { manager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    private fun report(endpoint: AdbEndpoint) {
        main.post { if (!stopped.get()) onFound(endpoint) }
    }

    private fun localSubnetHosts(): List<String> {
        val connectivity = appContext.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        return connectivity.allNetworks.flatMap { network ->
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return@flatMap emptyList()
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return@flatMap emptyList()
            connectivity.getLinkProperties(network)?.linkAddresses.orEmpty().flatMap { link ->
                val address = link.address as? Inet4Address ?: return@flatMap emptyList()
                if (!address.isSiteLocalAddress) return@flatMap emptyList()
                subnetHosts(address, link.prefixLength)
            }
        }
    }

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        main.removeCallbacks(finish)
        listeners.forEach { listener -> runCatching { nsd?.stopServiceDiscovery(listener) } }
        listeners.clear()
        worker.shutdownNow()
    }

    companion object {
        internal fun subnetHosts(address: Inet4Address, prefixLength: Int): List<String> {
            if (prefixLength >= 31) return emptyList()
            val prefix = prefixLength.coerceIn(24, 30)
            val bytes = address.address.map { it.toInt() and 0xff }
            val addressInt = (bytes[0] shl 24) or (bytes[1] shl 16) or (bytes[2] shl 8) or bytes[3]
            val network = addressInt and (-1 shl (32 - prefix))
            val count = 1 shl (32 - prefix)
            return (1 until count - 1).mapNotNull { offset ->
                val value = network + offset
                if (value == addressInt) null else listOf(24, 16, 8, 0).joinToString(".") { shift ->
                    ((value ushr shift) and 0xff).toString()
                }
            }
        }
    }
}
