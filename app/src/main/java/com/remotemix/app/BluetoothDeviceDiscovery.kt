package com.remotemix.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

/** One visible-device scan owned by the chooser dialog. Pairing stays with Android's system UI. */
@SuppressLint("MissingPermission")
internal class BluetoothDeviceDiscovery(
    context: Context,
    private val onFound: (BluetoothDevice) -> Unit,
    private val onState: (String) -> Unit,
    private val onBondResult: (BluetoothDevice, Boolean) -> Unit
) {
    private val appContext = context.applicationContext
    private val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
    private var registered = false
    private var bondingAddress: String? = null
    private var restartPending = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> deviceFrom(intent)?.let(onFound)
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (restartPending) {
                        restartPending = false
                        start()
                    } else if (bondingAddress == null)
                        onState("搜索完成；如未看到电视，请先在电视上开启蓝牙可见模式。")
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = deviceFrom(intent) ?: return
                    if (device.address != bondingAddress) return
                    when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)) {
                        BluetoothDevice.BOND_BONDED -> {
                            bondingAddress = null
                            onBondResult(device, true)
                        }
                        BluetoothDevice.BOND_NONE -> if (intent.getIntExtra(
                                BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE
                            ) == BluetoothDevice.BOND_BONDING) {
                            bondingAddress = null
                            onBondResult(device, false)
                        }
                    }
                }
            }
        }
    }

    fun start() {
        val radio = adapter ?: run { onState("这部手机没有蓝牙适配器"); return }
        if (!radio.isEnabled) { onState("请先在系统设置中开启蓝牙，然后重新搜索"); return }
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= 33)
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            else appContext.registerReceiver(receiver, filter)
            registered = true
        }
        if (radio.isDiscovering) {
            restartPending = true
            radio.cancelDiscovery()
            onState("正在重新搜索附近设备…")
            return
        }
        onState(if (radio.startDiscovery()) "正在搜索附近可见的蓝牙设备…" else "无法开始搜索，请检查蓝牙是否开启")
    }

    fun pair(device: BluetoothDevice) {
        restartPending = false
        bondingAddress = device.address
        adapter?.cancelDiscovery()
        if (!device.createBond()) {
            bondingAddress = null
            onState("系统未能开始配对，请在电视上开启配对模式后重试")
        } else onState("正在与 ${device.name ?: device.address} 配对，请确认系统提示…")
    }

    fun close() {
        restartPending = false
        runCatching { adapter?.cancelDiscovery() }
        if (registered) {
            runCatching { appContext.unregisterReceiver(receiver) }
            registered = false
        }
        bondingAddress = null
    }

    @Suppress("DEPRECATION")
    private fun deviceFrom(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
}
