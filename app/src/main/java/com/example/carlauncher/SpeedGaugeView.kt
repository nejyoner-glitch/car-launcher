package com.example.carlauncher

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * GPS-driven, analogue-style speed dial. The Clio IV photo is decoration only:
 * speed, needle and warning tint are fed by the existing GPS listener.
 * No fake transmission/vehicle sensor information is shown.
 */
class SpeedGaugeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    var speedKmh = 0
        set(value) { field = value.coerceAtLeast(0); invalidate() }

    var gaugeColor = Color.rgb(79, 195, 247)
        set(value) { field = value; invalidate() }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val car by lazy { BitmapFactory.decodeResource(resources, R.drawable.clio_speed) }
    private val density = resources.displayMetrics.density

    private fun point(x: Float, y: Float, radius: Float, degrees: Float): Pair<Float, Float> {
        val a = Math.toRadians(degrees.toDouble())
        return (x + radius * cos(a).toFloat()) to (y + radius * sin(a).toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w * .5f
        val cy = h * .665f
        val r = min(w * .455f, h * .48f)
        val outer = RectF(cx - r, cy - r, cx + r, cy + r)
        val carBitmap = car

        // The car stays behind the scale and real-time numbers, slightly darkened
        // so white text always stays legible on the dashboard.
        if (carBitmap != null) {
            val dest = RectF(w * .115f, h * .455f, w * .885f, h * .825f)
            p.reset()
            p.isAntiAlias = true
            p.isFilterBitmap = true
            p.alpha = 174
            canvas.drawBitmap(carBitmap, null, dest, p)
            p.alpha = 255
            p.shader = LinearGradient(0f, h * .58f, 0f, h * .88f,
                intArrayOf(0x00091A34, 0x24091A34, 0xEE091A34.toInt()),
                null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, h * .58f, w, h * .91f, p)
            p.shader = null
        }

        // 0–220 km/h scale, with a dark track and live illuminated arc.
        p.reset()
        p.isAntiAlias = true
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 7f * density
        p.color = 0xFF23496D.toInt()
        canvas.drawArc(outer, 150f, 240f, false, p)
        p.color = gaugeColor
        p.strokeWidth = 5f * density
        canvas.drawArc(outer, 150f, speedKmh.coerceIn(0, 220) / 220f * 240f, false, p)

        for (i in 0..44) {
            val major = i % 4 == 0
            val degree = 150f + i / 44f * 240f
            val (x1, y1) = point(cx, cy, r - (3f * density), degree)
            val (x2, y2) = point(cx, cy, r - (if (major) 17f else 10f) * density, degree)
            p.color = if (i * 5 <= speedKmh) gaugeColor else 0xFFB5CEE8.toInt()
            p.strokeWidth = (if (major) 2.6f else 1.25f) * density
            canvas.drawLine(x1, y1, x2, y2, p)

            if (major) {
                val (tx, ty) = point(cx, cy, r - 34f * density, degree)
                p.style = Paint.Style.FILL
                p.color = Color.WHITE
                p.textAlign = Paint.Align.CENTER
                p.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                p.textSize = min(16f * density, w / 28f)
                canvas.drawText((i * 5).toString(), tx, ty - (p.ascent() + p.descent()) / 2f, p)
                p.style = Paint.Style.STROKE
            }
        }

        // The needle moves with GPS speed, never with a simulated value.
        val needle = 150f + speedKmh.coerceIn(0, 220) / 220f * 240f
        val (nx, ny) = point(cx, cy, r - 17f * density, needle)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3.5f * density
        p.color = gaugeColor
        canvas.drawLine(cx, cy, nx, ny, p)
        p.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, 5.5f * density, p)
        p.color = Color.WHITE
        canvas.drawCircle(cx, cy, 2.3f * density, p)
    }
}
