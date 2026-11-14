package com.remotemix.app

import android.app.Activity
import android.content.res.ColorStateList
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

class IrMatchActivity : Activity() {
    private enum class Stage { TYPE, REGION, BRAND, TEST, SUCCESS, EXHAUSTED, FAILURE, UNAVAILABLE, LOADING }
    private val worker = Executors.newSingleThreadExecutor()
    private val ink = UiPalette.ink
    private val muted = UiPalette.muted
    private val teal = UiPalette.accent
    private val surface = UiPalette.background
    private lateinit var ir: IrRemote
    private lateinit var page: LinearLayout
    private lateinit var title: TextView
    private lateinit var step: TextView
    private var deviceId: String? = null
    private var brand: String? = null
    private var province: IrRegion? = null
    private var region: IrRegion? = null
    private var brands = emptyList<IrBrand>()
    private var match: IrMatchSession? = null
    @Volatile private var screenVersion = 0
    private var completedCode: String? = null
    private var stage = Stage.TYPE
    private var pendingRestore: IntArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ir = IrRemote(this)
        // System bars are inset from the scroll container below.
        val scroll = ScrollView(this).apply {
            setBackgroundColor(surface)
            isFillViewport = true
        }
        page = column().apply { setPadding(dp(20), dp(18), dp(20), dp(28)) }
        scroll.addView(page)
        setContentView(scroll)
        SystemBars.attach(this, scroll, surface, lightIcons = true)
        if (!ir.hasEmitter()) { showUnavailable(); return }
        if (savedInstanceState == null) { showTypes(); return }
        deviceId = savedInstanceState.getString("device_id")
        brand = savedInstanceState.getString("brand")
        completedCode = savedInstanceState.getString("completed_code")
        pendingRestore = savedInstanceState.getIntArray("match_state")
        if (savedInstanceState.containsKey("province_id")) {
            province = IrRegion(savedInstanceState.getInt("province_id"),
                savedInstanceState.getString("province_name") ?: "", 0)
        }
        if (savedInstanceState.containsKey("region_id")) {
            region = IrRegion(savedInstanceState.getInt("region_id"),
                savedInstanceState.getString("region_name") ?: "",
                savedInstanceState.getInt("region_parent"))
        }
        when (savedInstanceState.getString("stage")) {
            Stage.REGION.name -> loadRegions(province?.id ?: 0)
            Stage.BRAND.name -> loadBrands()
            Stage.TEST.name, Stage.SUCCESS.name, Stage.EXHAUSTED.name -> if (brand != null) loadCandidates() else showTypes()
            else -> showTypes()
        }
    }

    private fun showTypes() {
        screenVersion++
        stage = Stage.TYPE
        deviceId = null
        brand = null
        province = null
        region = null
        match = null
        shell("第 1 步 · 添加遥控器", "选择设备类型")
        page.addView(label("选择您要添加的遥控器属于哪类设备。", 16f, muted))
        page.addView(gap(22))
        listOf("1" to "电视", "2" to "电视机顶盒", "5" to "IPTV", "10" to "投影仪", "12" to "盒子").forEach { (id, name) ->
            page.addView(actionButton(name, false) {
                deviceId = id
                if (id == "2") loadRegions(0) else loadBrands()
            })
        }
        page.addView(gap(18))
        page.addView(label("手机需要自带红外发射器，并对准设备的红外接收位置。", 13f, muted))
    }

    private fun loadRegions(parentId: Int) {
        val version = ++screenVersion
        stage = Stage.LOADING
        shell("第 2 步 / 共 4 步", "正在读取地区…")
        worker.execute {
            runCatching { ir.regions(parentId) }
                .onSuccess { values -> runOnUiThread {
                    if (version == screenVersion) showRegions(values)
                } }
                .onFailure { error -> runOnUiThread {
                    if (version == screenVersion) showFailure("读取地区失败：${error.message}") { loadRegions(parentId) }
                } }
        }
    }

    private fun showRegions(values: List<IrRegion>) {
        screenVersion++
        stage = Stage.REGION
        val parent = province
        shell("第 2 步 / 共 4 步", if (parent == null) "选择省份" else "选择城市或区县")
        page.addView(label("选择地区后会优先尝试对应码组；本地资料不全时仍可继续尝试其他方案。", 15f, muted))
        page.addView(gap(12))
        if (parent != null) page.addView(actionButton("使用整个${parent.name}", true) {
            region = parent
            loadBrands()
        })
        values.forEach { item -> page.addView(actionButton(item.name, false) {
            if (parent == null) {
                province = item
                loadRegions(item.id)
            } else {
                region = item
                loadBrands()
            }
        }) }
        page.addView(actionButton("跳过地区，测试全部方案", false) {
            province = null
            region = null
            loadBrands()
        })
    }

    private fun loadBrands() {
        val id = requireNotNull(deviceId)
        val version = ++screenVersion
        stage = Stage.LOADING
        shell(brandStep(), "正在读取品牌…")
        worker.execute {
            runCatching { ir.brands(id) }
                .onSuccess { values -> runOnUiThread {
                    if (version == screenVersion) { brands = values; showBrands() }
                } }
                .onFailure { error -> runOnUiThread {
                    if (version == screenVersion) showFailure("读取品牌失败：${error.message}") { loadBrands() }
                } }
        }
    }

    private fun showBrands() {
        screenVersion++
        stage = Stage.BRAND
        shell(brandStep(), "选择品牌")
        page.addView(label("选好品牌后，按照设备反应逐步测试按键。", 15f, muted))
        val search = EditText(this).apply {
            hint = "搜索品牌"
            textSize = 16f
            setSingleLine(true)
            minHeight = dp(56)
        }
        page.addView(search)
        val results = column()
        page.addView(results)
        fun display(query: String) {
            results.removeAllViews()
            val filtered = brands.filter { it.name.contains(query.trim(), ignoreCase = true) || it.label.contains(query.trim(), ignoreCase = true) }
            if (filtered.isEmpty()) results.addView(label("没有找到品牌，请试试“通用／其他”。", 15f, muted))
            filtered.forEach { item ->
                results.addView(actionButton(item.label, false) {
                    brand = item.name
                    loadCandidates()
                })
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = display(s?.toString() ?: "")
            override fun afterTextChanged(s: Editable?) = Unit
        })
        display("")
    }

    private fun loadCandidates() {
        val id = requireNotNull(deviceId)
        val selectedBrand = requireNotNull(brand)
        val version = ++screenVersion
        stage = Stage.LOADING
        shell(testStep(), "正在准备测试…")
        worker.execute {
            runCatching {
                ir.matchCandidates(id, selectedBrand, region?.id,
                    region?.parentId?.takeIf { it > 0 }) to ir.brands(id)
            }
                .onSuccess { (values, loadedBrands) -> runOnUiThread {
                    if (version != screenVersion) return@runOnUiThread
                    brands = loadedBrands
                    match = IrMatchSession(values)
                    pendingRestore?.let { snapshot -> runCatching { match?.restore(snapshot) } }
                    pendingRestore = null
                    completedCode?.let(::showSuccess) ?: showTest()
                } }
                .onFailure { error -> runOnUiThread {
                    if (version == screenVersion) showFailure("读取码组失败：${error.message}") { loadCandidates() }
                } }
        }
    }

    private fun showTest() {
        val session = requireNotNull(match)
        val probe = session.current()
        if (probe == null) { showExhausted(); return }
        screenVersion++
        stage = Stage.TEST
        shell(testStep(), "测试遥控器")
        val category = when (deviceId) {
            "1" -> "电视"
            "2" -> "电视机顶盒"
            "5" -> "IPTV"
            "10" -> "投影仪"
            "12" -> "盒子"
            else -> "设备"
        }
        page.addView(label("$category · ${if (brand == "未知品牌") "通用／其他" else brand}${region?.let { " · ${it.name}" } ?: ""}", 16f, ink, true).apply { gravity = Gravity.CENTER })
        page.addView(gap(12))
        page.addView(label("第 ${probe.groupNumber} / ${probe.groupCount} 套方案", 15f, muted).apply { gravity = Gravity.CENTER })
        page.addView(label(
            if (probe.phase == IrMatchPhase.POWER) "先测试电源键"
            else "电源有响应，验证第 ${probe.variantNumber} / ${probe.variantCount} 套 · 按键 ${probe.verifyNumber} / ${probe.verifyCount}",
            14f, muted
        ).apply { gravity = Gravity.CENTER })
        page.addView(gap(18))
        val illustration = column().apply {
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(22), dp(20), dp(22))
            background = rounded(UiPalette.secondary, 20)
            addView(ImageView(this@IrMatchActivity).apply {
                setImageDrawable(RemoteGlyphDrawable(this@IrMatchActivity, RemoteGlyph.TV, ink))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(108), dp(108)))
            addView(label("⌁  对准设备红外接收窗", 14f, muted).apply { gravity = Gravity.CENTER })
        }
        page.addView(illustration, LinearLayout.LayoutParams(-1, dp(170)))
        page.addView(gap(18))
        val keyName = keyLabel(probe.keyId)
        val instructions = label(
            if (probe.sent) "已发射“$keyName”。请观察设备，再回答下面的问题。"
            else "请将手机红外口对准设备，点击下方按钮测试“$keyName”。",
            18f, ink, true
        ).apply { gravity = Gravity.CENTER; setPadding(dp(12), dp(12), dp(12), dp(12)) }
        page.addView(instructions, LinearLayout.LayoutParams(-1, -2))
        page.addView(gap(10))
        val sendButton = actionButton("发射 $keyName", true) { }
        sendButton.setOnClickListener {
            sendButton.isEnabled = false
            sendButton.text = "正在发射…"
            val version = screenVersion
            worker.execute {
                if (version != screenVersion) return@execute
                runCatching { ir.sendTest(probe.codeId, probe.keyId) }
                    .onSuccess { runOnUiThread {
                        if (version == screenVersion) { session.markSent(); showTest() }
                    } }
                    .onFailure { error -> runOnUiThread {
                        if (version == screenVersion) {
                            sendButton.isEnabled = true
                            sendButton.text = "重新发射 $keyName"
                            instructions.text = "发射失败：${error.message}"
                        }
                    } }
            }
        }
        page.addView(sendButton)
        if (probe.sent) {
            page.addView(gap(16))
            page.addView(label(
                if (probe.keyId == "power") "设备开机或关机了吗？" else "设备对“$keyName”有响应吗？",
                17f, ink, true
            ).apply { gravity = Gravity.CENTER })
            val answers = row()
            answers.addView(actionButton("没有响应", false) { answer(false) }, weighted())
            answers.addView(actionButton("有响应", true) { answer(true) }, weighted())
            page.addView(answers)
        }
        page.addView(gap(12))
        page.addView(actionButton("跳过这套", false) { session.skip(); showTest() })
        page.addView(actionButton("重新选择品牌", false) { match = null; showBrands() })
        page.addView(label("码组 ${probe.codeId}", 12f, muted))
    }

    private fun answer(responded: Boolean) {
        val selected = requireNotNull(match).answer(responded)
        if (selected == null) showTest() else showSuccess(selected)
    }

    private fun showSuccess(codeId: String) {
        screenVersion++
        stage = Stage.SUCCESS
        completedCode = codeId
        shell("匹配完成", "设备有响应")
        page.addView(label("电源和复核按键均已得到您的确认。", 18f, ink, true))
        page.addView(gap(18))
        page.addView(label("码组：$codeId", 14f, muted))
        page.addView(gap(24))
        page.addView(actionButton("保存并使用", true) {
            setResult(RESULT_OK, Intent().putExtra("code_id", codeId)
                .putExtra("profile_id", intent.getStringExtra("profile_id")))
            finish()
        })
        page.addView(actionButton("继续试其他码组", false) {
            completedCode = null
            requireNotNull(match).skip()
            showTest()
        })
    }

    private fun showExhausted() {
        screenVersion++
        stage = Stage.EXHAUSTED
        shell("匹配结束", "没有找到匹配码组")
        page.addView(label("已试完这个品牌的本地方案。请换一个品牌，或确认手机和设备之间没有遮挡。", 16f, muted))
        page.addView(gap(24))
        page.addView(actionButton("重新选择品牌", true) { match = null; showBrands() })
        page.addView(actionButton("返回遥控器", false) { finish() })
    }

    private fun showUnavailable() {
        stage = Stage.UNAVAILABLE
        shell("红外匹配", "手机没有红外发射器")
        page.addView(label("当前手机无法发射红外信号。", 16f, muted))
        page.addView(actionButton("返回", false) { finish() })
    }

    private fun showFailure(message: String, retry: () -> Unit) {
        stage = Stage.FAILURE
        shell("红外匹配", "暂时无法继续")
        page.addView(label(message, 16f, muted))
        page.addView(actionButton("重试", true, retry))
        page.addView(actionButton("返回", false) { onBackPressed() })
    }

    private fun shell(stage: String, heading: String) {
        page.removeAllViews()
        val toolbar = row()
        toolbar.addView(Button(this).apply {
            text = "‹"
            textSize = 28f
            isAllCaps = false
            setTextColor(ink)
            background = ripple(UiPalette.surface, 14)
            setOnClickListener { onBackPressed() }
            contentDescription = "返回上一步"
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        toolbar.addView(label("红外匹配", 17f, ink, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(View(this), LinearLayout.LayoutParams(dp(48), dp(48)))
        page.addView(toolbar)
        page.addView(gap(18))
        val progress = row()
        val completed = Regex("第 (\\d+) 步").find(stage)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val total = Regex("共 (\\d+) 步").find(stage)?.groupValues?.get(1)?.toIntOrNull() ?: 3
        repeat(total) { index -> progress.addView(View(this).apply {
            background = rounded(if (index < completed) UiPalette.panelAccent else UiPalette.border, 4)
        }, LinearLayout.LayoutParams(0, dp(6), 1f).apply { marginEnd = dp(6) }) }
        page.addView(progress)
        step = label(stage, 14f, muted).apply { gravity = Gravity.CENTER }
        title = label(heading, 28f, ink, true).apply { gravity = Gravity.CENTER }
        page.addView(step)
        page.addView(gap(24))
        page.addView(title)
        page.addView(gap(20))
    }

    @Deprecated("Uses the Activity back callback to restore the previous matching step")
    override fun onBackPressed() {
        when (stage) {
            Stage.SUCCESS -> {
                completedCode = null
                if (match?.back() == true) showTest() else showBrands()
            }
            Stage.TEST, Stage.EXHAUSTED -> if (match?.back() == true) showTest() else { match = null; showBrands() }
            Stage.BRAND -> {
                brand = null
                if (deviceId == "2") loadRegions(province?.id ?: 0) else showTypes()
            }
            Stage.REGION -> if (province != null) { province = null; loadRegions(0) } else showTypes()
            Stage.LOADING, Stage.FAILURE -> showTypes()
            Stage.TYPE, Stage.UNAVAILABLE -> finish()
        }
    }

    private fun brandStep() = if (deviceId == "2") "第 3 步 / 共 4 步" else "第 2 步 / 共 3 步"
    private fun testStep() = if (deviceId == "2") "第 4 步 / 共 4 步" else "第 3 步 / 共 3 步"

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("stage", stage.name)
        outState.putString("device_id", deviceId)
        outState.putString("brand", brand)
        outState.putString("completed_code", completedCode)
        match?.let { outState.putIntArray("match_state", it.snapshot()) }
        province?.let {
            outState.putInt("province_id", it.id)
            outState.putString("province_name", it.name)
        }
        region?.let {
            outState.putInt("region_id", it.id)
            outState.putString("region_name", it.name)
            outState.putInt("region_parent", it.parentId)
        }
    }

    private fun keyLabel(key: String) = when (key) {
        "power" -> "电源"; "vol+" -> "音量+"; "up" -> "方向上"
        "ok" -> "确定"; "ch+" -> "频道+"; "menu" -> "菜单"
        "back", "exit" -> "返回"
        "down" -> "方向下"; "left" -> "方向左"; "right" -> "方向右"
        "vol-" -> "音量−"; "ch-" -> "频道−"; "mute" -> "静音"
        "home" -> "主页"; "input", "tv_av" -> "信号源"; "play" -> "播放"; "pause" -> "暂停"
        else -> key
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (color == UiPalette.surface) setStroke(dp(1), UiPalette.border)
    }
    private fun ripple(color: Int, radius: Int) = RippleDrawable(
        ColorStateList.valueOf(0x222B2D2B), rounded(color, radius), null
    )
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun gap(height: Int) = View(this).apply { layoutParams = ViewGroup.LayoutParams(1, dp(height)) }
    private fun weighted() = LinearLayout.LayoutParams(0, dp(58), 1f).apply { setMargins(dp(4), dp(12), dp(4), 0) }
    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun actionButton(value: String, primary: Boolean, click: () -> Unit) = Button(this).apply {
        text = value; textSize = 16f; isAllCaps = false
        setTextColor(if (primary) UiPalette.surface else ink)
        background = ripple(if (primary) teal else UiPalette.surface, 15)
        minHeight = dp(56)
        val padding = LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(10) }
        layoutParams = padding
        setOnClickListener { click() }
    }

    override fun onDestroy() {
        screenVersion++
        worker.shutdownNow()
        super.onDestroy()
    }
}
