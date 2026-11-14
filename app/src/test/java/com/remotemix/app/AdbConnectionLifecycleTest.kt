package com.remotemix.app

import io.github.rhythmcache.adb.AdbClient
import io.github.rhythmcache.adb.AdbCmd
import io.github.rhythmcache.adb.MemoryKeyProvider
import io.github.rhythmcache.adb.TlsPacketTransport
import io.github.rhythmcache.adb.pairing.buildPairingIdentity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.DataInputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

class AdbConnectionLifecycleTest {
    @Test fun tlsPeerThatNeverUpgradesGetsDisconnected() {
        val identity = runBlocking { buildPairingIdentity(MemoryKeyProvider) }
        fakePeer { input, _ ->
            assertEquals(AdbCmd.CNXN, input.readPacket().first)
            assertEquals(-1, input.read())
        }.use { peer ->
            assertThrows(SocketTimeoutException::class.java) {
                runBlocking {
                    TlsPacketTransport.connect(peer.host, peer.port, identity, ByteArray(32), handshakeTimeoutMs = 750)
                }
            }
            peer.verify()
        }
    }

    @Test fun shellDeadlineClosesConnectionEvenIfPeerNeverClosesItsStream() {
        fakePeer { input, output ->
            assertEquals(AdbCmd.CNXN, input.readPacket().first)
            output.packet(AdbCmd.CNXN, 0x01000001, 4096, "device::features=shell_v2\u0000".toByteArray())
            val (command, streamId) = input.readPacket()
            assertEquals(AdbCmd.OPEN, command)
            output.packet(AdbCmd.OKAY, 1, streamId)
            while (input.read() != -1) { /* ADB may send CLSE before the underlying socket closes. */ }
        }.use { peer ->
            runBlocking { AdbClient.connectTcp(peer.host, peer.port, handshakeTimeoutMs = 2_000) }.use { client ->
                val error = assertThrows(IllegalStateException::class.java) {
                    runBlocking { withAdbDeadline(client, 750) { client.shell("true") } }
                }
                assertTrue(error.message.orEmpty().contains("超时"))
                peer.verify()
            }
        }
    }

    @Test fun closingIdleConnectionClosesTheUnderlyingSocket() {
        fakePeer { input, output ->
            assertEquals(AdbCmd.CNXN, input.readPacket().first)
            output.packet(AdbCmd.CNXN, 0x01000001, 4096, "device::\u0000".toByteArray())
            assertEquals(-1, input.read())
        }.use { peer ->
            runBlocking { AdbClient.connectTcp(peer.host, peer.port, handshakeTimeoutMs = 2_000) }.close()
            peer.verify()
        }
    }

    private fun fakePeer(serve: (DataInputStream, OutputStream) -> Unit) = Peer(serve)

    private class Peer(serve: (DataInputStream, OutputStream) -> Unit) : AutoCloseable {
        private val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 5_000 }
        val host = "127.0.0.1"
        val port: Int get() = listener.localPort
        private val done = FutureTask {
            listener.accept().use { socket ->
                socket.soTimeout = 5_000
                serve(DataInputStream(socket.getInputStream()), socket.getOutputStream())
            }
        }.also { task -> Thread(task, "test ADB peer").apply { isDaemon = true; start() } }

        fun verify() { done.get(6, TimeUnit.SECONDS) }
        override fun close() = listener.close()
    }

    private fun DataInputStream.readPacket(): Pair<Int, Int> {
        val header = ByteBuffer.wrap(readNBytes(24)).order(ByteOrder.LITTLE_ENDIAN)
        val command = header.int
        val arg0 = header.int
        header.int // arg1
        val length = header.int
        assertEquals(length, readNBytes(length).size)
        return command to arg0
    }

    private fun OutputStream.packet(command: Int, arg0: Int, arg1: Int, payload: ByteArray = byteArrayOf()) {
        write(ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(command).putInt(arg0).putInt(arg1).putInt(payload.size)
            .putInt(payload.sumOf { it.toInt() and 0xff }).putInt(command.inv()).array())
        write(payload)
        flush()
    }
}
