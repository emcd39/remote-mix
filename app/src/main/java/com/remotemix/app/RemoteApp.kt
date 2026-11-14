package com.remotemix.app

import org.json.JSONObject
import java.util.Base64

data class RemoteApp(
    val packageName: String,
    val component: String,
    val label: String,
    val iconPng: ByteArray? = null
)

object RemoteAppCatalog {
    private val componentLine = Regex("^([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)+)/([A-Za-z_.$][A-Za-z0-9_.$]*)$")
    private val knownNames = mapOf(
        "com.google.android.youtube.tv" to "YouTube",
        "com.google.android.youtube" to "YouTube",
        "com.netflix.ninja" to "Netflix",
        "com.netflix.mediaclient" to "Netflix",
        "com.amazon.amazonvideo.livingroom" to "Prime Video",
        "com.amazon.avod.thirdpartyclient" to "Prime Video",
        "com.disney.disneyplus" to "Disney+",
        "org.xbmc.kodi" to "Kodi",
        "org.videolan.vlc" to "VLC",
        "com.plexapp.android" to "Plex",
        "com.android.vending" to "Google Play",
        "com.android.tv.settings" to "电视设置",
        "tv.danmaku.bili" to "哔哩哔哩"
    )

    /** LEANBACK entries go first so TV apps open their television UI. */
    fun parse(leanback: String, launcher: String): List<RemoteApp> {
        val byPackage = linkedMapOf<String, RemoteApp>()
        for (line in (leanback + "\n" + launcher).lineSequence()) {
            val component = line.trim()
            val match = componentLine.matchEntire(component) ?: continue
            val packageName = match.groupValues[1]
            byPackage.putIfAbsent(packageName,
                RemoteApp(packageName, component, knownNames[packageName] ?: packageName))
        }
        return byPackage.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    fun isSafeComponent(component: String) = componentLine.matches(component)

    fun launchCommand(component: String): String {
        require(isSafeComponent(component)) { "应用启动入口无效" }
        return "am start -n '$component'"
    }

    /** Metadata lines are emitted by the shell-side read-only catalog helper. */
    fun parseMetadata(output: String): List<RemoteApp> {
        val declared = output.lineSequence().firstOrNull { it.startsWith("RMAPP_READY\t") }
            ?.substringAfter('\t')?.toIntOrNull()
            ?: error("电视未返回应用信息")
        val apps = linkedMapOf<String, RemoteApp>()
        for (line in output.lineSequence()) {
            if (!line.startsWith("RMAPP\t")) continue
            val row = JSONObject(line.substringAfter('\t'))
            val packageName = row.getString("package")
            val component = row.getString("component")
            if (!isSafeComponent(component) || !component.startsWith("$packageName/")) continue
            val label = row.optString("label").trim().ifBlank { packageName }
            val encoded = row.optString("icon")
            val icon = runCatching {
                if (encoded.length > 200_000) null
                else encoded.takeIf { it.isNotEmpty() }?.let(Base64.getDecoder()::decode)
            }.getOrNull()
            apps.putIfAbsent(packageName, RemoteApp(packageName, component, label, icon))
        }
        check(declared == 0 || apps.isNotEmpty()) { "电视应用元数据为空" }
        return apps.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }
}
