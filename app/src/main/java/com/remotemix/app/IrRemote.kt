package com.remotemix.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.hardware.ConsumerIrManager
import org.json.JSONArray
import java.io.File

data class IrBrand(val name: String, val count: Int) {
    val label: String get() = "${if (name == "未知品牌") "通用／其他" else name} · $count 套"
}

data class IrRegion(val id: Int, val name: String, val parentId: Int)

class IrRemote(private val context: Context) {
    private val emitter = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    private val database: SQLiteDatabase by lazy {
        val local = File(context.filesDir, "ir-tv-v4.sqlite")
        if (!local.exists()) {
            context.assets.open("ir-tv.sqlite").use { input ->
                local.outputStream().use { output -> input.copyTo(output) }
            }
        }
        SQLiteDatabase.openDatabase(local.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    fun hasEmitter() = emitter?.hasIrEmitter() == true

    fun regions(parentId: Int): List<IrRegion> {
        val found = mutableListOf<IrRegion>()
        val cursor = database.rawQuery(
            "SELECT id,name,parent_id FROM live_area WHERE parent_id=? ORDER BY id",
            arrayOf(parentId.toString())
        )
        try {
            while (cursor.moveToNext()) found += IrRegion(cursor.getInt(0), cursor.getString(1), cursor.getInt(2))
        } finally {
            cursor.close()
        }
        return found
    }

    fun brands(deviceId: String): List<IrBrand> {
        val found = mutableListOf<IrBrand>()
        val cursor = database.rawQuery(
            "SELECT brand_name,COUNT(*) FROM remotes WHERE device_id=? " +
                "AND code_id IN (SELECT code_id FROM keys WHERE key_id='power') " +
                "GROUP BY brand_name ORDER BY CASE WHEN brand_name='未知品牌' THEN 1 ELSE 0 END,brand_name",
            arrayOf(deviceId)
        )
        try {
            while (cursor.moveToNext()) found += IrBrand(cursor.getString(0), cursor.getInt(1))
        } finally {
            cursor.close()
        }
        return found
    }

    fun matchCandidates(deviceId: String, brand: String, regionId: Int? = null, parentRegionId: Int? = null): List<IrMatchCandidate> {
        val found = mutableListOf<IrMatchCandidate>()
        val cursor = database.rawQuery(
            "SELECT r.code_id,r.frequency_hz,p.pattern_us_json," +
                "(SELECT k.key_id FROM keys k WHERE k.code_id=r.code_id " +
                "AND k.key_id IN ('vol+','up','ok','ch+','menu') " +
                "ORDER BY CASE k.key_id " +
                (if (deviceId == "1") "WHEN 'vol+' THEN 0 WHEN 'ch+' THEN 1 WHEN 'up' THEN 2 "
                 else "WHEN 'up' THEN 0 WHEN 'ok' THEN 1 WHEN 'vol+' THEN 2 ") +
                "ELSE 3 END LIMIT 1) " +
                "FROM remotes r JOIN keys p ON p.code_id=r.code_id AND p.key_id='power' " +
                "WHERE r.device_id=? AND r.brand_name=? " +
                "ORDER BY CASE WHEN ? != '' AND r.sp_id=? THEN 0 " +
                "WHEN ? != '' AND r.sp_id=? THEN 1 ELSE 2 END,r.match_rank,r.code_id",
            arrayOf(deviceId, brand,
                regionId?.toString() ?: "", regionId?.toString() ?: "",
                parentRegionId?.toString() ?: "", parentRegionId?.toString() ?: "")
        )
        try {
            while (cursor.moveToNext()) {
                val verifyKey = cursor.getString(3) ?: continue
                found += IrMatchCandidate(
                    cursor.getString(0), "${cursor.getInt(1)}:${cursor.getString(2)}", verifyKey
                )
            }
        } finally {
            cursor.close()
        }
        return found
    }

    fun sendTest(codeId: String, keyId: String) = transmit(codeId, listOf(keyId))

    fun send(codeId: String, action: RemoteAction) {
        val alternatives = when (action) {
            RemoteAction.BACK -> listOf("back", "exit")
            RemoteAction.HOME -> listOf("home", "menu")
            RemoteAction.INPUT -> listOf("input", "tv_av")
            RemoteAction.PLAY_PAUSE -> listOf("play", "pause")
            else -> listOf(action.irKey)
        }
        transmit(codeId, alternatives)
    }

    private fun transmit(codeId: String, alternatives: List<String>) {
        val ir = emitter ?: error("手机没有红外发射器")
        check(ir.hasIrEmitter()) { "手机没有红外发射器" }
        check(codeId.isNotBlank()) { "请先匹配红外遥控器" }
        val placeholders = alternatives.joinToString(",") { "?" }
        val args = arrayOf(codeId, *alternatives.toTypedArray())
        val cursor = database.rawQuery(
            "SELECT r.frequency_hz,k.pattern_us_json FROM remotes r JOIN keys k USING(code_id) " +
                "WHERE r.code_id=? AND k.key_id IN ($placeholders) " +
                "ORDER BY CASE k.key_id WHEN ? THEN 0 ELSE 1 END LIMIT 1",
            args + alternatives.first()
        )
        try {
            check(cursor.moveToFirst()) { "此码组没有“${alternatives.first()}”按键，请重新匹配" }
            val pattern = JSONArray(cursor.getString(1))
            val pulses = IntArray(pattern.length()) { pattern.getInt(it) }
            ir.transmit(cursor.getInt(0), pulses)
        } finally {
            cursor.close()
        }
    }
}
