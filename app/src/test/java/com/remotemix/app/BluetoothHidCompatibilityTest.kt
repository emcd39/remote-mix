package com.remotemix.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothHidCompatibilityTest {
    @Test fun televisionCompatibleKeyboardAndConsumerReports() {
        // Bluetooth Remote 8.8 registers an uncategorized HID device with two-byte
        // keyboard and consumer reports. The paired TV accepts that layout.
        assertEquals(0, BluetoothRemote.SUBCLASS)
        assertEquals(mapOf(1 to 2, 2 to 2, 3 to 4), inputReportBytes(BluetoothRemote.DESCRIPTOR))

        val (upId, up) = BluetoothRemote.pressed(RemoteAction.UP)
        assertEquals(1, upId)
        assertArrayEquals(byteArrayOf(0, 0x52), up)

        val (volumeId, volume) = BluetoothRemote.pressed(RemoteAction.VOLUME_UP)
        assertEquals(2, volumeId)
        assertArrayEquals(byteArrayOf(0xE9.toByte(), 0), volume)
    }

    private fun inputReportBytes(descriptor: ByteArray): Map<Int, Int> {
        val bits = mutableMapOf<Int, Int>()
        var reportId = 0
        var reportSize = 0
        var reportCount = 0
        var index = 0
        while (index < descriptor.size) {
            val prefix = descriptor[index++].toInt() and 0xff
            val length = when (prefix and 3) { 3 -> 4; else -> prefix and 3 }
            var value = 0
            repeat(length) { offset -> value = value or ((descriptor[index + offset].toInt() and 0xff) shl (8 * offset)) }
            index += length
            when (prefix) {
                0x85 -> reportId = value
                0x75 -> reportSize = value
                0x95 -> reportCount = value
                0x81 -> bits[reportId] = (bits[reportId] ?: 0) + reportSize * reportCount
            }
        }
        return bits.mapValues { (_, count) -> count / 8 }
    }
}
