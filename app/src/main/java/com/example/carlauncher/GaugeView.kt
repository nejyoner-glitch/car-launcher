package com.example.carlauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Dairesel hız göstergesi: yay doluluğu hızı, çentik hız limitini gösterir. */
class GaugeView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var speed = 0
    private var limit = 0
    private var over = false
    private var accent = 0xFF4FC3F7.toInt()
    private val maxSpeed = 200

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0xFF2A3A52.toInt()
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0xFFFFFFFF.toInt()
    }
    private val number = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        color = 0xFFFFFFFF.toInt()
    }
    private val unit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFF9FB3CE.toInt()
    }
    private val rect = RectF()

    fun setValues(speed: Int, limit: Int, over: Boolean, accent: Int) {
        this.speed = speed
        this.limit = limit
        this.over = over
        this.accent = accent
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val stroke = min(w, h) * 0.07f
        val r = min(w, h) / 2f - stroke
        val cx = w / 2f
        val cy = h / 2f

        track.strokeWidth = stroke
        arc.strokeWidth = stroke
        tick.strokeWidth = stroke * 0.35f
        rect.set(cx - r, cy - r, cx + r, cy + r)

        canvas.drawArc(rect, 150f, 240f, false, track)

        if (speed > 0) {
            arc.color = if (over) 0xFFEF5350.toInt() else accent
            val sweep = 240f * min(speed, maxSpeed) / maxSpeed
            canvas.drawArc(rect, 150f, sweep, false, arc)
        }

        if (limit in 1..maxSpeed) {
            val angle = Math.toRadians((150f + 240f * limit / maxSpeed).toDouble())
            val inner = r - stroke * 0.9f
            val outer = r + stroke * 0.9f
            canvas.drawLine(
                cx + (inner * cos(angle)).toFloat(), cy + (inner * sin(angle)).toFloat(),
                cx + (outer * cos(angle)).toFloat(), cy + (outer * sin(angle)).toFloat(),
                tick
            )
        }

        number.textSize = r * 0.85f
        number.color = if (over) 0xFFEF5350.toInt() else 0xFFFFFFFF.toInt()
        canvas.drawText(speed.toString(), cx, cy + r * 0.25f, number)

        unit.textSize = r * 0.2f
        canvas.drawText("km/h", cx, cy + r * 0.72f, unit)
    }
}
