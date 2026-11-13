package com.remotemix.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import java.util.UUID

/** The launcher is a device picker; selecting one opens its remote. */
class DeviceListActivity : Activity() {
    private val background = UiPalette.background
    private val ink = UiPalette.ink
    private val muted = UiPalette.muted
    private val teal = UiPalette.accent
    private lateinit var store: DeviceStore
    private lateinit var content: LinearLayout
    private var devices = mutableListOf<DeviceProfile>()
    private val initialPalette = UiPalette.current.id

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = DeviceStore(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(this@DeviceListActivity.background)
            addView(content)
        }
        val frame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(this@DeviceListActivity.background)
            addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(button("＋  添加设备", true) { edit(null) }, LinearLayout.LayoutParams(-1, dp(54)).apply {
                setMargins(dp(16), dp(4), dp(16), dp(16))
            })
        }
        setContentView(frame)
        SystemBars.attach(this, frame, background, lightIcons = true)
    }

    override fun onResume() {
        super.onResume()
        if (initialPalette != UiPalette.current.id) { recreate(); return }
        devices = store.load()
        render()
    }

    private fun render() {
        content.removeAllViews()
        val brand = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        brand.addView(label("◉  Remote Mix", 18f, ink, true), LinearLayout.LayoutParams(0, dp(48), 1f).apply { gravity = Gravity.CENTER_VERTICAL })
        brand.addView(label("⋮", 26f, ink).apply {
            gravity = Gravity.CENTER
            contentDescription = "打开外观设置"
            setOnClickListener { startActivity(Intent(this@DeviceListActivity, AppearanceActivity::class.java)) }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        content.addView(brand)
        content.addView(label("我的设备", 32f, ink, true), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(24)
        })
        content.addView(label("已保存设备 · ${devices.size}", 14f, muted, true), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(10)
            bottomMargin = dp(20)
        })
        if (devices.isEmpty()) {
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(40), dp(24), dp(40))
                background = rounded(UiPalette.surface, 20)
                addView(label("还没有设备", 20f, ink, true).apply { gravity = Gravity.CENTER })
                addView(label("添加电视或盒子后，在这里选择并打开遥控器。", 15f, muted).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(9), 0, 0)
                })
            }
            content.addView(empty, LinearLayout.LayoutParams(-1, -2))
        } else {
            devices.forEachIndexed { index, profile -> content.addView(deviceCard(profile, index == 0), LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(10)
            }) }
        }
    }

    private fun deviceCard(profile: DeviceProfile, featured: Boolean): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(86)
            setPadding(dp(12), dp(12), dp(8), dp(12))
            background = ripple(if (featured) UiPalette.accentSoft else UiPalette.surface, 16)
            isClickable = true
            isFocusable = true
            contentDescription = "${profile.name}，进入遥控器"
            setOnClickListener { open(profile) }
        }
        card.addView(ImageView(this).apply {
            setImageDrawable(RemoteGlyphDrawable(this@DeviceListActivity,
                if (profile.name.contains("盒") || profile.name.contains("box", true)) RemoteGlyph.BOX else RemoteGlyph.TV, ink))
            setPadding(dp(5), dp(5), dp(5), dp(5))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(54), dp(54)))
        val titleBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBlock.addView(label(profile.name, 18f, ink, true).apply { maxLines = 1 })
        val methods = summary(profile).split("   ").filter { it.isNotBlank() }
        if (methods.size == 1 && methods.first() == "尚未设置连接方式") {
            titleBlock.addView(label(methods.first(), 13f, muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        } else {
            val badges = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            methods.forEach { method -> badges.addView(label(method, 12f, ink, true).apply {
                gravity = Gravity.CENTER
                minHeight = dp(24)
                setPadding(dp(7), 0, dp(7), 0)
                background = rounded(if (method == "ADB") UiPalette.panelAccent else UiPalette.surface, 7)
            }, LinearLayout.LayoutParams(-2, dp(24)).apply { marginEnd = dp(5) }) }
            titleBlock.addView(badges, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        }
        card.addView(titleBlock, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        card.addView(label("⋯", 24f, ink).apply {
            gravity = Gravity.CENTER
            contentDescription = "${profile.name}的更多操作"
            setOnClickListener { anchor ->
                PopupMenu(this@DeviceListActivity, anchor).apply {
                    menu.add("编辑设备").setOnMenuItemClickListener { edit(profile); true }
                    menu.add("删除设备").setOnMenuItemClickListener { confirmDelete(profile); true }
                    show()
                }
            }
        }, LinearLayout.LayoutParams(dp(44), dp(48)))
        card.addView(label("›", 30f, ink).apply {
            gravity = Gravity.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(22), dp(48)))
        return card
    }

    private fun summary(profile: DeviceProfile): String {
        val ready = buildList {
            if (profile.adbHost.isNotBlank()) add("ADB")
            if (profile.bluetoothAddress.isNotBlank()) add("蓝牙")
            if (profile.irCodeId.isNotBlank()) add("红外")
        }
        return if (ready.isEmpty()) "尚未设置连接方式" else ready.joinToString("   ")
    }

    private fun open(profile: DeviceProfile) {
        store.setActiveId(profile.id)
        startActivity(Intent(this, MainActivity::class.java).putExtra("profile_id", profile.id))
    }

    private fun edit(profile: DeviceProfile?) {
        val draft = profile?.copy() ?: DeviceProfile(UUID.randomUUID().toString(), "")
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(8))
        }
        val name = field(form, "设备名称", draft.name)
        val host = field(form, "ADB 地址（可稍后设置）", draft.adbHost)
        val port = field(form, "ADB 连接端口", draft.adbPort.toString()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val tls = CheckBox(this).apply {
            text = "新版无线调试（TLS）"
            isChecked = draft.adbTls
            minHeight = dp(48)
        }
        form.addView(tls)
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (profile == null) "添加设备" else "编辑设备")
            .setView(ScrollView(this).apply { addView(form) })
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newName = name.text.toString().trim()
                val newPort = port.text.toString().toIntOrNull()
                if (newName.isEmpty()) { name.error = "请输入设备名称"; name.requestFocus(); return@setOnClickListener }
                if (newPort == null || newPort !in 1..65535) {
                    port.error = "端口必须是 1–65535"; port.requestFocus(); return@setOnClickListener
                }
                draft.name = newName
                draft.adbHost = host.text.toString().trim()
                draft.adbPort = newPort
                draft.adbTls = tls.isChecked
                if (profile == null) devices.add(draft)
                else devices[devices.indexOfFirst { it.id == profile.id }] = draft
                store.save(devices)
                dialog.dismiss()
                render()
            }
        }
        dialog.show()
    }

    private fun confirmDelete(profile: DeviceProfile) {
        AlertDialog.Builder(this)
            .setTitle("删除设备？")
            .setMessage("将删除“${profile.name}”及它保存的连接设置和红外码组。")
            .setPositiveButton("删除") { _, _ ->
                devices.removeAll { it.id == profile.id }
                store.save(devices)
                if (store.activeId() == profile.id) store.setActiveId(devices.firstOrNull()?.id ?: "")
                render()
            }
            .setNegativeButton("取消", null)
            .show()
            .getButton(AlertDialog.BUTTON_POSITIVE)
            .setTextColor(UiPalette.danger)
    }

    private fun field(parent: LinearLayout, title: String, value: String): EditText {
        parent.addView(label(title, 14f, muted, true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        return EditText(this).apply {
            hint = title
            setText(value)
            textSize = 16f
            minHeight = dp(52)
            setSingleLine(true)
            parent.addView(this)
        }
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun button(value: String, primary: Boolean, action: () -> Unit) = Button(this).apply {
        text = value
        textSize = 15f
        isAllCaps = false
        minHeight = dp(48)
        setTextColor(if (primary) UiPalette.surface else ink)
        background = ripple(if (primary) teal else UiPalette.secondary, 14)
        setOnClickListener { action() }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (color == UiPalette.surface) setStroke(dp(1), UiPalette.border)
    }

    private fun ripple(color: Int, radius: Int) = RippleDrawable(
        ColorStateList.valueOf(0x222B2D2B), rounded(color, radius), null
    )

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
}
