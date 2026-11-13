package com.remotemix.app

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Local appearance setting with a small preview of both the page and remote. */
class AppearanceActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var scroll: ScrollView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(24))
        }
        scroll = ScrollView(this).apply { isFillViewport = true; addView(root) }
        setContentView(scroll)
        SystemBars.attach(this, scroll, UiPalette.current.background, lightIcons = true)
        render()
    }

    private fun render() {
        val colors = UiPalette.current
        window.statusBarColor = colors.background
        window.navigationBarColor = colors.background
        scroll.setBackgroundColor(colors.background)
        root.setBackgroundColor(colors.background)
        root.removeAllViews()
        root.addView(text("‹  设置", 18f, colors.ink, true).apply {
            minHeight = dp(52); gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { finish() }
            contentDescription = "返回"
        })
        root.addView(text("外观与配色", 29f, colors.ink, true), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(18); bottomMargin = dp(6)
        })
        root.addView(text("选择后立即生效，下次打开会保留。", 15f, colors.muted), LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(22)
        })
        UiPalette.themes.forEach { theme ->
            val selected = theme.id == colors.id
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = RippleDrawable(ColorStateList.valueOf(0x22000000), shape(theme.surface, 16, if (selected) theme.accent else theme.border), null)
                isClickable = true; isFocusable = true
                contentDescription = "${theme.name}，${if (selected) "已选择" else "点击选择"}"
                setOnClickListener { UiPalette.select(this@AppearanceActivity, theme.id); render() }
            }
            val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            heading.addView(text(theme.name, 19f, theme.ink, true), LinearLayout.LayoutParams(0, -2, 1f))
            heading.addView(text(if (selected) "✓ 已选择" else "选择", 14f, theme.accent, true))
            card.addView(heading)
            card.addView(text(theme.description, 13f, theme.muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
            val swatches = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            listOf(theme.background, theme.surface, theme.panel, theme.panelKey, theme.panelAccent).forEach { color ->
                swatches.addView(View(this).apply { background = shape(color, 8, theme.border) },
                    LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(5) })
            }
            card.addView(swatches, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(13) })
            root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        }
    }
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun shape(color: Int, radius: Int, stroke: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), stroke)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
}
