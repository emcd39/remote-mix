package com.remotemix.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val background = UiPalette.background
    private val ink = UiPalette.ink
    private val muted = UiPalette.muted
    private val teal = UiPalette.accent
    private val white = UiPalette.surface
    private val panelText = UiPalette.panelText
    private val initialPalette = UiPalette.current.id
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var store: DeviceStore
    private lateinit var adb: AdbRemote
    private lateinit var bluetooth: BluetoothRemote
    private lateinit var ir: IrRemote
    private lateinit var devices: MutableList<DeviceProfile>
    private lateinit var active: DeviceProfile
    private lateinit var status: TextView
    private lateinit var modeButtons: List<Button>
    private lateinit var setupButton: Button
    private lateinit var extraButton: Button
    private lateinit var remotePanel: RemotePanel
    private lateinit var headerTitle: TextView
    @Volatile private var generation = 0
    private var displayedMode: ControlMode? = null
    private var appsLoading = false
    private var adbDiscovery: AdbDiscovery? = null
    private var bluetoothDiscovery: BluetoothDeviceDiscovery? = null
    private val bluetoothStatusListener: (String) -> Unit = { message ->
        runOnUiThread {
            if (::active.isInitialized && active.mode == ControlMode.BLUETOOTH && !isFinishing) {
                showStatus(message)
                if (message.startsWith("蓝牙 HID 已就绪")) connectSavedBluetooth()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = DeviceStore(this)
        devices = store.load()
        active = devices.firstOrNull { it.id == intent.getStringExtra("profile_id") }
            ?: run { finish(); return }
        store.setActiveId(active.id)
        adb = AdbRemote(this)
        ir = IrRemote(this)
        bluetooth = (application as RemoteMixApp).bluetooth
        buildUi()
        selectMode(active.mode)
    }

    override fun onStart() {
        super.onStart()
        bluetooth.setStatusListener(bluetoothStatusListener)
    }

    override fun onStop() {
        bluetooth.clearStatusListener(bluetoothStatusListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (initialPalette != UiPalette.current.id) { recreate(); return }
        if (::active.isInitialized && active.mode == ControlMode.BLUETOOTH &&
            (Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)) {
            ensureBluetooth()
            if (bluetooth.isReady()) connectSavedBluetooth()
        }
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(UiPalette.panel)
            isFillViewport = true
        }
        val root = column().apply { setPadding(dp(12), dp(4), dp(12), dp(8)) }
        scroll.addView(root)
        setContentView(scroll)
        SystemBars.attach(this, scroll, UiPalette.panel, lightIcons = false)

        val header = row()
        header.addView(text("‹", 32f, panelText).apply {
            gravity = Gravity.CENTER
            contentDescription = "返回设备列表"
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        headerTitle = text(active.name, 19f, panelText, true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
        }
        header.addView(headerTitle, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(2)
            marginEnd = dp(2)
        })
        header.addView(text("⚙", 25f, panelText).apply {
            gravity = Gravity.CENTER
            contentDescription = "外观设置"
            setOnClickListener { startActivity(Intent(this@MainActivity, AppearanceActivity::class.java)) }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(header)

        val modeRow = row()
        modeButtons = ControlMode.entries.map { mode ->
            button(mode.label, false) { selectMode(mode) }.apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
                modeRow.addView(this)
            }
        }
        modeRow.background = rounded(UiPalette.panelInset, 22)
        root.addView(modeRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val statusRow = row()
        status = text("请选择控制方式", 13f, UiPalette.panelMuted).apply {
            setPadding(dp(6), dp(4), dp(4), dp(4))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        statusRow.addView(status, LinearLayout.LayoutParams(0, dp(40), 1f).apply { gravity = Gravity.CENTER_VERTICAL })
        setupButton = button("设置", false) { openSetup() }
        statusRow.addView(setupButton, LinearLayout.LayoutParams(dp(68), dp(40)))
        extraButton = button("搜索", false) {
            when (active.mode) {
                ControlMode.ADB -> searchAdbDevices()
                ControlMode.BLUETOOTH -> pairBluetooth()
                ControlMode.IR -> Unit
            }
        }
        statusRow.addView(extraButton, LinearLayout.LayoutParams(dp(68), dp(40)).apply { marginStart = dp(4) })
        root.addView(statusRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })

        remotePanel = RemotePanel(this, ::send, ::openApps)
        root.addView(remotePanel, LinearLayout.LayoutParams(-1, -2))
        // Fill spare viewport height by spacing groups, while preserving every key's touch size.
        root.post {
            if (isFinishing || isDestroyed) return@post
            val spare = root.height - remotePanel.bottom - root.paddingBottom
            if (spare > 0) {
                remotePanel.layoutParams = (remotePanel.layoutParams as LinearLayout.LayoutParams).apply {
                    height = remotePanel.height + spare
                }
            }
        }
    }

    private fun selectMode(mode: ControlMode) {
        generation++
        appsLoading = false
        if (displayedMode == ControlMode.BLUETOOTH && mode != ControlMode.BLUETOOTH) bluetooth.cancelPendingReports()
        displayedMode = mode
        active.mode = mode
        store.save(devices)
        headerTitle.text = active.name
        remotePanel.setMode(mode)
        modeButtons.forEachIndexed { index, button ->
            val selected = ControlMode.entries[index] == mode
            button.background = ripple(if (selected) UiPalette.panelAccent else UiPalette.panelInset, 22)
            button.setTextColor(if (selected) UiPalette.panel else UiPalette.panelText)
            button.isSelected = selected
        }
        setupButton.text = when (mode) {
            ControlMode.ADB -> "设置"
            ControlMode.BLUETOOTH -> "连接"
            ControlMode.IR -> "匹配"
        }
        extraButton.visibility = if (mode == ControlMode.IR) View.GONE else View.VISIBLE
        extraButton.text = if (mode == ControlMode.BLUETOOTH) "配对" else "搜索"
        extraButton.contentDescription = if (mode == ControlMode.BLUETOOTH) "配对新的蓝牙电视或盒子" else "搜索局域网内的 ADB 设备"
        setupButton.contentDescription = when (mode) {
            ControlMode.ADB -> "设置 ADB 地址或配对"
            ControlMode.BLUETOOTH -> "选择已配对的蓝牙设备"
            ControlMode.IR -> "匹配红外遥控器"
        }
        when (mode) {
            ControlMode.ADB -> showStatus(if (active.adbHost.isBlank()) "请先设置电视的 ADB 地址" else "ADB ${if (active.adbTls) "TLS" else "TCP"} ${active.adbHost}:${active.adbPort} · 点击按键时连接")
            ControlMode.BLUETOOTH -> {
                showStatus(bluetooth.connectedName()?.let { "蓝牙已连接：$it" }
                    ?: "蓝牙设备：${active.bluetoothAddress.ifBlank { "未选择" }}")
                ensureBluetooth()
                if (bluetooth.isReady()) connectSavedBluetooth()
            }
            ControlMode.IR -> showStatus(if (ir.hasEmitter()) "红外码组：${active.irCodeId.ifBlank { "未匹配" }}" else "手机没有红外发射器")
        }
    }

    private fun send(action: RemoteAction) {
        val profile = active
        val ticket = generation
        when (profile.mode) {
            ControlMode.ADB -> {
                if (profile.adbHost.isBlank()) { showStatus("请先设置 ADB 地址"); return }
                showStatus("正在发送：${action.label}…")
                worker.execute {
                    runCatching {
                        if (ticket != generation) return@execute
                        adb.connect(profile.adbHost, profile.adbPort, profile.adbTls, profile.adbPeerFingerprint)
                        if (ticket != generation) return@execute
                        adb.send(action)
                    }.onSuccess { if (ticket == generation) runOnUiThread { showStatus("ADB 已连接 · ${action.label}已发送") } }
                        .onFailure { error -> if (ticket == generation) runOnUiThread { showStatus("ADB：${error.message ?: "发送失败"}") } }
                }
            }
            ControlMode.BLUETOOTH -> runCatching { bluetooth.send(action) }
                .onFailure { showStatus(it.message ?: "蓝牙发送失败") }
            ControlMode.IR -> {
                worker.execute {
                    runCatching { ir.send(profile.irCodeId, action) }
                        .onSuccess { if (ticket == generation) runOnUiThread { showStatus("红外“${action.label}”已发送") } }
                        .onFailure { if (ticket == generation) runOnUiThread { showStatus(it.message ?: "红外发送失败") } }
                }
            }
        }
    }

    private fun openApps() {
        val profile = active
        if (profile.mode != ControlMode.ADB || appsLoading) return
        if (profile.adbHost.isBlank()) { showStatus("请先设置 ADB 地址"); return }
        val ticket = generation
        appsLoading = true
        showStatus("正在读取电视应用…")
        worker.execute {
            if (ticket != generation) return@execute
            runCatching {
                adb.connect(profile.adbHost, profile.adbPort, profile.adbTls, profile.adbPeerFingerprint)
                if (ticket != generation) return@execute
                adb.listApps()
            }.onSuccess { apps -> runOnUiThread {
                if (ticket == generation) appsLoading = false
                if (ticket != generation || active.id != profile.id) return@runOnUiThread
                if (apps.isEmpty()) showStatus("电视没有返回可启动的应用")
                else {
                    showStatus("已读取 ${apps.size} 个可启动应用")
                    RemoteAppsDialog.show(this, apps) { selected -> launchApp(selected, profile, ticket) }
                }
            } }.onFailure { error -> runOnUiThread {
                if (ticket == generation) appsLoading = false
                if (ticket == generation) showStatus("读取应用失败：${error.message ?: "ADB 连接中断"}")
            } }
        }
    }

    private fun launchApp(app: RemoteApp, profile: DeviceProfile, ticket: Int) {
        if (ticket != generation || active.id != profile.id) return
        showStatus("正在打开 ${app.label}…")
        worker.execute {
            if (ticket != generation) return@execute
            runCatching { adb.launchApp(app) }
                .onSuccess { runOnUiThread { if (ticket == generation) showStatus("已打开 ${app.label}") } }
                .onFailure { error -> runOnUiThread {
                    if (ticket == generation) showStatus("打开 ${app.label} 失败：${error.message}")
                } }
        }
    }

    private fun openSetup() = when (active.mode) {
        ControlMode.ADB -> editDevice()
        ControlMode.BLUETOOTH -> chooseBluetooth()
        ControlMode.IR -> chooseIr()
    }

    private fun searchAdbDevices() {
        if (adbDiscovery != null) return
        val content = column().apply { setPadding(dp(18), dp(8), dp(18), dp(8)) }
        val message = text("请确保手机与电视在同一局域网，并已开启网络调试。", 14f, ink)
        content.addView(message)
        val results = column()
        content.addView(ScrollView(this).apply { addView(results) },
            LinearLayout.LayoutParams(-1, dp(300)).apply { topMargin = dp(12) })
        val refresh = button("重新搜索", false) { }
        content.addView(refresh, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        val dialog = AlertDialog.Builder(this).setTitle("搜索局域网 ADB 设备")
            .setView(content).setNegativeButton("关闭", null).create()
        val found = linkedMapOf<String, AdbEndpoint>()
        fun renderResults() {
            results.removeAllViews()
            if (found.isEmpty()) {
                results.addView(text("正在查找电视、盒子和网络调试服务…", 15f, muted))
            } else found.values.forEach { endpoint ->
                val suffix = if (endpoint.tls) "无线调试 · 需配对" else "TCP · 连接时验证"
                results.addView(button("${endpoint.name}\n${endpoint.host}:${endpoint.port} · $suffix", false) {
                    val previousHost = active.adbHost
                    active.adbHost = endpoint.host
                    active.adbPort = endpoint.port
                    active.adbTls = endpoint.tls
                    if (previousHost != endpoint.host || !endpoint.tls) active.adbPeerFingerprint = ""
                    store.save(devices)
                    worker.execute { adb.disconnect() }
                    selectMode(ControlMode.ADB)
                    dialog.dismiss()
                    if (endpoint.tls && active.adbPeerFingerprint.isBlank()) pairAdb()
                    else showStatus("已选择 ${endpoint.host}:${endpoint.port}，点击按键时连接")
                }, LinearLayout.LayoutParams(-1, dp(68)).apply { bottomMargin = dp(8) })
            }
        }
        fun startSearch() {
            adbDiscovery?.stop()
            found.clear()
            renderResults()
            message.text = "正在搜索当前局域网…"
            adbDiscovery = AdbDiscovery(this, devices.map { it.adbHost }.filter { it.isNotBlank() },
                onFound = { endpoint ->
                    found[endpoint.key] = endpoint
                    message.text = "找到 ${found.size} 个可尝试连接的地址"
                    renderResults()
                }, onComplete = {
                    message.text = if (found.isEmpty()) "没有发现设备，请检查电视的网络调试，或手动填写地址。"
                        else "搜索完成 · 找到 ${found.size} 个地址"
                }).also { it.start() }
        }
        refresh.setOnClickListener { startSearch() }
        dialog.setOnDismissListener { adbDiscovery?.stop(); adbDiscovery = null }
        dialog.show()
        startSearch()
    }

    private fun editDevice() {
        val form = column().apply { setPadding(dp(20), dp(8), dp(20), 0) }
        val name = field("设备名称", active.name)
        val host = field("电视 / 盒子 IP 地址", active.adbHost)
        val port = field("ADB 连接端口", active.adbPort.toString()).apply { inputType = 2 }
        val tls = CheckBox(this).apply { text = "新版无线调试（TLS）"; isChecked = active.adbTls }
        form.addView(name); form.addView(host); form.addView(port); form.addView(tls)
        AlertDialog.Builder(this).setTitle("设备设置").setView(form)
            .setPositiveButton("保存") { _, _ ->
                val value = port.text.toString().toIntOrNull()
                if (value == null || value !in 1..65535) { showStatus("ADB 端口必须是 1–65535"); return@setPositiveButton }
                active.name = name.text.toString().trim().ifBlank { "设备" }
                active.adbHost = host.text.toString().trim()
                active.adbPort = value
                active.adbTls = tls.isChecked
                store.save(devices)
                generation++
                worker.execute { adb.disconnect() }
                selectMode(active.mode)
            }
            .setNeutralButton("配对码配对") { _, _ -> pairAdb() }
            .setNegativeButton("取消", null).show()
    }

    private fun pairAdb() {
        val form = column().apply { setPadding(dp(20), dp(8), dp(20), 0) }
        val host = field("电视 IP 地址", active.adbHost)
        val port = field("配对端口（与连接端口不同）", "").apply { inputType = 2 }
        val code = field("电视上显示的 6 位配对码", "").apply { inputType = 2 }
        form.addView(host); form.addView(port); form.addView(code)
        AlertDialog.Builder(this).setTitle("无线调试配对").setView(form)
            .setPositiveButton("配对") { _, _ ->
                val profile = active
                val address = host.text.toString().trim()
                val number = port.text.toString().toIntOrNull() ?: 0
                val secret = code.text.toString().trim()
                showStatus("正在配对…")
                worker.execute {
                    runCatching { adb.pair(address, number, secret) }
                        .onSuccess { fingerprint -> runOnUiThread {
                            profile.adbHost = address
                            profile.adbTls = true
                            profile.adbPeerFingerprint = fingerprint
                            store.save(devices)
                            showStatus("配对完成，请在设备设置中填写无线调试的连接端口")
                        } }
                        .onFailure { runOnUiThread { showStatus("配对失败：${it.message}") } }
                }
            }.setNegativeButton("取消", null).show()
    }

    private fun ensureBluetooth() {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 100)
        } else runCatching { bluetooth.start() }.onFailure { showStatus(it.message ?: "蓝牙启动失败") }
    }

    private fun connectSavedBluetooth() {
        val address = active.bluetoothAddress
        if (address.isBlank() || !bluetooth.isReady() || bluetooth.connectedAddress() == address) return
        runCatching {
            val device = bluetooth.bondedDevices().firstOrNull { it.address == address }
                ?: error("保存的电视已不在系统配对列表，请重新配对")
            bluetooth.connect(device)
        }.onFailure { showStatus(it.message ?: "蓝牙连接失败") }
    }

    private fun pairBluetooth() {
        if (Build.VERSION.SDK_INT >= 31) {
            val missing = arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
                .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isNotEmpty()) { requestPermissions(missing.toTypedArray(), 101); return }
        }
        runCatching {
            startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            }, 201)
        }.onFailure { showStatus("无法打开系统蓝牙配对：${it.message}") }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            100 -> if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) ensureBluetooth()
                else showStatus("需要蓝牙连接权限")
            101 -> if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) pairBluetooth()
                else showStatus("需要蓝牙连接和可发现权限才能配对")
            102 -> if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) chooseBluetooth()
                else showStatus("需要附近设备搜索权限才能查找蓝牙电视")
        }
    }

    private fun chooseBluetooth() {
        val permissions = if (Build.VERSION.SDK_INT >= 31)
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) { requestPermissions(missing.toTypedArray(), 102); return }

        bluetoothDiscovery?.close()
        val content = column().apply { setPadding(dp(18), dp(8), dp(18), dp(8)) }
        val message = text("正在准备蓝牙搜索…", 14f, ink)
        content.addView(message)
        val list = column()
        val listScroll = ScrollView(this).apply { addView(list) }
        content.addView(listScroll,
            LinearLayout.LayoutParams(-1, dp(160)).apply { topMargin = dp(12) })
        val refresh = button("重新搜索", false) { }
        content.addView(refresh, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        val dialog = AlertDialog.Builder(this).setTitle("连接蓝牙设备")
            .setView(content)
            .setNeutralButton("让电视搜索手机") { _, _ -> pairBluetooth() }
            .setNegativeButton("关闭", null).create()
        val found = linkedMapOf<String, BluetoothDevice>()
        runCatching { bluetooth.bondedDevices() }.getOrDefault(emptyList()).forEach { found[it.address] = it }

        fun connectSelected(selected: BluetoothDevice) {
            bluetoothDiscovery?.close()
            active.bluetoothAddress = selected.address
            store.save(devices)
            dialog.dismiss()
            if (bluetooth.isReady()) runCatching { bluetooth.connect(selected) }
                .onFailure { showStatus(it.message ?: "蓝牙连接失败") }
            else {
                ensureBluetooth()
                showStatus("正在启动蓝牙 HID，就绪后自动连接")
            }
        }
        fun renderList() {
            list.removeAllViews()
            val paired = found.values.filter { it.bondState == BluetoothDevice.BOND_BONDED }
            val nearby = found.values.filter { it.bondState != BluetoothDevice.BOND_BONDED }
            list.addView(text("已配对 · ${paired.size}", 15f, ink, true))
            paired.forEach { device ->
                val connected = if (bluetooth.connectedAddress() == device.address) " · 已连接" else ""
                list.addView(button("${device.name ?: "未知设备"}$connected\n${device.address}", false) {
                    connectSelected(device)
                }, LinearLayout.LayoutParams(-1, dp(66)).apply { bottomMargin = dp(7) })
            }
            list.addView(text("附近可见设备 · ${nearby.size}", 15f, ink, true),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(6) })
            nearby.forEach { device ->
                list.addView(button("${device.name ?: "未知设备"}\n${device.address} · 点击配对", false) {
                    runCatching { bluetoothDiscovery?.pair(device) }
                        .onFailure { message.text = "配对失败：${it.message}" }
                }, LinearLayout.LayoutParams(-1, dp(66)).apply { bottomMargin = dp(7) })
            }
            if (paired.isEmpty() && nearby.isEmpty())
                list.addView(text("还没有设备。请让电视进入蓝牙可见／配对模式。", 14f, muted))
            listScroll.layoutParams = (listScroll.layoutParams as LinearLayout.LayoutParams).apply {
                height = dp((160 + (paired.size + nearby.size) * 66).coerceAtMost(340))
            }
        }
        renderList()
        val scanner = BluetoothDeviceDiscovery(this,
            onFound = { device -> found[device.address] = device; renderList() },
            onState = { message.text = it },
            onBondResult = { device, success ->
                if (success) { found[device.address] = device; renderList(); connectSelected(device) }
                else message.text = "配对未完成，请在电视上确认后重试"
            })
        bluetoothDiscovery = scanner
        refresh.setOnClickListener { runCatching { scanner.start() }.onFailure { message.text = "搜索失败：${it.message}" } }
        dialog.setOnDismissListener { scanner.close(); if (bluetoothDiscovery === scanner) bluetoothDiscovery = null }
        dialog.show()
        runCatching { scanner.start() }.onFailure { message.text = "搜索失败：${it.message}" }
    }

    private fun chooseIr() {
        if (!ir.hasEmitter()) { showStatus("手机没有红外发射器"); return }
        startActivityForResult(Intent(this, IrMatchActivity::class.java).putExtra("profile_id", active.id), 200)
    }

    @Deprecated("Receives the result from the IR matching wizard")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 201) {
            if (resultCode > 0) {
                ensureBluetooth()
                AlertDialog.Builder(this).setTitle("手机已可被电视发现")
                    .setMessage("请在电视或盒子的蓝牙设置中搜索这部手机并确认配对。配对完成后，回到这里点击“连接”选择电视。")
                    .setPositiveButton("查看已配对设备") { _, _ -> chooseBluetooth() }
                    .setNegativeButton("稍后", null).show()
            } else showStatus("蓝牙未开启，或可发现请求已取消")
            return
        }
        if (requestCode != 200 || resultCode != RESULT_OK) return
        val code = data?.getStringExtra("code_id") ?: return
        val profile = devices.firstOrNull { it.id == data.getStringExtra("profile_id") } ?: return
        profile.irCodeId = code
        store.save(devices)
        if (profile.id == active.id) showStatus("红外遥控器已匹配，可用下面的按键测试：$code")
    }

    private fun showStatus(message: String) { status.text = message }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun ripple(color: Int, radius: Int) =
        RippleDrawable(ColorStateList.valueOf(0x222B2D2B), rounded(color, radius), null)
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun field(hint: String, value: String) = EditText(this).apply {
        this.hint = hint; setText(value); textSize = 16f; minHeight = dp(56)
        setSingleLine(true)
    }
    private fun button(label: String, primary: Boolean, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 15f; isAllCaps = false; minHeight = dp(48)
        setTextColor(UiPalette.panelText)
        background = ripple(UiPalette.panelInset, 15)
        setOnClickListener { action() }
    }

    override fun onDestroy() {
        generation++
        adbDiscovery?.stop()
        bluetoothDiscovery?.close()
        if (::bluetooth.isInitialized) bluetooth.cancelPendingReports()
        if (::adb.isInitialized) worker.execute { adb.disconnect() }
        worker.shutdown()
        super.onDestroy()
    }
}
