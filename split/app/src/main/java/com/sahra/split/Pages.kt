package com.sahra.split

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.icu.util.IslamicCalendar
import android.view.View
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** صفحة تُرسم بالكود وتُحدَّث مع بيانات السيارة ما دامت ظاهرة. */
abstract class LivePage(context: Context) : View(context), VehicleRepository.Listener {
    protected var s = VehicleState()
    protected val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    protected val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Palette.fg }
    protected val bold: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

    /** فاصل التحديث الذاتي (للساعة مثلًا)، 0 = بدون. */
    protected open val tickMs = 0L
    private val ticker = object : Runnable {
        override fun run() { invalidate(); if (tickMs > 0) postDelayed(this, tickMs) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        VehicleRepository.addListener(this)
        if (tickMs > 0) post(ticker)
    }

    override fun onDetachedFromWindow() {
        VehicleRepository.removeListener(this)
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    override fun onState(state: VehicleState) {
        s = state
        invalidate()
    }

    protected fun Canvas.label(str: String, x: Float, y: Float, size: Float, color: Int = Palette.fg, isBold: Boolean = false, align: Paint.Align = Paint.Align.CENTER) {
        text.textSize = size
        text.color = color
        text.typeface = if (isBold) bold else Typeface.DEFAULT
        text.textAlign = align
        drawText(str, x, y, text)
    }
}

// ------------------------------------------------------------------ الساعة

class ClockPage(context: Context) : LivePage(context) {
    override val tickMs = 1000L
    private val prefs = Prefs(context)
    private val hijriMonths = arrayOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة",
    )

    init {
        setOnClickListener {
            prefs.clockStyle = 1 - prefs.clockStyle
            invalidate()
        }
    }

    private fun hijri(): String = runCatching {
        val c = IslamicCalendar()
        c.calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        "${c.get(IslamicCalendar.DAY_OF_MONTH)} ${hijriMonths[c.get(IslamicCalendar.MONTH)]} ${c.get(IslamicCalendar.YEAR)}هـ"
    }.getOrDefault("")

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val now = Calendar.getInstance()
        val dateText = SimpleDateFormat("EEEE d MMMM", Locale("ar")).format(Date())
        val footer = h * 0.2f
        if (prefs.clockStyle == 0) drawAnalog(c, w, h - footer, now) else drawDigital(c, w, h - footer, now)
        c.label(dateText, w / 2, h - footer + w * 0.07f, w * 0.065f, Palette.fg, true)
        c.label(hijri(), w / 2, h - footer + w * 0.14f, w * 0.055f, Palette.muted)
        if (!s.outsideTemp.isNaN()) c.label("الخارج ${num(s.outsideTemp)}°", w / 2, h - w * 0.02f, w * 0.05f, Palette.accent)
    }

    private fun drawAnalog(c: Canvas, w: Float, h: Float, now: Calendar) {
        val cx = w / 2; val cy = h / 2
        val r = min(w, h) * 0.42f
        fill.color = Palette.card
        c.drawCircle(cx, cy, r, fill)
        stroke.color = Palette.line; stroke.strokeWidth = r * 0.02f
        c.drawCircle(cx, cy, r, stroke)
        for (i in 0 until 60) {
            val a = Math.toRadians(i * 6.0 - 90)
            val major = i % 5 == 0
            val r1 = r * (if (major) 0.82f else 0.9f)
            stroke.color = if (major) Palette.fg else Palette.muted
            stroke.strokeWidth = r * (if (major) 0.03f else 0.012f)
            c.drawLine(cx + (r1 * cos(a)).toFloat(), cy + (r1 * sin(a)).toFloat(),
                cx + (r * 0.95f * cos(a)).toFloat(), cy + (r * 0.95f * sin(a)).toFloat(), stroke)
        }
        val sec = now.get(Calendar.SECOND)
        val minute = now.get(Calendar.MINUTE) + sec / 60f
        val hour = now.get(Calendar.HOUR) + minute / 60f
        hand(c, cx, cy, hour * 30f, r * 0.5f, r * 0.06f, Palette.fg)
        hand(c, cx, cy, minute * 6f, r * 0.75f, r * 0.04f, Palette.fg)
        hand(c, cx, cy, sec * 6f, r * 0.85f, r * 0.015f, Palette.accent)
        fill.color = Palette.accent
        c.drawCircle(cx, cy, r * 0.05f, fill)
    }

    private fun hand(c: Canvas, cx: Float, cy: Float, deg: Float, len: Float, width: Float, color: Int) {
        val a = Math.toRadians(deg - 90.0)
        stroke.color = color; stroke.strokeWidth = width
        c.drawLine(cx, cy, cx + (len * cos(a)).toFloat(), cy + (len * sin(a)).toFloat(), stroke)
    }

    private fun drawDigital(c: Canvas, w: Float, h: Float, now: Calendar) {
        val t = String.format(Locale.US, "%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
        val size = min(w * 0.36f, h * 0.5f)
        c.label(t, w / 2, h / 2 + size * 0.35f, size, Palette.fg, true)
        c.label(String.format(Locale.US, "%02d", now.get(Calendar.SECOND)), w / 2, h / 2 + size * 0.85f, size * 0.25f, Palette.accent)
    }
}

// ------------------------------------------------------------------ الوقود

class FuelPage(context: Context) : LivePage(context) {
    private val prefs = Prefs(context)

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2
        val r = min(w * 0.36f, h * 0.22f)
        val cy = r + h * 0.06f
        val fuel = s.fuelPercent
        // قوس الوقود
        val box = RectF(cx - r, cy - r, cx + r, cy + r)
        stroke.strokeWidth = r * 0.16f
        stroke.color = Palette.line
        c.drawArc(box, 150f, 240f, false, stroke)
        if (!fuel.isNaN()) {
            stroke.color = when { fuel < 12f -> Palette.warn; fuel < 25f -> Palette.accent; else -> Palette.good }
            c.drawArc(box, 150f, 240f * fuel / 100f, false, stroke)
        }
        c.label("${num(fuel)}%", cx, cy + r * 0.15f, r * 0.5f, Palette.fg, true)
        c.label("الوقود", cx, cy + r * 0.55f, r * 0.2f, Palette.muted)

        val liters = if (fuel.isNaN()) Float.NaN else fuel / 100f * prefs.tankLiters
        val rows = listOf(
            "المدى المتبقي" to "${num(s.rangeKm)} كم",
            "المتبقي في الخزان" to "${num(liters)} لتر",
            "الاستهلاك منذ التعبئة" to "${num(FuelTracker.avgSinceRefuel(context, s), 1)} ل/100",
            "منذ آخر تعبئة" to "${num(FuelTracker.kmSinceRefuel(context, s))} كم",
            "الرحلة الحالية" to "${num(FuelTracker.tripKm(s), 1)} كم",
            "العداد الكلي" to "${num(s.odometerKm)} كم",
        )
        val top = cy + r + h * 0.06f
        val step = (h - top) / rows.size
        val size = min(step * 0.42f, w * 0.06f)
        rows.forEachIndexed { i, (k, v) ->
            val y = top + step * i + step * 0.62f
            c.label(k, w * 0.94f, y, size, Palette.muted, align = Paint.Align.RIGHT)
            c.label(v, w * 0.06f, y, size, Palette.fg, true, Paint.Align.LEFT)
            if (i < rows.size - 1) {
                fill.color = Palette.line
                c.drawRect(w * 0.06f, top + step * (i + 1), w * 0.94f, top + step * (i + 1) + 1, fill)
            }
        }
    }
}

// ------------------------------------------------------------------ الرسوم البيانية

class ChartsPage(context: Context) : LivePage(context) {
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val half = h / 2
        chart(c, History.snapshot(History.speed), 0f, half, w, "السرعة", "كم/س", Palette.accent, 40f)
        chart(c, History.snapshot(History.rpm), half, half, w, "الدورات", "د/د", Palette.blue, 1000f)
    }

    private fun chart(c: Canvas, data: FloatArray, top: Float, h: Float, w: Float, title: String, unit: String, color: Int, step: Float) {
        val padX = w * 0.05f
        val head = h * 0.2f
        val bottom = top + h - h * 0.08f
        val plotTop = top + head
        val peak = data.maxOrNull() ?: 0f
        val maxV = max(step * 2, kotlin.math.ceil(peak / step) * step)
        val now = data.lastOrNull() ?: 0f
        c.label(title, w - padX, top + head * 0.7f, head * 0.42f, Palette.fg, true, Paint.Align.RIGHT)
        c.label("${num(now)} $unit · الأعلى ${num(peak)}", padX, top + head * 0.7f, head * 0.34f, color, false, Paint.Align.LEFT)
        // خطوط الشبكة
        fill.color = Palette.line
        for (i in 0..2) {
            val y = plotTop + (bottom - plotTop) * i / 2f
            c.drawRect(padX, y, w - padX, y + 1, fill)
        }
        if (data.size < 2) {
            c.label("آخر 10 دقائق — لا بيانات بعد", w / 2, (plotTop + bottom) / 2, head * 0.32f, Palette.muted)
            return
        }
        val path = Path()
        val area = Path()
        val dx = (w - 2 * padX) / (History.SIZE - 1)
        val x0 = w - padX - dx * (data.size - 1)
        data.forEachIndexed { i, v ->
            val x = x0 + dx * i
            val y = bottom - (bottom - plotTop) * (v / maxV).coerceIn(0f, 1f)
            if (i == 0) { path.moveTo(x, y); area.moveTo(x, bottom); area.lineTo(x, y) } else { path.lineTo(x, y); area.lineTo(x, y) }
        }
        area.lineTo(w - padX, bottom); area.close()
        fill.color = (color and 0x00FFFFFF) or 0x30000000
        c.drawPath(area, fill)
        stroke.color = color; stroke.strokeWidth = context.dp(2f)
        c.drawPath(path, stroke)
    }

    // الرسم مرة كل ثانية يكفي، مهما كان معدل وصول البيانات
    override val tickMs = 1000L

    override fun onState(state: VehicleState) {
        s = state
    }
}

// ------------------------------------------------------------------ الطرق الوعرة

class OffroadPage(context: Context) : LivePage(context) {
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val r = min(w * 0.22f, h * 0.15f)
        val y1 = h * 0.22f
        tilt(c, w * 0.27f, y1, r, s.pitch, "الميلان الأمامي", true)
        tilt(c, w * 0.73f, y1, r, s.roll, "الميلان الجانبي", false)
        compass(c, w / 2, h * 0.62f, min(w * 0.3f, h * 0.17f), s.compass)
        val size = w * 0.055f
        c.label("الارتفاع ${num(s.altitude)} م", w * 0.94f, h * 0.93f, size, Palette.fg, true, Paint.Align.RIGHT)
        c.label("المقود ${num(s.steeringAngle)}°", w * 0.06f, h * 0.93f, size, Palette.fg, true, Paint.Align.LEFT)
    }

    private fun tilt(c: Canvas, cx: Float, cy: Float, r: Float, angle: Float, title: String, side: Boolean) {
        fill.color = Palette.card
        c.drawCircle(cx, cy, r, fill)
        val a = if (angle.isNaN()) 0f else angle
        val danger = kotlin.math.abs(a) >= (if (side) 30f else 25f)
        c.save()
        c.rotate(if (side) -a else a, cx, cy)
        // الأفق
        stroke.color = Palette.line; stroke.strokeWidth = r * 0.03f
        c.drawLine(cx - r * 0.9f, cy + r * 0.3f, cx + r * 0.9f, cy + r * 0.3f, stroke)
        // جسم السيارة مبسّط
        fill.color = if (danger) Palette.warn else Palette.accent
        if (side) {
            c.drawRoundRect(RectF(cx - r * 0.55f, cy - r * 0.2f, cx + r * 0.55f, cy + r * 0.15f), r * 0.08f, r * 0.08f, fill)
            c.drawRoundRect(RectF(cx - r * 0.35f, cy - r * 0.42f, cx + r * 0.25f, cy - r * 0.18f), r * 0.08f, r * 0.08f, fill)
            fill.color = Palette.fg
            c.drawCircle(cx - r * 0.33f, cy + r * 0.18f, r * 0.13f, fill)
            c.drawCircle(cx + r * 0.33f, cy + r * 0.18f, r * 0.13f, fill)
        } else {
            c.drawRoundRect(RectF(cx - r * 0.45f, cy - r * 0.3f, cx + r * 0.45f, cy + r * 0.15f), r * 0.1f, r * 0.1f, fill)
            fill.color = Palette.fg
            c.drawRect(cx - r * 0.5f, cy + r * 0.05f, cx - r * 0.35f, cy + r * 0.3f, fill)
            c.drawRect(cx + r * 0.35f, cy + r * 0.05f, cx + r * 0.5f, cy + r * 0.3f, fill)
        }
        c.restore()
        c.label("${num(angle)}°", cx, cy + r * 0.78f, r * 0.3f, if (danger) Palette.warn else Palette.fg, true)
        c.label(title, cx, cy + r * 1.35f, r * 0.24f, Palette.muted)
    }

    private fun compass(c: Canvas, cx: Float, cy: Float, r: Float, heading: Float) {
        fill.color = Palette.card
        c.drawCircle(cx, cy, r, fill)
        val hdg = if (heading.isNaN()) 0f else heading
        c.save()
        c.rotate(-hdg, cx, cy)
        val names = arrayOf("ش", "شر", "ج", "غ")
        for (i in 0 until 36) {
            val major = i % 9 == 0
            stroke.color = if (major) Palette.fg else Palette.muted
            stroke.strokeWidth = r * (if (major) 0.03f else 0.012f)
            val a = Math.toRadians(i * 10.0 - 90)
            val r1 = r * (if (major) 0.78f else 0.86f)
            c.drawLine(cx + (r1 * cos(a)).toFloat(), cy + (r1 * sin(a)).toFloat(),
                cx + (r * 0.94f * cos(a)).toFloat(), cy + (r * 0.94f * sin(a)).toFloat(), stroke)
        }
        for (i in 0 until 4) {
            val a = Math.toRadians(i * 90.0 - 90)
            c.label(names[i], cx + (r * 0.6f * cos(a)).toFloat(), cy + (r * 0.6f * sin(a)).toFloat() + r * 0.08f,
                r * 0.2f, if (i == 0) Palette.warn else Palette.fg, true)
        }
        c.restore()
        // مؤشر الاتجاه ثابت للأعلى
        val p = Path().apply {
            moveTo(cx, cy - r * 1.02f); lineTo(cx - r * 0.08f, cy - r * 0.84f); lineTo(cx + r * 0.08f, cy - r * 0.84f); close()
        }
        fill.color = Palette.accent
        c.drawPath(p, fill)
        c.label("${num(heading)}°", cx, cy + r * 0.12f, r * 0.3f, Palette.fg, true)
    }
}
