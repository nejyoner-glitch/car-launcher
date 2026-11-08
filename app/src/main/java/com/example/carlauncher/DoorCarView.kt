package com.example.carlauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/** Yukarıdan görünüm araç: açık kapılar kırmızı ve dışarı açılmış çizilir (Tesla tarzı). */
class DoorCarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var fl = false
    private var fr = false
    private var rl = false
    private var rr = false
    private var hood = false
    private var trunk = false

    fun setState(fl: Boolean, fr: Boolean, rl: Boolean, rr: Boolean, hood: Boolean, trunk: Boolean) {
        this.fl = fl; this.fr = fr; this.rl = rl; this.rr = rr
        this.hood = hood; this.trunk = trunk
        invalidate()
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()
    private val red = 0xFFEF5350.toInt()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val carH = min(h * .72f, w * 1.5f)
        val carW = carH * .46f
        val cx = w / 2f
        val top = (h - carH) / 2f
        val left = cx - carW / 2f
        val right = cx + carW / 2f
        val bottom = top + carH
        fun y(f: Float) = top + f * carH

        // gövde
        rect.set(left, top, right, bottom)
        p.style = Paint.Style.FILL
        p.color = 0xFF2A4562.toInt()
        canvas.drawRoundRect(rect, carW * .30f, carW * .30f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = carW * .035f
        p.color = 0xFF6F93B6.toInt()
        canvas.drawRoundRect(rect, carW * .30f, carW * .30f, p)

        // ön ve arka cam, tavan
        p.style = Paint.Style.FILL
        p.color = 0xFF0C1828.toInt()
        path.reset()
        path.moveTo(left + carW * .14f, y(.30f)); path.lineTo(right - carW * .14f, y(.30f))
        path.lineTo(right - carW * .08f, y(.22f)); path.lineTo(left + carW * .08f, y(.22f)); path.close()
        canvas.drawPath(path, p)
        path.reset()
        path.moveTo(left + carW * .14f, y(.76f)); path.lineTo(right - carW * .14f, y(.76f))
        path.lineTo(right - carW * .10f, y(.83f)); path.lineTo(left + carW * .10f, y(.83f)); path.close()
        canvas.drawPath(path, p)
        p.color = 0xFF37597D.toInt()
        rect.set(left + carW * .12f, y(.32f), right - carW * .12f, y(.74f))
        canvas.drawRoundRect(rect, carW * .1f, carW * .1f, p)

        // kaput ve bagaj
        drawPanel(canvas, left + carW * .1f, y(.03f), right - carW * .1f, y(.20f), hood, false)
        drawPanel(canvas, left + carW * .1f, y(.85f), right - carW * .1f, y(.97f), trunk, true)

        // kapılar: menteşe ön kenarda
        val dt = carW * .1f
        val frontLen = carH * .21f
        val rearLen = carH * .20f
        drawDoor(canvas, left, y(.30f), frontLen, dt, fl, true)
        drawDoor(canvas, right, y(.30f), frontLen, dt, fr, false)
        drawDoor(canvas, left, y(.52f), rearLen, dt, rl, true)
        drawDoor(canvas, right, y(.52f), rearLen, dt, rr, false)
    }

    private fun drawDoor(c: Canvas, hx: Float, hy: Float, len: Float, t: Float, open: Boolean, leftSide: Boolean) {
        c.save()
        c.translate(hx, hy)
        if (open) c.rotate(if (leftSide) 55f else -55f)
        val x0 = if (leftSide) -t / 2f else -t / 2f
        rect.set(x0, 0f, x0 + t, len)
        p.style = Paint.Style.FILL
        p.color = if (open) red else 0xFF3F6188.toInt()
        c.drawRoundRect(rect, t * .35f, t * .35f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = t * .22f
        p.color = if (open) 0xFFFFCDD2.toInt() else 0xFF86A9CB.toInt()
        c.drawRoundRect(rect, t * .35f, t * .35f, p)
        c.restore()
    }

    private fun drawPanel(c: Canvas, l: Float, t: Float, r: Float, b: Float, open: Boolean, rear: Boolean) {
        val shift = if (!open) 0f else (b - t) * .55f * (if (rear) 1f else -1f)
        rect.set(l, t + shift, r, b + shift)
        p.style = Paint.Style.FILL
        p.color = if (open) red else 0xFF3F6188.toInt()
        c.drawRoundRect(rect, (r - l) * .12f, (r - l) * .12f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = (r - l) * .05f
        p.color = if (open) 0xFFFFCDD2.toInt() else 0xFF86A9CB.toInt()
        c.drawRoundRect(rect, (r - l) * .12f, (r - l) * .12f, p)
    }
}
