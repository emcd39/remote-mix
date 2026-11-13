package com.remotemix.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress

class AdbDiscoveryTest {
    @Test fun scansOnlyLocalSegmentAndSkipsOwnAddress() {
        val address = InetAddress.getByName("192.168.1.95") as Inet4Address
        val hosts = AdbDiscovery.subnetHosts(address, 24)
        assertEquals(253, hosts.size)
        assertTrue("192.168.1.1" in hosts)
        assertTrue("192.168.1.254" in hosts)
        assertFalse("192.168.1.95" in hosts)
        assertFalse("192.168.2.1" in hosts)
    }

    @Test fun largeLanIsBoundedToOneClassCSegment() {
        val address = InetAddress.getByName("172.26.0.4") as Inet4Address
        val hosts = AdbDiscovery.subnetHosts(address, 16)
        assertEquals(253, hosts.size)
        assertTrue("172.26.0.1" in hosts)
        assertFalse("172.26.1.1" in hosts)
    }
}
