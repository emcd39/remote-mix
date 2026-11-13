package com.remotemix.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

@SuppressLint("ViewConstructor")
class RemotePanel(context: Context, onAction: (RemoteAction) -> Unit, onApps: () -> Unit) : LinearLayout(context) {
    private val colors = UiPalette.current
    private val appsTile: LinearLayout

    init {
        orientation = VERTICAL
        setPadding(dp(14), dp(16), dp(14), dp(18))
        background = shape(colors.panel, 24)

        val top = row()
        top.addView(tile(RemoteGlyph.POWER, "电源", colors.danger, colors.panelText) { onAction(RemoteAction.POWER) }, weighted(74))
        top.addView(tile(RemoteGlyph.INPUT, "信号源", colors.panelKey, colors.panelText) { onAction(RemoteAction.INPUT) }, weighted(74))
        appsTile = tile(RemoteGlyph.APPS, "APP", colors.panelKey, colors.panelText, onApps)
        top.addView(appsTile, weighted(74))
        addView(top)
        addView(View(context), LayoutParams(1, 0, 1f))

        val padSize = minOf(258, maxOf(220, (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt() - 92))
        val pad = FrameLayout(context).apply {
            background = PadDrawable(colors.panelInset, colors.panelKey, colors.border)
            contentDescription = "方向键"
        }
        fun direction(glyph: RemoteGlyph, action: RemoteAction, gravityValue: Int) {
            pad.addView(iconButton(glyph, action.label, colors.panelInset, colors.panelText, 62, false) { onAction(action) },
                FrameLayout.LayoutParams(dp(62), dp(62), gravityValue).apply { setMargins(dp(8), dp(8), dp(8), dp(8)) })
        }
        direction(RemoteGlyph.UP, RemoteAction.UP, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        direction(RemoteGlyph.LEFT, RemoteAction.LEFT, Gravity.START or Gravity.CENTER_VERTICAL)
        direction(RemoteGlyph.RIGHT, RemoteAction.RIGHT, Gravity.END or Gravity.CENTER_VERTICAL)
        direction(RemoteGlyph.DOWN, RemoteAction.DOWN, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        pad.addView(TextView(context).apply {
            text = "OK"; textSize = 20f; setTextColor(colors.panel)
            gravity = Gravity.CENTER; isClickable = true; isFocusable = true
            contentDescription = "确定"
            background = RippleDrawable(ColorStateList.valueOf(0x33000000), shape(colors.panelAccent, 60, true), null)
            setOnClickListener { onAction(RemoteAction.OK) }
            elevation = dp(3).toFloat()
        }, FrameLayout.LayoutParams(dp(80), dp(80), Gravity.CENTER))
        addView(pad, LayoutParams(dp(padSize), dp(padSize)).apply {
            gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(14); bottomMargin = dp(12)
        })
        addView(View(context), LayoutParams(1, 0, 1f))

        addView(row().apply {
            addView(tile(RemoteGlyph.BACK, "返回", colors.panelKey, colors.panelText) { onAction(RemoteAction.BACK) }, weighted(70))
            addView(tile(RemoteGlyph.HOME, "主页", colors.panelKey, colors.panelText) { onAction(RemoteAction.HOME) }, weighted(70))
            addView(tile(RemoteGlyph.MENU, "菜单", colors.panelKey, colors.panelText) { onAction(RemoteAction.MENU) }, weighted(70))
        })
        addView(View(context), LayoutParams(1, 0, 1f))

        val lower = row()
        lower.addView(verticalPair(RemoteAction.VOLUME_UP, RemoteGlyph.VOLUME_UP,
            RemoteAction.VOLUME_DOWN, RemoteGlyph.VOLUME_DOWN, "音量", onAction), LayoutParams(0, dp(142), 1f))
        lower.addView(LinearLayout(context).apply {
            orientation = VERTICAL; gravity = Gravity.CENTER
            addView(tile(RemoteGlyph.MUTE, "静音", colors.panelKey, colors.panelText) { onAction(RemoteAction.MUTE) }, LayoutParams(-1, dp(70)))
            addView(tile(RemoteGlyph.PLAY_PAUSE, "播放/暂停", colors.panelKey, colors.panelText) { onAction(RemoteAction.PLAY_PAUSE) }, LayoutParams(-1, dp(70)))
        }, LayoutParams(0, dp(142), 1f))
        lower.addView(verticalPair(RemoteAction.CHANNEL_UP, RemoteGlyph.CHANNEL_UP,
            RemoteAction.CHANNEL_DOWN, RemoteGlyph.CHANNEL_DOWN, "频道", onAction), LayoutParams(0, dp(142), 1f))
        addView(lower, LayoutParams(-1, -2).apply { topMargin = dp(6) })
    }

    fun setMode(mode: ControlMode) { appsTile.visibility = if (mode == ControlMode.ADB) View.VISIBLE else View.INVISIBLE }

    private fun verticalPair(up: RemoteAction, upGlyph: RemoteGlyph, down: RemoteAction, downGlyph: RemoteGlyph,
                             label: String, onAction: (RemoteAction) -> Unit) = LinearLayout(context).apply {
        orientation = VERTICAL; gravity = Gravity.CENTER
        addView(iconButton(upGlyph, "$label+", colors.panelKey, colors.panelText, 55) { onAction(up) },
            LayoutParams(dp(55), dp(55)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        addView(TextView(context).apply {
            text = label; textSize = 12f; gravity = Gravity.CENTER; setTextColor(colors.panelMuted)
        }, LayoutParams(-1, dp(25)))
        addView(iconButton(downGlyph, "$label−", colors.panelKey, colors.panelText, 55) { onAction(down) },
            LayoutParams(dp(55), dp(55)).apply { gravity = Gravity.CENTER_HORIZONTAL })
    }

    private fun tile(glyph: RemoteGlyph, label: String, color: Int, iconColor: Int, onClick: () -> Unit) = LinearLayout(context).apply {
        orientation = VERTICAL; gravity = Gravity.CENTER
        addView(iconButton(glyph, label, color, iconColor, 54, onClick = onClick))
        addView(TextView(context).apply {
            text = label; textSize = 12f; gravity = Gravity.CENTER; setTextColor(colors.panelMuted)
            maxLines = 1; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(-1, dp(20)))
    }

    private fun iconButton(glyph: RemoteGlyph, label: String, color: Int, iconColor: Int,
                           size: Int, filled: Boolean = true, onClick: () -> Unit) = ImageButton(context).apply {
        contentDescription = label
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(14), dp(14), dp(14), dp(14))
        setImageDrawable(RemoteGlyphDrawable(context, glyph, iconColor))
        background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF),
            if (filled) shape(color, 14) else shape(color, 40, true), null)
        layoutParams = LayoutParams(dp(size), dp(size))
        setOnClickListener { onClick() }
    }
    private fun weighted(height: Int) = LayoutParams(0, dp(height), 1f)
    private fun row() = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun shape(color: Int, radius: Int, circle: Boolean = false) = GradientDrawable().apply {
        setColor(color); if (circle) shape = GradientDrawable.OVAL else cornerRadius = dp(radius).toFloat()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
}

private class PadDrawable(private val base: Int, private val rim: Int, private val line: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        val b = bounds
        val x = b.exactCenterX(); val y = b.exactCenterY(); val r = minOf(b.width(), b.height()) * 0.48f
        paint.color = rim; paint.style = Paint.Style.FILL; canvas.drawCircle(x, y, r, paint)
        paint.color = base; canvas.drawCircle(x, y, r - 2f, paint)
        paint.color = line; paint.alpha = 70; paint.strokeWidth = 1.5f; paint.style = Paint.Style.STROKE
        val edge = r * 0.68f; val inner = r * 0.33f
        for (sx in listOf(-1, 1)) for (sy in listOf(-1, 1))
            canvas.drawLine(x + sx * inner, y + sy * inner, x + sx * edge, y + sy * edge, paint)
        paint.alpha = 255
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
