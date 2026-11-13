package com.remotemix.app

import android.app.Application
import android.content.Context
import android.graphics.Color

internal data class Palette(
    val id: String, val name: String, val description: String,
    val background: Int, val surface: Int, val ink: Int, val muted: Int,
    val accent: Int, val accentSoft: Int, val border: Int, val secondary: Int,
    val danger: Int, val panel: Int, val panelInset: Int, val panelKey: Int,
    val panelText: Int, val panelMuted: Int, val panelAccent: Int
)

/** The saved theme is loaded before any Activity creates its views. */
internal object UiPalette {
    private fun c(hex: String) = Color.parseColor(hex)
    val themes = listOf(
        Palette("brass", "暖金石墨", "暖白页面 · 深色遥控器 · 黄铜点缀",
            c("#F7F3EC"), c("#FFFCF7"), c("#1F201E"), c("#535550"),
            c("#30332F"), c("#F0E3C9"), c("#D8D1C6"), c("#EAE5DC"),
            c("#C44F42"), c("#202321"), c("#303431"), c("#3B403C"),
            c("#F8F6F0"), c("#E2E2DA"), c("#D8B56C")),
        Palette("forest", "松林绿", "象牙白 · 深松绿 · 柔和铜金",
            c("#F2F4EE"), c("#FCFDF8"), c("#20342A"), c("#52665A"),
            c("#284C3B"), c("#DCE9DD"), c("#C6D4C8"), c("#E4ECE4"),
            c("#B84A3E"), c("#183329"), c("#28483A"), c("#345342"),
            c("#F2F8F1"), c("#D5E4D7"), c("#D9BC78")),
        Palette("slate", "雾蓝灰", "冷白页面 · 蓝灰面板 · 浅银点缀",
            c("#F1F4F6"), c("#FFFFFF"), c("#202D38"), c("#586875"),
            c("#344B5D"), c("#DFEAF0"), c("#CDD8DE"), c("#E5ECEF"),
            c("#BB534A"), c("#202D37"), c("#30414E"), c("#3A4D5B"),
            c("#F6FAFC"), c("#DCE5EA"), c("#AFC8D9")),
        Palette("mono", "黑白经典", "纯白页面 · 炭黑面板 · 灰白按键",
            c("#F5F5F3"), c("#FFFFFF"), c("#1A1A19"), c("#595957"),
            c("#292927"), c("#E7E7E3"), c("#D2D2CE"), c("#E9E9E6"),
            c("#B44940"), c("#20201F"), c("#30302E"), c("#40403D"),
            c("#FFFFFF"), c("#DFDFDA"), c("#D8D8D0"))
    )
    private var selected = themes.first()
    val current get() = selected
    fun load(context: Context) {
        val id = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getString("palette", "brass")
        selected = themes.firstOrNull { it.id == id } ?: themes.first()
    }
    fun select(context: Context, id: String) {
        selected = themes.firstOrNull { it.id == id } ?: themes.first()
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putString("palette", selected.id).apply()
    }
    val background get() = selected.background
    val surface get() = selected.surface
    val ink get() = selected.ink
    val muted get() = selected.muted
    val accent get() = selected.accent
    val accentSoft get() = selected.accentSoft
    val border get() = selected.border
    val secondary get() = selected.secondary
    val danger get() = selected.danger
    val panel get() = selected.panel
    val panelInset get() = selected.panelInset
    val panelKey get() = selected.panelKey
    val panelText get() = selected.panelText
    val panelMuted get() = selected.panelMuted
    val panelAccent get() = selected.panelAccent
}

class RemoteMixApp : Application() {
    val bluetooth by lazy { BluetoothRemote(this) }
    override fun onCreate() { super.onCreate(); UiPalette.load(this) }
}
