package com.remotemix.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("MissingPermission")
class BluetoothRemote(context: Context) {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var statusListener: ((String) -> Unit)? = null
    private var lastStatus = ""
    private var hid: BluetoothHidDevice? = null
    private var device: BluetoothDevice? = null
    private var ready = false
    private var starting = false
    private var registering = false
    private var connectingAddress: String? = null
    private var generation = 0
    private var nextPressAt = 0L

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            registering = false
            ready = registered
            if (!registered) { device = null; connectingAddress = null; generation++ }
            else if (pluggedDevice != null) { device = pluggedDevice; connectingAddress = null }
            emit(when {
                !registered -> "蓝牙 HID 已停止，请返回蓝牙界面重试"
                pluggedDevice != null -> "蓝牙已连接：${pluggedDevice.name ?: pluggedDevice.address}"
                else -> "蓝牙 HID 已就绪，请选已配对设备"
            })
        }

        override fun onConnectionStateChanged(remoteDevice: BluetoothDevice, state: Int) {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                device = remoteDevice
                connectingAddress = null
                emit("蓝牙已连接：${remoteDevice.name ?: remoteDevice.address}")
            } else if (state == BluetoothProfile.STATE_DISCONNECTED &&
                (device?.address == remoteDevice.address || connectingAddress == remoteDevice.address)) {
                generation++
                device = null
                connectingAddress = null
                emit("蓝牙连接已断开")
            }
        }
    }

    private val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            starting = false
            val service = proxy as? BluetoothHidDevice ?: return
            hid = service
            register(service)
        }

        override fun onServiceDisconnected(profile: Int) {
            starting = false
            hid = null
            ready = false
            registering = false
            device = null
            connectingAddress = null
            generation++
            emit("蓝牙服务已断开")
        }
    }

    fun setStatusListener(listener: ((String) -> Unit)?) {
        statusListener = listener
        if (listener != null && lastStatus.isNotEmpty()) listener(lastStatus)
    }

    fun clearStatusListener(listener: (String) -> Unit) {
        if (statusListener === listener) statusListener = null
    }

    fun currentStatus() = lastStatus
    fun isReady() = ready
    fun connectedAddress() = device?.address?.takeIf { ready }
    fun connectedName() = device?.takeIf { ready }?.let { it.name ?: it.address }

    private fun emit(message: String) { lastStatus = message; statusListener?.invoke(message) }

    private fun register(service: BluetoothHidDevice) {
        if (ready || registering) return
        registering = true
        val settings = BluetoothHidDeviceAppSdpSettings(
            "Remote Mix", "电视遥控器", "Remote Mix", SUBCLASS.toByte(), DESCRIPTOR
        )
        if (!service.registerApp(settings, null, null, { command -> handler.post(command) }, callback)) {
            registering = false
            emit("蓝牙 HID 注册请求失败")
        }
    }

    fun start() {
        val a = adapter ?: error("手机没有蓝牙适配器")
        check(a.isEnabled) { "请先开启手机蓝牙" }
        hid?.let { register(it); return }
        if (starting) return
        starting = true
        try {
            check(a.getProfileProxy(context, listener, 19)) { "手机未提供 HID Device 服务" }
        } catch (error: Exception) {
            starting = false
            throw error
        }
        emit("正在启动蓝牙 HID…")
    }

    fun bondedDevices(): List<BluetoothDevice> =
        adapter?.bondedDevices?.sortedBy { it.name ?: it.address } ?: emptyList()

    fun connect(remoteDevice: BluetoothDevice) {
        check(ready && hid != null) { "蓝牙 HID 尚未就绪" }
        if (device?.address == remoteDevice.address || connectingAddress == remoteDevice.address) return
        generation++
        device?.let { hid?.disconnect(it) }
        device = null
        connectingAddress = remoteDevice.address
        if (!hid!!.connect(remoteDevice)) {
            connectingAddress = null
            error("电视拒绝蓝牙连接，请先在电视上配对")
        }
        emit("正在连接 ${remoteDevice.name ?: remoteDevice.address}…")
    }

    fun isConnected() = device != null && ready

    fun disconnect() {
        generation++
        device?.let { runCatching { hid?.disconnect(it) } }
        device = null
        connectingAddress = null
    }

    /** Cancel queued key presses without dropping the HID connection. */
    fun cancelPendingReports() {
        generation++
        nextPressAt = SystemClock.uptimeMillis()
    }

    fun send(action: RemoteAction) {
        val target = device ?: error("蓝牙尚未连接")
        val service = hid ?: error("蓝牙 HID 尚未就绪")
        val (reportId, pressed) = pressed(action)
        val released = ByteArray(pressed.size)
        val ticket = generation
        val pressedAccepted = AtomicBoolean(false)
        val at = maxOf(SystemClock.uptimeMillis(), nextPressAt)
        nextPressAt = at + 75
        handler.postAtTime({
            if (ticket == generation && target == device) {
                runCatching { service.sendReport(target, reportId, pressed) }
                    .onSuccess { accepted ->
                        if (accepted) pressedAccepted.set(true)
                        emit(if (accepted) "蓝牙已发送：${action.label}" else "蓝牙按键发送失败：电视连接未接受报告")
                    }
                    .onFailure { error -> emit("蓝牙按键发送失败：${error.message ?: "蓝牙服务异常"}") }
            }
        }, at)
        handler.postAtTime({
            if (pressedAccepted.get() && target == device) {
                runCatching { service.sendReport(target, reportId, released) }
                    .onSuccess { accepted -> if (!accepted) emit("蓝牙松键报告发送失败") }
                    .onFailure { error -> emit("蓝牙松键失败：${error.message ?: "蓝牙服务异常"}") }
            }
        }, at + 45)
    }

    fun close() {
        disconnect()
        val service = hid
        val a = adapter
        runCatching { if (service != null) service.unregisterApp() }
        runCatching { if (service != null) a?.closeProfileProxy(19, service) }
        device = null
        hid = null
        ready = false
        starting = false
        registering = false
    }

    companion object {
        internal const val SUBCLASS = 0

        internal fun pressed(action: RemoteAction): Pair<Int, ByteArray> =
            if (action.hidConsumer != 0) {
                2 to byteArrayOf(action.hidConsumer.toByte(), (action.hidConsumer shr 8).toByte())
            } else {
                1 to byteArrayOf(0, action.hidKey.toByte())
            }

        // Report 1: keyboard, report 2: consumer controls, report 3: mouse.
        // The mouse collection is advertised for compatibility; this app does not send mouse input.
        internal val DESCRIPTOR = byteArrayOf(
            0x05, 0x0C, 0x09, 0x01, 0xA1.toByte(), 0x01, 0x85.toByte(), 0x02,
            0x19, 0x00, 0x2A, 0xFF.toByte(), 0x03, 0x75, 0x10,
            0x95.toByte(), 0x01, 0x15, 0x00, 0x26, 0xFF.toByte(), 0x03,
            0x81.toByte(), 0x00, 0xC0.toByte(),
            0x05, 0x01, 0x09, 0x06, 0xA1.toByte(), 0x01, 0x85.toByte(), 0x01,
            0x05, 0x07, 0x19, 0xE0.toByte(), 0x29, 0xE7.toByte(), 0x15, 0x00,
            0x25, 0x01, 0x75, 0x01, 0x95.toByte(), 0x08, 0x81.toByte(), 0x02,
            0x75, 0x08, 0x95.toByte(), 0x01, 0x15, 0x00,
            0x26, 0xFF.toByte(), 0x00, 0x05, 0x07, 0x19, 0x00,
            0x29, 0xFF.toByte(), 0x81.toByte(), 0x00, 0xC0.toByte(),
            0x05, 0x01, 0x09, 0x02, 0xA1.toByte(), 0x01, 0x85.toByte(), 0x03,
            0x09, 0x01, 0xA1.toByte(), 0x00, 0x05, 0x09, 0x19, 0x01,
            0x29, 0x03, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01,
            0x95.toByte(), 0x03, 0x81.toByte(), 0x02, 0x75, 0x05,
            0x95.toByte(), 0x01, 0x81.toByte(), 0x01, 0x05, 0x01,
            0x09, 0x30, 0x09, 0x31, 0x09, 0x38, 0x15, 0x81.toByte(),
            0x25, 0x7F, 0x75, 0x08, 0x95.toByte(), 0x03, 0x81.toByte(), 0x06,
            0xC0.toByte(), 0xC0.toByte()
        )
    }
}
