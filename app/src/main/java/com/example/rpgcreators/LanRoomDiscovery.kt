package com.example.rpgcreators

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicBoolean

/** Discovery is optional: if the access point blocks discovery, direct-IP joining still works. */
internal data class DiscoveredLanRoom(val address: String, val name: String, val via: String)

internal class LanRoomDiscovery(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val nsd = app.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val running = AtomicBoolean(false)
    private var socket: DatagramSocket? = null
    private var worker: Thread? = null
    private var nsdRegistration: NsdManager.RegistrationListener? = null
    private var nsdDiscovery: NsdManager.DiscoveryListener? = null
    private val nsdResolvers = mutableListOf<NsdManager.ResolveListener>()
    private val found = linkedMapOf<String, Pair<DiscoveredLanRoom, Long>>()
    private var receiver: ((List<DiscoveredLanRoom>) -> Unit)? = null
    private var advertising = false

    private fun emit() {
        main.post {
            val now = System.currentTimeMillis()
            found.entries.removeAll { now - it.value.second > 12000L }
            receiver?.invoke(found.values.map { it.first }.sortedBy { it.name })
        }
    }
    private fun foundRoom(ip: String, name: String, via: String) {
        if (ip.isBlank() || ip == "0.0.0.0") return
        main.post {
            if (!running.get() || advertising) return@post
            found[ip] = DiscoveredLanRoom(ip, name.take(60).ifBlank { "Комната мастера" }, via) to System.currentTimeMillis()
            emit()
        }
    }

    fun advertise(roomName: String = "Комната мастера") {
        stop()
        running.set(true)
        advertising = true
        val registration = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        nsdRegistration = registration
        try {
            val service = NsdServiceInfo().apply {
                serviceName = roomName.take(40)
                serviceType = "_rpgcreators._tcp."
                port = LAN_PORT
            }
            nsd.registerService(service, NsdManager.PROTOCOL_DNS_SD, registration)
        } catch (_: Exception) { nsdRegistration = null }
        worker = Thread {
            try {
                DatagramSocket(DISCOVERY_PORT).use { udp ->
                    udp.soTimeout = 1500
                    socket = udp
                    val data = ByteArray(512)
                    while (running.get()) {
                        val incoming = DatagramPacket(data, data.size)
                        try { udp.receive(incoming) } catch (_: java.net.SocketTimeoutException) { continue }
                        if (!running.get()) break
                        val message = String(incoming.data, incoming.offset, incoming.length, Charsets.UTF_8)
                        if (message == PROBE) {
                            val reply = "${REPLY_PREFIX}${roomName.take(50)}".toByteArray(Charsets.UTF_8)
                            udp.send(DatagramPacket(reply, reply.size, incoming.address, incoming.port))
                        }
                    }
                }
            } catch (_: Exception) { /* NSD or manual IP still works */ }
            finally { socket = null }
        }.apply { name = "RpgLanAdvertiser"; isDaemon = true; start() }
    }

    fun discover(onChange: (List<DiscoveredLanRoom>) -> Unit) {
        stop()
        advertising = false
        receiver = onChange
        running.set(true)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.startsWith("_rpgcreators._tcp")) return
                val resolver = object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        if (resolved.port == LAN_PORT) {
                            val address = resolved.host as? Inet4Address
                            if (address != null) foundRoom(address.hostAddress ?: "", resolved.serviceName, "NSD")
                        }
                    }
                }
                synchronized(nsdResolvers) { nsdResolvers.add(resolver) }
                try { nsd.resolveService(serviceInfo, resolver) } catch (_: Exception) { }
            }
        }
        nsdDiscovery = listener
        try { nsd.discoverServices("_rpgcreators._tcp.", NsdManager.PROTOCOL_DNS_SD, listener) }
        catch (_: Exception) { nsdDiscovery = null }

        worker = Thread {
            try {
                DatagramSocket().use { udp ->
                    socket = udp
                    udp.broadcast = true
                    udp.soTimeout = 1000
                    val buffer = ByteArray(512)
                    var lastProbe = 0L
                    while (running.get()) {
                        val now = System.currentTimeMillis()
                        if (now - lastProbe >= 3000) {
                            lastProbe = now
                            val targets = linkedSetOf("255.255.255.255")
                            try {
                                val interfaces = NetworkInterface.getNetworkInterfaces()
                                while (interfaces != null && interfaces.hasMoreElements()) {
                                    val iface = interfaces.nextElement()
                                    if (!iface.isUp || iface.isLoopback) continue
                                    for (a in iface.interfaceAddresses) {
                                        if (a.address is Inet4Address && a.address.isSiteLocalAddress) {
                                            (a.broadcast as? Inet4Address)?.hostAddress?.let { targets.add(it) }
                                        }
                                    }
                                }
                            } catch (_: Exception) { }
                            val bytes = PROBE.toByteArray(Charsets.UTF_8)
                            for (target in targets) try {
                                udp.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(target), DISCOVERY_PORT))
                            } catch (_: Exception) { }
                            emit()
                        }
                        val incoming = DatagramPacket(buffer, buffer.size)
                        try { udp.receive(incoming) } catch (_: java.net.SocketTimeoutException) { continue }
                        val text = String(incoming.data, incoming.offset, incoming.length, Charsets.UTF_8)
                        if (text.startsWith(REPLY_PREFIX)) {
                            foundRoom(incoming.address.hostAddress ?: "", text.removePrefix(REPLY_PREFIX), "LAN")
                        }
                    }
                }
            } catch (_: Exception) { /* NSD and direct IP stay available */ }
            finally { socket = null }
        }.apply { name = "RpgLanDiscovery"; isDaemon = true; start() }
    }

    fun stop() {
        running.set(false)
        socket?.close()
        socket = null
        worker?.interrupt()
        worker = null
        nsdDiscovery?.let { try { nsd.stopServiceDiscovery(it) } catch (_: Exception) { } }
        nsdDiscovery = null
        nsdRegistration?.let { try { nsd.unregisterService(it) } catch (_: Exception) { } }
        nsdRegistration = null
        synchronized(nsdResolvers) { nsdResolvers.clear() }
        receiver = null
        found.clear()
    }

    companion object {
        private const val DISCOVERY_PORT = 8989
        private const val PROBE = "RPG_CREATORS_DISCOVER_V1"
        private const val REPLY_PREFIX = "RPG_CREATORS_ROOM_V1:"
    }
}
