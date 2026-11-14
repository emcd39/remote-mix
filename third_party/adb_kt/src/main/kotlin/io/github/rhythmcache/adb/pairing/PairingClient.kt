package io.github.rhythmcache.adb.pairing

import io.github.rhythmcache.adb.withDeadline
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Host/client-side entry point for the ADB pairing protocol: connects to a
 * device that is advertising a pairing code (QR or 6-digit), and runs the
 * full SPAKE2 + TLS pairing exchange.
 */
object PairingClient {
    /**
     * Pairs with a device at [host]:[port] using [password] (the raw bytes
     * of the pairing code shown on-device). Returns [PairingResult] containing
     * the device's PeerInfo (expected type ADB_DEVICE_GUID) and peer X.509 certificate PEM.
     */
    suspend fun pair(
        host: String,
        port: Int,
        password: ByteArray,
        ourPeerInfo: PeerInfo,
        identity: PairingIdentity,
        connectTimeoutMs: Int = 10_000,
        pairingTimeoutMs: Int = 15_000,
    ): Result<PairingResult> {
        return try {
            Socket().use { socket ->
                socket.withDeadline(pairingTimeoutMs) {
                    socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                    socket.tcpNoDelay = true
                    PairingConnection.run(socket, ConnectionRole.CLIENT, password, ourPeerInfo, identity)
                }
            }
        } catch (e: IOException) {
            Result.failure(e)
        }
    }
}
