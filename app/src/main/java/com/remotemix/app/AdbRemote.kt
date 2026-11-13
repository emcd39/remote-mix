package com.remotemix.app

import android.content.Context
import io.github.rhythmcache.adb.AdbClient
import io.github.rhythmcache.adb.FileKeyProvider
import kotlinx.coroutines.runBlocking
import android.util.Base64
import java.io.File
import java.security.MessageDigest

class AdbRemote(context: Context) {
    private val appContext = context.applicationContext
    private val keyProvider = FileKeyProvider(File(context.filesDir, "adbkey"), identityComment = "remote-mix@android")
    private var client: AdbClient? = null
    private var endpoint = ""

    @Synchronized
    fun connect(host: String, port: Int, tls: Boolean, peerFingerprint: String) {
        require(host.isNotBlank() && port in 1..65535) { "请填写有效的 ADB 地址和端口" }
        val newEndpoint = "${if (tls) "tls" else "tcp"}://$host:$port/$peerFingerprint"
        if (endpoint == newEndpoint && client != null) return
        disconnect()
        val candidate = runBlocking {
            if (tls) {
                require(peerFingerprint.matches(Regex("[0-9a-fA-F]{64}"))) { "请先在本 App 完成无线调试配对" }
                val fingerprint = peerFingerprint.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                AdbClient.connectTls(host, port, keyProvider, handshakeTimeoutMs = 10_000,
                    trustedServerCertificateSha256 = fingerprint)
            }
            else AdbClient.connectTcp(host, port, keyProvider, handshakeTimeoutMs = 10_000)
        }
        try {
            runBlocking { candidate.shell("true") }
            client = candidate
            endpoint = newEndpoint
        } catch (error: Exception) {
            candidate.close()
            throw error
        }
    }

    fun pair(host: String, pairingPort: Int, code: String): String {
        require(host.isNotBlank() && pairingPort in 1..65535 && code.matches(Regex("[0-9]{6}"))) {
            "请填写配对地址、端口和 6 位配对码"
        }
        val result = runBlocking {
            AdbClient.pairTls(host, pairingPort, code, keyProvider, connectTimeoutMs = 10_000).getOrThrow()
        }
        val pem = requireNotNull(result.peerCertPem) { "配对成功但没有设备证书，无法安全重连" }
        val der = Base64.decode(pem.lineSequence().filterNot { it.startsWith("-----") }.joinToString(""), Base64.DEFAULT)
        return MessageDigest.getInstance("SHA-256").digest(der).joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    @Synchronized
    fun send(action: RemoteAction) {
        val active = client ?: error("ADB 尚未连接")
        try {
            val response = runBlocking { active.shell("input keyevent ${action.adbCode}") }
            check(response.isSuccess) { "ADB 按键失败：${response.stderrText.ifBlank { response.stdoutText }}" }
        } catch (error: Exception) {
            disconnect()
            throw error
        }
    }

    @Synchronized
    fun listApps(): List<RemoteApp> {
        val active = client ?: error("ADB 尚未连接")
        val metadata = runCatching {
            val remotePath = "/data/local/tmp/remotemix-app-catalog.dex"
            runBlocking {
                appContext.assets.open("remote-app-catalog.dex").use { input ->
                    active.sync.push(input, remotePath)
                }
            }
            val result = runBlocking {
                active.shell("CLASSPATH=$remotePath app_process / com.remotemix.catalog.RemoteAppCatalogProbe")
            }
            check(result.isSuccess && result.stdoutText.length <= 8_000_000) {
                result.stderrText.ifBlank { "电视不支持读取应用信息" }
            }
            RemoteAppCatalog.parseMetadata(result.stdoutText)
        }
        if (metadata.isSuccess) return metadata.getOrThrow()

        fun query(category: String) = runCatching {
            val result = runBlocking {
                active.shell("cmd package query-activities --components -a android.intent.action.MAIN -c android.intent.category.$category")
            }
            check(result.isSuccess) { result.stderrText.ifBlank { result.stdoutText } }
            result.stdoutText
        }
        val leanback = query("LEANBACK_LAUNCHER")
        val launcher = query("LAUNCHER")
        if (leanback.isFailure && launcher.isFailure) {
            disconnect()
            throw IllegalStateException("读取电视应用失败：${launcher.exceptionOrNull()?.message ?: leanback.exceptionOrNull()?.message}")
        }
        return RemoteAppCatalog.parse(leanback.getOrDefault(""), launcher.getOrDefault(""))
    }

    @Synchronized
    fun launchApp(app: RemoteApp) {
        check(RemoteAppCatalog.isSafeComponent(app.component)) { "应用启动入口无效" }
        val active = client ?: error("ADB 尚未连接")
        try {
            val result = runBlocking { active.shell("am start -n ${app.component}") }
            check(result.isSuccess &&
                !result.stdoutText.contains("Error:", ignoreCase = true) &&
                !result.stdoutText.contains("Error type", ignoreCase = true)) {
                "启动失败：${result.stderrText.ifBlank { result.stdoutText }}"
            }
        } catch (error: Exception) {
            if (error.message?.contains("closed", ignoreCase = true) == true) disconnect()
            throw error
        }
    }

    @Synchronized
    fun disconnect() {
        client?.close()
        client = null
        endpoint = ""
    }
}
