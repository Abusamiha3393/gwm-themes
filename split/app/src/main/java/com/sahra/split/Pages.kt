package com.sahra.split

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.icu.util.IslamicCalendar
import android.view.View
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * صفحة من لوحة المعلومات تُرسم كلوحة طريق واحدة: كتابة بيضاء على لون اللوحة.
 * الخلفية (اللون والإطار) يرسمها الحاوي؛ الصفحة ترسم المحتوى فقط.
 */
abstract class LivePage(context: Context) : View(context), VehicleRepository.Listener {
    protected var s = VehicleState()
    protected val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    protected val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Palette.white }
    private val icons = HashMap<Int, Drawable>()
    protected val dp1 = context.dp(1f)

    /** فاصل التحديث الذاتي (للساعة مثلًا)، 0 = بدون. */
    protected open val tickMs = 0L
    private val ticker = object : Runnable {
        override fun run() { invalidate(); if (tickMs > 0) postDelayed(this, tickMs) }
    }

    init { Type.init(context) }

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

    protected enum class Face { WORD, WORD_BOLD, DIGIT, DIGIT_BLACK }

    protected fun Canvas.say(str: String, x: Float, y: Float, size: Float, face: Face = Face.WORD, color: Int = Palette.white, align: Paint.Align = Paint.Align.CENTER) {
        text.textSize = size
        text.color = color
        text.typeface = when (face) {
            Face.WORD -> Type.kufi
            Face.WORD_BOLD -> Type.kufiBold
            Face.DIGIT -> Type.digits
            Face.DIGIT_BLACK -> Type.digitsBlack
        }
        text.textAlign = align
        drawText(str, x, y, text)
    }

    protected fun width(str: String, size: Float, face: Typeface): Float {
        text.textSize = size; text.typeface = face
        return text.measureText(str)
    }

    protected fun Canvas.glyph(res: Int, cx: Float, cy: Float, size: Float, color: Int = Palette.white) {
        val d = icons.getOrPut(res) { context.getDrawable(res)!!.mutate() }
        d.setTint(color)
        val h = (size / 2).toInt()
        d.setBounds(cx.toInt() - h, cy.toInt() - h, cx.toInt() + h, cy.toInt() + h)
        d.draw(this)
    }

    /**
     * سطر بأسلوب لوحة المسافات: الاسم يمينًا، ثم نقاط، ثم الرقم والوحدة يسارًا.
     * x0..x1 حدود السطر، y خط القاعدة.
     */
    protected fun Canvas.destination(name: String, value: String, unit: String, x0: Float, x1: Float, y: Float, size: Float, color: Int = Palette.white) {
        say(name, x1, y, size, Face.WORD_BOLD, color, Paint.Align.RIGHT)
        val unitW = if (unit.isEmpty()) 0f else width(unit, size * 0.7f, Type.kufi) + size * 0.3f
        if (unit.isNotEmpty()) say(unit, x0, y, size * 0.7f, Face.WORD, Palette.legendDim, Paint.Align.LEFT)
        say(value, x0 + unitW, y, size * 1.5f, Face.DIGIT_BLACK, color, Paint.Align.LEFT)
        val nameW = width(name, size, Type.kufiBold)
        val valueW = width(value, size * 1.5f, Type.digitsBlack)
        val a = x0 + unitW + valueW + size * 0.4f
        val b = x1 - nameW - size * 0.4f
        if (b > a) {
            stroke.color = (Palette.white and 0x00FFFFFF) or 0x70000000
            stroke.strokeWidth = dp1 * 2
            stroke.pathEffect = DashPathEffect(floatArrayOf(dp1 * 1, dp1 * 5), 0f)
            drawLine(a, y - size * 0.25f, b, y - size * 0.25f, stroke)
            stroke.pathEffect = null
        }
    }

    /** لوحة صغيرة داخل الصفحة (مثل شارة حرارة الخارج). */
    protected fun Canvas.plaque(r: RectF, color: Int) {
        fill.color = color
        drawRoundRect(r, dp1 * 9, dp1 * 9, fill)
        stroke.color = Palette.white; stroke.strokeWidth = dp1 * 2
        val i = dp1 * 4
        drawRoundRect(RectF(r.left + i, r.top + i, r.right - i, r.bottom - i), dp1 * 6, dp1 * 6, stroke)
    }
}

// ------------------------------------------------------------------ الساعة (لوحة خضراء)

class ClockPage(context: Context) : LivePage(context) {
    override val tickMs = 1000L
    private val prefs = Prefs(context)
    private val hijriMonths = arrayOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة",
    )
    private val arabic = ArabicLatn

    init {
        setOnClickListener {
            prefs.clockStyle = 1 - prefs.clockStyle
            invalidate()
        }
    }

    private fun hijri(): String = runCatching {
        val c = IslamicCalendar()
        c.calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        String.format(Locale.US, "%d %s %dهـ", c.get(IslamicCalendar.DAY_OF_MONTH), hijriMonths[c.get(IslamicCalendar.MONTH)], c.get(IslamicCalendar.YEAR))
    }.getOrDefault("")

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val now = Calendar.getInstance()
        val unit = min(w, h * 0.62f)
        val dateSize = unit * 0.085f
        val hasTemp = !s.outsideTemp.isNaN()
        val th = unit * 0.17f
        val plaqueBlock = if (hasTemp) dateSize * 1.8f + th else 0f
        // الوقت ثم التاريخ ثم شارة الحرارة، كتلة واحدة في وسط اللوحة
        val dy: Float
        if (prefs.clockStyle == 1) {
            val r = min(w * 0.4f, h * 0.24f)
            val total = 2 * r + dateSize * 3.6f + plaqueBlock
            val top = (h - total) / 2
            drawAnalog(c, w / 2, top + r, r, now)
            dy = top + 2 * r + dateSize * 2.1f
        } else {
            val size = unit * 0.36f
            val total = size * 0.72f + dateSize * 3.6f + plaqueBlock
            val top = (h - total) / 2
            val ty = top + size * 0.72f
            c.say(String.format(Locale.US, "%d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)), w / 2, ty, size, Face.DIGIT_BLACK)
            dy = ty + dateSize * 2.1f
        }
        c.say(SimpleDateFormat("EEEE d MMMM", arabic).format(Date()), w / 2, dy, dateSize, Face.WORD_BOLD)
        c.say(hijri(), w / 2, dy + dateSize * 1.45f, dateSize * 0.72f, Face.WORD, Palette.legendDim)
        if (hasTemp) {
            val tw = unit * 0.62f
            val pt = dy + dateSize * 1.45f + dateSize * 1.5f
            val r = RectF(w / 2 - tw / 2, pt, w / 2 + tw / 2, pt + th)
            c.plaque(r, Palette.brown)
            c.glyph(R.drawable.ic_thermo, r.right - th * 0.62f, r.centerY(), th * 0.56f)
            c.say("الخارج", r.right - th * 1.1f, r.centerY() + th * 0.12f, th * 0.3f, Face.WORD_BOLD, Palette.white, Paint.Align.RIGHT)
            c.say("${num(s.outsideTemp)}°", r.left + th * 0.4f, r.centerY() + th * 0.2f, th * 0.5f, Face.DIGIT_BLACK, Palette.white, Paint.Align.LEFT)
        }
    }

    private fun drawAnalog(c: Canvas, cx: Float, cy: Float, r: Float, now: Calendar) {
        stroke.color = Palette.white
        stroke.strokeWidth = r * 0.05f
        c.drawCircle(cx, cy, r, stroke)
        for (i in 0 until 60) {
            val a = Math.toRadians(i * 6.0 - 90)
            val major = i % 5 == 0
            val r1 = r * (if (major) 0.76f else 0.85f)
            stroke.strokeWidth = r * (if (major) 0.05f else 0.018f)
            stroke.color = if (major) Palette.white else Palette.legendDim
            c.drawLine(cx + (r1 * cos(a)).toFloat(), cy + (r1 * sin(a)).toFloat(),
                cx + (r * 0.9f * cos(a)).toFloat(), cy + (r * 0.9f * sin(a)).toFloat(), stroke)
        }
        val sec = now.get(Calendar.SECOND)
        val minute = now.get(Calendar.MINUTE) + sec / 60f
        val hour = now.get(Calendar.HOUR) + minute / 60f
        hand(c, cx, cy, hour * 30f, r * 0.48f, r * 0.08f)
        hand(c, cx, cy, minute * 6f, r * 0.7f, r * 0.055f)
        fill.color = Palette.white
        c.drawCircle(cx, cy, r * 0.06f, fill)
    }

    private fun hand(c: Canvas, cx: Float, cy: Float, deg: Float, len: Float, width: Float, color: Int = Palette.white) {
        val a = Math.toRadians(deg - 90.0)
        stroke.color = color; stroke.strokeWidth = width
        c.drawLine(cx, cy, cx + (len * cos(a)).toFloat(), cy + (len * sin(a)).toFloat(), stroke)
    }
}

// ------------------------------------------------------------------ الوقود (لوحة خدمات زرقاء)

class FuelPage(context: Context) : LivePage(context) {
    private val prefs = Prefs(context)

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val pad = min(w, h) * 0.07f
        val x0 = pad; val x1 = w - pad
        val fuel = s.fuelPercent
        val low = !fuel.isNaN() && fuel < 12f
        val big = min(w * 0.22f, h * 0.12f)
        // الرأس: مضخة الوقود ونسبة كبيرة
        val headY = pad + big
        c.glyph(if (low) R.drawable.ic_warning else R.drawable.ic_fuel, x1 - big * 0.55f, headY - big * 0.38f, big * 1.05f, if (low) Palette.amber else Palette.white)
        val pct = num(fuel)
        c.say("%", x1 - big * 1.35f, headY, big * 0.42f, Face.DIGIT_BLACK, Palette.white, Paint.Align.RIGHT)
        c.say(pct, x1 - big * 1.35f - width("%", big * 0.42f, Type.digitsBlack) - big * 0.05f, headY, big, Face.DIGIT_BLACK, if (low) Palette.amber else Palette.white, Paint.Align.RIGHT)
        c.say(if (low) "الوقود منخفض" else "الوقود في الخزان", x1 - big * 1.35f, headY + big * 0.42f, big * 0.26f, Face.WORD, if (low) Palette.amber else Palette.legendDim, Paint.Align.RIGHT)
        // مقياس بعلامات الأرباع، يمتلئ من اليمين
        val barTop = headY + big * 0.75f
        val barH = big * 0.22f
        stroke.color = Palette.white; stroke.strokeWidth = dp1 * 2
        c.drawRoundRect(RectF(x0, barTop, x1, barTop + barH), dp1 * 3, dp1 * 3, stroke)
        if (!fuel.isNaN()) {
            fill.color = if (low) Palette.amber else Palette.white
            c.drawRect(x1 - (x1 - x0) * fuel / 100f, barTop, x1, barTop + barH, fill)
        }
        fill.color = Palette.white
        for (q in 1..3) {
            val x = x0 + (x1 - x0) * q / 4f
            c.drawRect(x - dp1, barTop - barH * 0.4f, x + dp1, barTop + barH * 1.4f, fill)
        }
        // سطور المسافات
        val liters = if (fuel.isNaN()) Float.NaN else fuel / 100f * prefs.tankLiters
        val rows = listOf(
            Triple("المدى المتبقي", num(s.rangeKm), "كم"),
            Triple("في الخزان", num(liters, 1), "لتر"),
            Triple("منذ التعبئة", num(FuelTracker.kmSinceRefuel(context, s)), "كم"),
            Triple("الاستهلاك", num(FuelTracker.avgSinceRefuel(context, s), 1), "ل/100"),
            Triple("الرحلة الحالية", num(FuelTracker.tripKm(s), 1), "كم"),
        )
        val top = barTop + barH + big * 0.5f
        val footer = big * 0.55f
        val step = (h - pad - footer - top) / rows.size
        val size = min(step * 0.34f, w * 0.058f)
        rows.forEachIndexed { i, (k, v, u) ->
            c.destination(k, v, u, x0, x1, top + step * i + step * 0.62f, size)
        }
        c.say("العداد  ${num(s.odometerKm)}  كم", w / 2, h - pad, big * 0.24f, Face.WORD, Palette.legendDim)
    }
}

// ------------------------------------------------------------------ الرسوم البيانية (لوحة خضراء)

class ChartsPage(context: Context) : LivePage(context) {
    override val tickMs = 1000L
    override fun onState(state: VehicleState) { s = state }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val half = h / 2
        chart(c, History.snapshot(History.speed), 0f, half, w, "السرعة", "كم/س", 40f, R.drawable.ic_speed)
        fill.color = (Palette.white and 0x00FFFFFF) or 0x40000000
        c.drawRect(w * 0.06f, half - dp1, w * 0.94f, half + dp1, fill)
        chart(c, History.snapshot(History.rpm), half, half, w, "الدوران", "د/د", 1000f, R.drawable.ic_chart)
    }

    private fun chart(c: Canvas, data: FloatArray, top: Float, h: Float, w: Float, title: String, unit: String, step: Float, res: Int) {
        val padX = w * 0.07f
        val head = h * 0.3f
        val bottom = top + h - h * 0.12f
        val plotTop = top + head + h * 0.03f
        val peak = data.maxOrNull() ?: 0f
        val maxV = max(step * 2, kotlin.math.ceil(peak / step) * step)
        val now = data.lastOrNull() ?: Float.NaN
        val ts = head * 0.34f
        val ty = top + head * 0.5f
        c.glyph(res, w - padX - ts * 0.5f, ty - ts * 0.35f, ts * 1.1f)
        c.say(title, w - padX - ts * 1.4f, ty, ts, Face.WORD_BOLD, Palette.white, Paint.Align.RIGHT)
        c.say("$unit · الأعلى ${num(peak)}", w - padX, ty + ts * 1.15f, ts * 0.62f, Face.WORD, Palette.legendDim, Paint.Align.RIGHT)
        // الرقم الحالي يسارًا، ويصغر إن لم يتسع بجانب العنوان
        var vs = ts * 1.8f
        val room = (w - 2 * padX) - ts * 1.4f - width(title, ts, Type.kufiBold) - ts * 0.8f
        val vw = width(num(now), vs, Type.digitsBlack)
        if (vw > room) vs *= room / vw
        c.say(num(now), padX, ty + ts * 0.6f, vs, Face.DIGIT_BLACK, Palette.white, Paint.Align.LEFT)
        stroke.color = (Palette.white and 0x00FFFFFF) or 0x40000000
        stroke.strokeWidth = dp1
        stroke.pathEffect = DashPathEffect(floatArrayOf(dp1 * 6, dp1 * 6), 0f)
        for (i in 0..2) {
            val y = plotTop + (bottom - plotTop) * i / 2f
            c.drawLine(padX, y, w - padX, y, stroke)
        }
        stroke.pathEffect = null
        if (data.size < 2) {
            c.say("آخر 10 دقائق — بانتظار البيانات", w / 2, (plotTop + bottom) / 2, ts * 0.6f, Face.WORD, Palette.legendDim)
            return
        }
        val path = Path(); val area = Path()
        val dx = (w - 2 * padX) / (History.SIZE - 1)
        val x0 = w - padX - dx * (data.size - 1)
        data.forEachIndexed { i, v ->
            val x = x0 + dx * i
            val y = bottom - (bottom - plotTop) * (v / maxV).coerceIn(0f, 1f)
            if (i == 0) { path.moveTo(x, y); area.moveTo(x, bottom); area.lineTo(x, y) } else { path.lineTo(x, y); area.lineTo(x, y) }
        }
        area.lineTo(w - padX, bottom); area.close()
        fill.color = (Palette.white and 0x00FFFFFF) or 0x26000000
        c.drawPath(area, fill)
        stroke.color = Palette.white; stroke.strokeWidth = dp1 * 3; stroke.strokeJoin = Paint.Join.ROUND
        c.drawPath(path, stroke)
        c.say("قبل 10 د", padX, bottom + h * 0.08f, ts * 0.5f, Face.WORD, Palette.legendDim, Paint.Align.LEFT)
        c.say("الآن", w - padX, bottom + h * 0.08f, ts * 0.5f, Face.WORD, Palette.legendDim, Paint.Align.RIGHT)
    }
}

// ------------------------------------------------------------------ الطرق الوعرة (لوحة بنية)

class OffroadPage(context: Context) : LivePage(context) {
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        // في الشاشات القصيرة تُترك البوصلة وتكبر دائرتا الميلان
        val compact = h < context.dp(420f)
        val r = if (compact) min(w * 0.2f, h * 0.2f) else min(w * 0.2f, h * 0.13f)
        val y1 = h * 0.06f + r
        tilt(c, w * 0.73f, y1, r, s.pitch, "أمامي", false)
        tilt(c, w * 0.27f, y1, r, s.roll, "جانبي", true)
        if (!compact) {
            val cr = min(w * 0.27f, h * 0.17f)
            compass(c, w / 2, y1 + r * 1.75f + cr, cr, s.compass)
        }
        val pad = w * 0.08f
        val size = min(w * 0.055f, h * 0.035f)
        val y = h - h * 0.05f
        c.destination("الارتفاع", num(s.altitude), "م", pad, w - pad, y - size * 2.4f, size)
        c.destination("زاوية المقود", "${num(s.steeringAngle)}°", "", pad, w - pad, y, size)
    }

    private fun tilt(c: Canvas, cx: Float, cy: Float, r: Float, angle: Float, title: String, side: Boolean) {
        val a = if (angle.isNaN()) 0f else angle
        val danger = abs(a) >= (if (side) 30f else 25f)
        val ink = if (danger) Palette.amber else Palette.white
        stroke.color = ink; stroke.strokeWidth = r * 0.07f
        c.drawCircle(cx, cy, r, stroke)
        // علامات 15 و30 درجة
        for (t in listOf(-30, -15, 15, 30)) {
            val rad = Math.toRadians(t.toDouble())
            stroke.strokeWidth = r * 0.04f
            c.drawLine(cx - (r * cos(rad)).toFloat(), cy - (r * sin(rad)).toFloat(), cx - (r * 0.85f * cos(rad)).toFloat(), cy - (r * 0.85f * sin(rad)).toFloat(), stroke)
        }
        c.save()
        c.rotate(if (side) -a else a, cx, cy)
        fill.color = ink
        if (side) {
            // السيارة من الخلف
            c.drawRoundRect(RectF(cx - r * 0.5f, cy - r * 0.32f, cx + r * 0.5f, cy + r * 0.16f), r * 0.1f, r * 0.1f, fill)
            c.drawRoundRect(RectF(cx - r * 0.36f, cy - r * 0.52f, cx + r * 0.36f, cy - r * 0.28f), r * 0.08f, r * 0.08f, fill)
            c.drawRect(cx - r * 0.5f, cy + r * 0.1f, cx - r * 0.3f, cy + r * 0.36f, fill)
            c.drawRect(cx + r * 0.3f, cy + r * 0.1f, cx + r * 0.5f, cy + r * 0.36f, fill)
        } else {
            // السيارة من الجانب
            c.drawRoundRect(RectF(cx - r * 0.6f, cy - r * 0.22f, cx + r * 0.6f, cy + r * 0.14f), r * 0.08f, r * 0.08f, fill)
            c.drawRoundRect(RectF(cx - r * 0.4f, cy - r * 0.46f, cx + r * 0.22f, cy - r * 0.2f), r * 0.08f, r * 0.08f, fill)
            c.drawCircle(cx - r * 0.34f, cy + r * 0.18f, r * 0.15f, fill)
            c.drawCircle(cx + r * 0.34f, cy + r * 0.18f, r * 0.15f, fill)
        }
        c.restore()
        c.say("${num(angle)}°", cx, cy + r * 1.42f, r * 0.42f, Face.DIGIT_BLACK, ink)
        c.say(title, cx, cy + r * 1.82f, r * 0.24f, Face.WORD, Palette.legendDim)
    }

    private fun compass(c: Canvas, cx: Float, cy: Float, r: Float, heading: Float) {
        val hdg = if (heading.isNaN()) 0f else heading
        stroke.color = Palette.white; stroke.strokeWidth = r * 0.05f
        c.drawCircle(cx, cy, r, stroke)
        c.save()
        c.rotate(-hdg, cx, cy)
        for (i in 0 until 36) {
            val major = i % 9 == 0
            stroke.strokeWidth = r * (if (major) 0.045f else 0.018f)
            stroke.color = if (major) Palette.white else Palette.legendDim
            val a = Math.toRadians(i * 10.0 - 90)
            val r1 = r * (if (major) 0.78f else 0.86f)
            c.drawLine(cx + (r1 * cos(a)).toFloat(), cy + (r1 * sin(a)).toFloat(),
                cx + (r * 0.93f * cos(a)).toFloat(), cy + (r * 0.93f * sin(a)).toFloat(), stroke)
        }
        c.restore()
        // الجهات تدور مع القرص لكن حروفها تبقى قائمة
        val names = arrayOf("ش", "شر", "ج", "غ")
        val ls = max(r * 0.22f, min(context.dp(16f), r * 0.28f))
        for (i in 0 until 4) {
            val a = Math.toRadians(i * 90.0 - 90 - hdg)
            c.say(names[i], cx + (r * 0.58f * cos(a)).toFloat(), cy + (r * 0.58f * sin(a)).toFloat() + ls * 0.36f,
                ls, Face.WORD_BOLD, if (i == 0) Palette.amber else Palette.white)
        }
        val p = Path().apply {
            moveTo(cx, cy - r * 1.06f); lineTo(cx - r * 0.1f, cy - r * 0.86f); lineTo(cx + r * 0.1f, cy - r * 0.86f); close()
        }
        fill.color = Palette.white
        c.drawPath(p, fill)
        c.say("${num(heading)}°", cx, cy + r * 0.13f, r * 0.34f, Face.DIGIT_BLACK)
    }
}
