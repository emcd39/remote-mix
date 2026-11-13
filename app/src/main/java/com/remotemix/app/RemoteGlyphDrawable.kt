package com.remotemix.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.min

enum class RemoteGlyph {
    POWER, INPUT, UP, DOWN, LEFT, RIGHT, OK, BACK, HOME, MENU,
    VOLUME_UP, VOLUME_DOWN, MUTE, CHANNEL_UP, CHANNEL_DOWN, PLAY_PAUSE, APPS, TV, BOX
}

/** Draws consistent 24-unit vector glyphs without raster assets or font symbols. */
class RemoteGlyphDrawable(context: Context, private val glyph: RemoteGlyph, private val tint: Int) : Drawable() {
    private val size = (28 * context.resources.displayMetrics.density + 0.5f).toInt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tint
        strokeWidth = 2.2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun getIntrinsicWidth() = size
    override fun getIntrinsicHeight() = size
    override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val scale = min(b.width(), b.height()) / 24f
        canvas.save()
        canvas.translate(b.centerX().toFloat() - 12f * scale, b.centerY().toFloat() - 12f * scale)
        canvas.scale(scale, scale)
        paint.color = tint
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f
        when (glyph) {
            RemoteGlyph.TV -> {
                canvas.drawRoundRect(RectF(2f, 3f, 22f, 18f), 1.5f, 1.5f, paint)
                canvas.drawLine(9f, 18f, 9f, 21f, paint)
                canvas.drawLine(15f, 18f, 15f, 21f, paint)
                canvas.drawLine(6f, 21f, 18f, 21f, paint)
            }
            RemoteGlyph.BOX -> {
                canvas.drawRoundRect(RectF(2f, 7f, 22f, 18f), 3f, 3f, paint)
                canvas.drawCircle(16f, 12.5f, 0.8f, paint)
                canvas.drawCircle(19f, 12.5f, 0.8f, paint)
            }
            RemoteGlyph.POWER -> {
                canvas.drawArc(RectF(4f, 4f, 20f, 21f), -53f, 286f, false, paint)
                canvas.drawLine(12f, 2f, 12f, 11f, paint)
            }
            RemoteGlyph.UP, RemoteGlyph.DOWN, RemoteGlyph.LEFT, RemoteGlyph.RIGHT -> {
                canvas.save()
                canvas.rotate(when (glyph) {
                    RemoteGlyph.UP -> 0f; RemoteGlyph.RIGHT -> 90f
                    RemoteGlyph.DOWN -> 180f; else -> 270f
                }, 12f, 12f)
                paint.style = Paint.Style.FILL
                canvas.drawPath(path { moveTo(12f, 3f); lineTo(3f, 13f); lineTo(9f, 13f)
                    lineTo(9f, 21f); lineTo(15f, 21f); lineTo(15f, 13f); lineTo(21f, 13f); close() }, paint)
                canvas.restore()
            }
            RemoteGlyph.OK -> {
                canvas.drawCircle(12f, 12f, 9f, paint)
                canvas.drawPath(path { moveTo(7.5f, 12f); lineTo(10.5f, 15f); lineTo(16.8f, 8.6f) }, paint)
            }
            RemoteGlyph.BACK -> {
                canvas.drawPath(path { moveTo(9f, 5f); lineTo(4f, 10f); lineTo(9f, 15f)
                    moveTo(4f, 10f); lineTo(14f, 10f); cubicTo(19f, 10f, 20f, 13f, 20f, 18f) }, paint)
            }
            RemoteGlyph.HOME -> {
                canvas.drawPath(path { moveTo(3f, 11f); lineTo(12f, 3f); lineTo(21f, 11f)
                    moveTo(6f, 9f); lineTo(6f, 21f); lineTo(18f, 21f); lineTo(18f, 9f)
                    moveTo(10f, 21f); lineTo(10f, 15f); lineTo(14f, 15f); lineTo(14f, 21f) }, paint)
            }
            RemoteGlyph.MENU -> for (y in listOf(6f, 12f, 18f)) canvas.drawLine(4f, y, 20f, y, paint)
            RemoteGlyph.INPUT -> {
                canvas.drawRoundRect(RectF(2f, 4f, 22f, 20f), 2f, 2f, paint)
                canvas.drawPath(path { moveTo(7f, 12f); lineTo(16f, 12f)
                    moveTo(12f, 8f); lineTo(16f, 12f); lineTo(12f, 16f) }, paint)
            }
            RemoteGlyph.VOLUME_UP, RemoteGlyph.VOLUME_DOWN, RemoteGlyph.MUTE -> {
                canvas.drawPath(path { moveTo(3f, 9f); lineTo(7f, 9f); lineTo(12f, 5f)
                    lineTo(12f, 19f); lineTo(7f, 15f); lineTo(3f, 15f); close() }, paint)
                canvas.drawLine(16f, 12f, 21f, 12f, paint)
                if (glyph == RemoteGlyph.VOLUME_UP) canvas.drawLine(18.5f, 9.5f, 18.5f, 14.5f, paint)
                if (glyph == RemoteGlyph.MUTE) canvas.drawLine(15f, 8f, 22f, 17f, paint)
            }
            RemoteGlyph.CHANNEL_UP, RemoteGlyph.CHANNEL_DOWN -> {
                canvas.drawRoundRect(RectF(3f, 5f, 21f, 19f), 2f, 2f, paint)
                canvas.drawLine(8f, 12f, 16f, 12f, paint)
                if (glyph == RemoteGlyph.CHANNEL_UP) canvas.drawLine(12f, 8f, 12f, 16f, paint)
            }
            RemoteGlyph.PLAY_PAUSE -> {
                paint.style = Paint.Style.FILL
                canvas.drawPath(path { moveTo(3f, 5f); lineTo(3f, 19f); lineTo(13f, 12f); close() }, paint)
                canvas.drawRoundRect(RectF(16f, 5f, 18.5f, 19f), 0.7f, 0.7f, paint)
                canvas.drawRoundRect(RectF(20.5f, 5f, 23f, 19f), 0.7f, 0.7f, paint)
            }
            RemoteGlyph.APPS -> {
                paint.style = Paint.Style.FILL
                for (x in listOf(3f, 13f)) for (y in listOf(3f, 13f))
                    canvas.drawRoundRect(RectF(x, y, x + 8f, y + 8f), 1.8f, 1.8f, paint)
            }
        }
        canvas.restore()
    }

    private inline fun path(edit: Path.() -> Unit) = Path().apply(edit)
}
