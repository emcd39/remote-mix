package io.github.rhythmcache.adb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private val socketTimeouts = ScheduledThreadPoolExecutor(1) { task ->
    Thread(task, "ADB socket deadlines").apply { isDaemon = true }
}.apply { removeOnCancelPolicy = true }

/** Close the socket at the deadline so blocking TLS I/O cannot hold the caller indefinitely. */
internal suspend fun <T> Socket.withDeadline(timeoutMs: Int, block: () -> T): T {
    val finished = AtomicBoolean(false)
    val deadline = socketTimeouts.schedule({
        if (finished.compareAndSet(false, true)) runCatching { close() }
    }, timeoutMs.toLong(), TimeUnit.MILLISECONDS)
    try {
        val result = try {
            withContext(Dispatchers.IO) { block() }
        } catch (e: Exception) {
            if (!finished.compareAndSet(false, true)) {
                throw SocketTimeoutException("ADB socket operation timed out").apply { initCause(e) }
            }
            throw e
        }
        if (!finished.compareAndSet(false, true)) throw SocketTimeoutException("ADB socket operation timed out")
        return result
    } finally {
        deadline.cancel(false)
    }
}
