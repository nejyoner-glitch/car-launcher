package com.example.carlauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * GPS ile çalışan analog hız kadranı. Altındaki araç siluetini kod ile çiziyoruz
 * (fotoğraf yok): yay, ibre ve vurgu rengi hıza göre canlı değişir.
 */
class SpeedGaugeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var speedKmh = 0
        set(value) { field = value.coerceAtLeast(0); invalidate() }

    /** GPS hızından türetilen boyuna ivme (m/s²). Artı: hızlanma, eksi: yavaşlama. */
    var accel = 0f
        set(value) { field = value.coerceIn(-4f, 4f); invalidate() }

    var gaugeColor = Color.rgb(79, 195, 247)
        set(value) { field = value; invalidate() }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private val body = Path()
    private val glass = Path()
    private val oval = RectF()

    // Tesla tarzı ivme çubuğu: sağa dolarsa hızlanma, sola dolarsa yavaşlama.
    private fun drawAccel(canvas: Canvas, cx: Float, y: Float, r: Float) {
        val half = r * .55f
        val hh = 3f * density
        p.reset()
        p.isAntiAlias = true
        p.style = Paint.Style.FILL
        oval.set(cx - half, y - hh, cx + half, y + hh)
        p.color = 0x6623496D
        canvas.drawRoundRect(oval, hh, hh, p)
        val frac = (Math.abs(accel) / 3f).coerceIn(0f, 1f)
        if (frac > 0.03f) {
            if (accel > 0f) {
                oval.set(cx, y - hh, cx + half * frac, y + hh)
                p.color = gaugeColor
            } else {
                oval.set(cx - half * frac, y - hh, cx, y + hh)
                p.color = 0xFF66BB6A.toInt()
            }
            canvas.drawRoundRect(oval, hh, hh, p)
        }
        p.color = Color.WHITE
        canvas.drawRect(cx - 1f * density, y - hh * 1.5f, cx + 1f * density, y + hh * 1.5f, p)
    }

    /** Hız sayısının dikey merkezi (kadranın içinde, ibre pivotunun biraz üstü). */
    fun numberCenterY(): Float = height * .56f - min(width * .44f, height * .43f) * .34f

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
        val cy = h * .56f
        val r = min(w * .44f, h * .43f)
        val outer = RectF(cx - r, cy - r, cx + r, cy + r)

        drawGlow(canvas, cx, cy, r)
        drawCar(canvas, cx, cy + r * .50f, r * 1.12f)
        drawDial(canvas, cx, cy, r, outer, w)
        drawAccel(canvas, cx, cy + r * .30f, r)
    }

    // Kadranın arkasında hıza göre parlayan yumuşak halka.
    private fun drawGlow(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val strength = (speedKmh.coerceIn(0, 220) / 220f * 70f + 22f).toInt()
        p.reset()
        p.isAntiAlias = true
        p.shader = RadialGradient(
            cx, cy, r * 1.15f,
            intArrayOf(
                Color.argb(strength, Color.red(gaugeColor), Color.green(gaugeColor), Color.blue(gaugeColor)),
                Color.argb(0, Color.red(gaugeColor), Color.green(gaugeColor), Color.blue(gaugeColor))
            ),
            floatArrayOf(.55f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r * 1.15f, p)
        p.shader = null
    }

    // Yandan görünüm hatchback silueti. Orijinal çizimdir, belirli bir marka değildir.
    private fun drawCar(canvas: Canvas, cx: Float, top: Float, carW: Float) {
        val left = cx - carW / 2f
        val carH = carW * .4f
        fun fx(x: Float) = left + x * carW
        fun fy(y: Float) = top + carH - y * carW

        // zemin gölgesi
        p.reset()
        p.isAntiAlias = true
        p.color = Color.argb(110, 0, 0, 0)
        oval.set(fx(.0f), fy(.025f), fx(1f), fy(-.02f))
        canvas.drawOval(oval, p)

        body.reset()
        body.moveTo(fx(.03f), fy(.07f))
        body.lineTo(fx(.02f), fy(.19f))
        body.quadTo(fx(.03f), fy(.24f), fx(.09f), fy(.255f))
        body.lineTo(fx(.19f), fy(.36f))
        body.quadTo(fx(.21f), fy(.385f), fx(.27f), fy(.385f))
        body.lineTo(fx(.55f), fy(.385f))
        body.quadTo(fx(.62f), fy(.38f), fx(.68f), fy(.30f))
        body.lineTo(fx(.77f), fy(.235f))
        body.lineTo(fx(.93f), fy(.205f))
        body.quadTo(fx(.985f), fy(.19f), fx(.985f), fy(.13f))
        body.lineTo(fx(.975f), fy(.07f))
        body.close()

        p.style = Paint.Style.FILL
        p.shader = LinearGradient(
            0f, fy(.39f), 0f, fy(.05f),
            intArrayOf(0xFF3A5C80.toInt(), 0xFF16304D.toInt()), null, Shader.TileMode.CLAMP
        )
        canvas.drawPath(body, p)
        p.shader = null

        // camlar
        glass.reset()
        glass.moveTo(fx(.20f), fy(.265f))
        glass.lineTo(fx(.285f), fy(.355f))
        glass.lineTo(fx(.54f), fy(.355f))
        glass.lineTo(fx(.625f), fy(.272f))
        glass.close()
        p.color = Color.argb(185, 8, 20, 36)
        canvas.drawPath(glass, p)
        p.color = Color.argb(150, 58, 92, 128)
        p.strokeWidth = carW * .012f
        p.style = Paint.Style.STROKE
        canvas.drawLine(fx(.41f), fy(.355f), fx(.41f), fy(.268f), p)

        // gövde çizgisi
        p.color = Color.argb(200, Color.red(gaugeColor), Color.green(gaugeColor), Color.blue(gaugeColor))
        p.strokeWidth = carW * .014f
        p.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(body, p)

        // yan çizgi (kapı hattı)
        p.color = Color.argb(90, 255, 255, 255)
        p.strokeWidth = carW * .008f
        canvas.drawLine(fx(.12f), fy(.18f), fx(.90f), fy(.17f), p)

        // farlar
        p.style = Paint.Style.FILL
        p.color = 0xFFFFF3C4.toInt()
        canvas.drawRoundRect(fx(.945f), fy(.19f), fx(.985f), fy(.15f), carW * .01f, carW * .01f, p)
        p.color = 0xFFEF5350.toInt()
        canvas.drawRoundRect(fx(.02f), fy(.2f), fx(.045f), fy(.15f), carW * .01f, carW * .01f, p)

        // tekerlekler
        for (wx in floatArrayOf(.22f, .78f)) {
            val wr = carW * .078f
            p.style = Paint.Style.FILL
            p.color = 0xFF070F1A.toInt()
            canvas.drawCircle(fx(wx), fy(.065f), wr * 1.12f, p)
            p.color = 0xFF1E2F44.toInt()
            canvas.drawCircle(fx(wx), fy(.065f), wr, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = carW * .01f
            p.color = Color.argb(220, Color.red(gaugeColor), Color.green(gaugeColor), Color.blue(gaugeColor))
            canvas.drawCircle(fx(wx), fy(.065f), wr * .55f, p)
        }
    }

    private fun drawDial(canvas: Canvas, cx: Float, cy: Float, r: Float, outer: RectF, w: Float) {
        // 0-220 km/h iz ve canlı yay
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
            p.style = Paint.Style.STROKE
            p.color = if (i * 5 <= speedKmh) gaugeColor else 0xFFB5CEE8.toInt()
            p.strokeWidth = (if (major) 2.6f else 1.25f) * density
            canvas.drawLine(x1, y1, x2, y2, p)

            if (major) {
                val (tx, ty) = point(cx, cy, r - 34f * density, degree)
                p.style = Paint.Style.FILL
                p.color = Color.WHITE
                p.textAlign = Paint.Align.CENTER
                p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                p.textSize = min(16f * density, w / 28f)
                canvas.drawText((i * 5).toString(), tx, ty - (p.ascent() + p.descent()) / 2f, p)
            }
        }

        // İbre yalnızca GPS hızıyla hareket eder.
        val needle = 150f + speedKmh.coerceIn(0, 220) / 220f * 240f
        val (nx, ny) = point(cx, cy, r - 17f * density, needle)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 3.5f * density
        p.color = gaugeColor
        canvas.drawLine(cx, cy, nx, ny, p)
        p.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, 5.5f * density, p)
        p.color = Color.WHITE
        canvas.drawCircle(cx, cy, 2.3f * density, p)
    }
}
