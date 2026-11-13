package com.remotemix.app

import org.junit.Assert.*
import org.junit.Test

class RemoteAppCatalogTest {
    @Test fun tvLaunchEntryWinsAndDuplicatePackageAppearsOnce() {
        val entries = RemoteAppCatalog.parse(
            "com.netflix.ninja/.MainActivity\ncom.google.android.youtube.tv/com.google.android.apps.youtube.tv.MainActivity",
            "com.netflix.ninja/.PhoneActivity\norg.xbmc.kodi/.Splash"
        )
        assertEquals(3, entries.size)
        assertEquals("com.netflix.ninja/.MainActivity", entries.first { it.packageName == "com.netflix.ninja" }.component)
        assertEquals("Netflix", entries.first { it.packageName == "com.netflix.ninja" }.label)
        assertEquals("org.xbmc.kodi/.Splash", entries.first { it.packageName == "org.xbmc.kodi" }.component)
    }

    @Test fun ignoresHeadersErrorsAndShellMetacharacters() {
        val entries = RemoteAppCatalog.parse(
            "2 activities found:\n Activity #0:\ncom.safe.app/.Main\ncom.bad.app/.Main;reboot",
            "No activities found"
        )
        assertEquals(1, entries.size)
        assertTrue(RemoteAppCatalog.isSafeComponent(entries.single().component))
        assertFalse(RemoteAppCatalog.isSafeComponent("com.bad.app/.Main;reboot"))
    }
}
