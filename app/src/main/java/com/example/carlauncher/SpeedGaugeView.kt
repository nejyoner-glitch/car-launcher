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

    private fun buildBody(fx: (Float) -> Float, fy: (Float) -> Float) {
        body.reset()
        body.moveTo(fx(.035f), fy(.07f))
        body.lineTo(fx(.015f), fy(.16f))
        body.quadTo(fx(.012f), fy(.215f), fx(.05f), fy(.235f))
        body.lineTo(fx(.085f), fy(.248f))
        body.cubicTo(fx(.13f), fy(.27f), fx(.16f), fy(.34f), fx(.215f), fy(.372f))
        body.quadTo(fx(.25f), fy(.388f), fx(.31f), fy(.388f))
        body.lineTo(fx(.52f), fy(.388f))
        body.cubicTo(fx(.58f), fy(.388f), fx(.62f), fy(.35f), fx(.665f), fy(.31f))
        body.lineTo(fx(.775f), fy(.236f))
        body.lineTo(fx(.80f), fy(.228f))
        body.lineTo(fx(.945f), fy(.21f))
        body.quadTo(fx(.99f), fy(.195f), fx(.99f), fy(.13f))
        body.lineTo(fx(.978f), fy(.085f))
        body.lineTo(fx(.965f), fy(.07f))
        body.close()
    }

    // Yandan görünüm 5 kapılı hatchback. Orijinal çizimdir, belirli bir marka veya model değildir.
    private fun drawCar(canvas: Canvas, cx: Float, top: Float, carW: Float) {
        val left = cx - carW / 2f
        val carH = carW * .4f
        fun fx(x: Float) = left + x * carW
        fun fy(y: Float) = top + carH - y * carW
        val ar = Color.red(gaugeColor)
        val ag = Color.green(gaugeColor)
        val ab = Color.blue(gaugeColor)

        // zemin gölgesi
        p.reset()
        p.isAntiAlias = true
        p.color = Color.argb(120, 0, 0, 0)
        oval.set(fx(-.01f), fy(.03f), fx(1.01f), fy(-.025f))
        canvas.drawOval(oval, p)

        buildBody({ fx(it) }, { fy(it) })
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(
            0f, fy(.39f), 0f, fy(.06f),
            intArrayOf(0xFF4A6F96.toInt(), 0xFF2A4868.toInt(), 0xFF142B45.toInt()),
            floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawPath(body, p)
        p.shader = null

        // alt eşik (siyah plastik)
        p.color = 0xFF0B1726.toInt()
        canvas.drawRect(fx(.07f), fy(.098f), fx(.93f), fy(.07f), p)

        // camlar: ön ve arka kapı camı
        p.color = Color.argb(215, 8, 20, 36)
        glass.reset()
        glass.moveTo(fx(.425f), fy(.272f)); glass.lineTo(fx(.425f), fy(.358f))
        glass.lineTo(fx(.525f), fy(.358f))
        glass.cubicTo(fx(.565f), fy(.358f), fx(.595f), fy(.33f), fx(.63f), fy(.272f))
        glass.close()
        canvas.drawPath(glass, p)
        glass.reset()
        glass.moveTo(fx(.205f), fy(.268f))
        glass.cubicTo(fx(.225f), fy(.31f), fx(.25f), fy(.345f), fx(.285f), fy(.358f))
        glass.lineTo(fx(.397f), fy(.358f)); glass.lineTo(fx(.397f), fy(.268f))
        glass.close()
        canvas.drawPath(glass, p)

        // cam parlaması
        p.color = Color.argb(70, 255, 255, 255)
        glass.reset()
        glass.moveTo(fx(.44f), fy(.352f)); glass.lineTo(fx(.50f), fy(.352f)); glass.lineTo(fx(.455f), fy(.285f)); glass.lineTo(fx(.44f), fy(.285f))
        glass.close()
        canvas.drawPath(glass, p)

        // tavan parlaması
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = carW * .01f
        p.color = Color.argb(80, 255, 255, 255)
        canvas.drawLine(fx(.30f), fy(.381f), fx(.52f), fy(.381f), p)

        // kapı hatları ve kollar
        p.strokeWidth = carW * .006f
        p.color = Color.argb(110, 6, 14, 24)
        canvas.drawLine(fx(.411f), fy(.268f), fx(.411f), fy(.10f), p)
        canvas.drawLine(fx(.19f), fy(.262f), fx(.19f), fy(.10f), p)
        canvas.drawLine(fx(.635f), fy(.268f), fx(.62f), fy(.10f), p)
        p.strokeWidth = carW * .011f
        p.color = Color.argb(190, 200, 220, 240)
        canvas.drawLine(fx(.345f), fy(.222f), fx(.385f), fy(.222f), p)
        canvas.drawLine(fx(.535f), fy(.222f), fx(.575f), fy(.222f), p)

        // yan ayna
        p.style = Paint.Style.FILL
        p.color = 0xFF28435F.toInt()
        oval.set(fx(.625f), fy(.292f), fx(.668f), fy(.254f))
        canvas.drawOval(oval, p)

        // omuz çizgisi
        p.style = Paint.Style.STROKE
        p.strokeWidth = carW * .007f
        p.color = Color.argb(95, 255, 255, 255)
        canvas.drawLine(fx(.10f), fy(.172f), fx(.95f), fy(.178f), p)

        // gövde dış çizgisi (vurgu rengi)
        buildBody({ fx(it) }, { fy(it) })
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = carW * .013f
        p.color = Color.argb(205, ar, ag, ab)
        canvas.drawPath(body, p)

        // ön far (LED imzası) ve sis farı
        p.style = Paint.Style.FILL
        p.color = 0xFFFFF3C4.toInt()
        glass.reset()
        glass.moveTo(fx(.915f), fy(.212f)); glass.lineTo(fx(.985f), fy(.19f))
        glass.lineTo(fx(.985f), fy(.162f)); glass.lineTo(fx(.93f), fy(.178f))
        glass.close()
        canvas.drawPath(glass, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = carW * .007f
        p.color = Color.argb(230, 255, 255, 255)
        canvas.drawLine(fx(.905f), fy(.176f), fx(.95f), fy(.158f), p)
        p.style = Paint.Style.FILL
        p.color = 0xFFFFE08A.toInt()
        canvas.drawCircle(fx(.955f), fy(.108f), carW * .011f, p)

        // arka stop lambası
        p.color = 0xFFEF5350.toInt()
        glass.reset()
        glass.moveTo(fx(.018f), fy(.215f)); glass.lineTo(fx(.05f), fy(.236f))
        glass.lineTo(fx(.062f), fy(.205f)); glass.lineTo(fx(.024f), fy(.17f))
        glass.close()
        canvas.drawPath(glass, p)

        // tekerlekler
        for (wx in floatArrayOf(.215f, .795f)) {
            val wcx = fx(wx)
            val wcy = fy(.07f)
            val wr = carW * .082f
            p.style = Paint.Style.FILL
            p.color = 0xFF050B14.toInt()
            canvas.drawCircle(wcx, wcy, wr * 1.28f, p)
            p.color = 0xFF111C2B.toInt()
            canvas.drawCircle(wcx, wcy, wr * 1.04f, p)
            p.color = 0xFFA9BCD3.toInt()
            canvas.drawCircle(wcx, wcy, wr * .72f, p)
            p.color = 0xFF2B3E55.toInt()
            canvas.drawCircle(wcx, wcy, wr * .60f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = carW * .008f
            p.color = Color.argb(235, ar, ag, ab)
            for (k in 0 until 5) {
                val ang = Math.toRadians((k * 72 + 18).toDouble())
                canvas.drawLine(
                    wcx, wcy,
                    wcx + (wr * .66f * cos(ang)).toFloat(), wcy + (wr * .66f * sin(ang)).toFloat(), p
                )
            }
            p.style = Paint.Style.FILL
            p.color = 0xFFE6EDF7.toInt()
            canvas.drawCircle(wcx, wcy, wr * .14f, p)
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
