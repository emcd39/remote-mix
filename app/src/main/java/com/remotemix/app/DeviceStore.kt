package com.remotemix.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class DeviceProfile(
    val id: String,
    var name: String,
    var adbHost: String = "",
    var adbPort: Int = 5555,
    var adbTls: Boolean = false,
    var adbPeerFingerprint: String = "",
    var bluetoothAddress: String = "",
    var irCodeId: String = "",
    var mode: ControlMode = ControlMode.ADB
)

class DeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences("devices", Context.MODE_PRIVATE)

    fun load(): MutableList<DeviceProfile> {
        val result = mutableListOf<DeviceProfile>()
        val array = runCatching { JSONArray(prefs.getString("profiles", "[]")) }.getOrDefault(JSONArray())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            result += DeviceProfile(
                item.optString("id"), item.optString("name", "设备"),
                item.optString("adbHost"), item.optInt("adbPort", 5555), item.optBoolean("adbTls", false),
                item.optString("adbPeerFingerprint"),
                item.optString("bluetoothAddress"), item.optString("irCodeId"),
                ControlMode.entries.firstOrNull { it.name == item.optString("mode") } ?: ControlMode.ADB
            )
        }
        return result
    }

    fun save(profiles: List<DeviceProfile>) {
        val array = JSONArray()
        profiles.forEach { p ->
            array.put(JSONObject().put("id", p.id).put("name", p.name)
                .put("adbHost", p.adbHost).put("adbPort", p.adbPort).put("adbTls", p.adbTls)
                .put("adbPeerFingerprint", p.adbPeerFingerprint)
                .put("bluetoothAddress", p.bluetoothAddress).put("irCodeId", p.irCodeId)
                .put("mode", p.mode.name))
        }
        prefs.edit().putString("profiles", array.toString()).apply()
    }

    // Profile edits and pairing results run on the main thread.
    fun update(id: String, change: (DeviceProfile) -> Unit): MutableList<DeviceProfile>? {
        val profiles = load()
        val profile = profiles.firstOrNull { it.id == id } ?: return null
        change(profile)
        save(profiles)
        return profiles
    }

    fun activeId(): String = prefs.getString("active", "") ?: ""
    fun setActiveId(id: String) { prefs.edit().putString("active", id).apply() }
}
