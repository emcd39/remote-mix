package io.github.rhythmcache.adb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.BufferedSource
import okio.buffer
import okio.sink
import okio.source
import java.net.InetSocketAddress
import java.net.Socket

class TcpPacketTransport private constructor(
    private val socket: Socket,
    private val source: BufferedSource,
    private val sink: BufferedSink,
) : PacketTransport {
    companion object {
        suspend fun connect(
            host: String,
            port: Int = 5555,
            connectTimeoutMs: Int = 10_000,
        ): TcpPacketTransport =
            withContext(Dispatchers.IO) {
                val socket = Socket()
                try {
                    socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                    socket.tcpNoDelay = true
                    TcpPacketTransport(socket, socket.source().buffer(), socket.sink().buffer())
                } catch (e: Exception) {
                    runCatching { socket.close() }
                    throw e
                }
            }
    }

    private val writeLock = Any()

    override fun send(pkt: AdbPacket) {
        synchronized(writeLock) {
            pkt.writeTo(sink)
        }
    }

    override fun recv(): AdbPacket = AdbPacket.readFrom(source)

    override fun close() {
        runCatching { socket.close() }
    }
}
