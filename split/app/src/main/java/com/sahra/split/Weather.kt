package com.sahra.split

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.max
import kotlin.math.min

/**
 * الطقس من Open-Meteo: مجاني وبدون مفتاح. يُحدَّث كل 30 دقيقة حسب موقع السيارة،
 * وتبقى آخر نتيجة محفوظة لتظهر فورًا عند التشغيل.
 */
object Weather {
    data class Day(val date: String, val code: Int, val max: Float, val min: Float, val rain: Int)
    data class Data(
        val temp: Float, val feels: Float, val humidity: Int, val wind: Float, val code: Int,
        val days: List<Day>, val at: Long,
    )

    private const val EVERY_MS = 30 * 60 * 1000L
    @Volatile var data: Data? = null
        private set
    @Volatile var error: String? = null
        private set
    @Volatile private var loading = false
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    fun ensure(context: Context, force: Boolean = false) {
        val p = Prefs(context)
        if (data == null && p.weatherJson.isNotEmpty()) data = runCatching { parse(JSONObject(p.weatherJson), p.weatherAt) }.getOrNull()
        val fresh = System.currentTimeMillis() - p.weatherAt < EVERY_MS
        if (loading || (fresh && !force && data != null)) return
        loading = true
        val app = context.applicationContext
        Thread {
            try {
                Sun.refreshLocation(app)
                val url = "https://api.open-meteo.com/v1/forecast?latitude=${p.lat}&longitude=${p.lon}" +
                    "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                    "&timezone=auto&forecast_days=4"
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000; readTimeout = 15_000
                }
                if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val now = System.currentTimeMillis()
                data = parse(JSONObject(body), now)
                p.weatherJson = body
                p.weatherAt = now
                error = null
            } catch (e: Exception) {
                error = "تعذر جلب الطقس — تحقق من الإنترنت"
            } finally {
                loading = false
                main.post { listeners.forEach { it() } }
            }
        }.start()
    }

    private fun parse(o: JSONObject, at: Long): Data {
        val cur = o.getJSONObject("current")
        val d = o.getJSONObject("daily")
        val dates = d.getJSONArray("time")
        val codes = d.getJSONArray("weather_code")
        val maxs = d.getJSONArray("temperature_2m_max")
        val mins = d.getJSONArray("temperature_2m_min")
        val rain = d.optJSONArray("precipitation_probability_max")
        val days = (0 until dates.length()).map { i ->
            Day(dates.getString(i), codes.optInt(i), maxs.optDouble(i).toFloat(), mins.optDouble(i).toFloat(), rain?.optInt(i) ?: 0)
        }
        return Data(
            temp = cur.optDouble("temperature_2m").toFloat(),
            feels = cur.optDouble("apparent_temperature").toFloat(),
            humidity = cur.optInt("relative_humidity_2m"),
            wind = cur.optDouble("wind_speed_10m").toFloat(),
            code = cur.optInt("weather_code"),
            days = days,
            at = at,
        )
    }

    /** رموز WMO إلى وصف ورمز من مجموعة الرموز. */
    fun describe(code: Int): Pair<String, Int> = when (code) {
        0 -> "صحو" to R.drawable.ic_sun
        1 -> "صحو غالبًا" to R.drawable.ic_partly
        2 -> "غائم جزئيًا" to R.drawable.ic_partly
        3 -> "غائم" to R.drawable.ic_cloud
        45, 48 -> "ضباب" to R.drawable.ic_fog
        51, 53, 55, 56, 57 -> "رذاذ" to R.drawable.ic_rain
        61, 63, 66 -> "مطر" to R.drawable.ic_rain
        65, 67 -> "مطر غزير" to R.drawable.ic_rain
        71, 73, 75, 77, 85, 86 -> "ثلج" to R.drawable.ic_snow
        80, 81, 82 -> "زخات مطر" to R.drawable.ic_rain
        95, 96, 99 -> "عاصفة رعدية" to R.drawable.ic_storm
        else -> "—" to R.drawable.ic_cloud
    }
}

/** الطقس على لوحة بنية (لوحات الوجهات السياحية). */
class WeatherPage(context: Context) : LivePage(context) {
    override val tickMs = 60_000L
    private val onWeather: () -> Unit = { invalidate() }
    private val dayName = SimpleDateFormat("EEEE", ArabicLatn)
    private val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    init { setOnClickListener { Weather.ensure(context, force = true); invalidate() } }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Weather.addListener(onWeather)
        Weather.ensure(context)
    }

    override fun onDetachedFromWindow() {
        Weather.removeListener(onWeather)
        super.onDetachedFromWindow()
    }

    override fun onState(state: VehicleState) { s = state }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val pad = min(w, h) * 0.07f
        val d = Weather.data
        if (d == null) {
            c.glyph(R.drawable.ic_partly, w / 2, h * 0.42f, min(w, h) * 0.3f, Palette.legendDim)
            c.say(Weather.error ?: "جارٍ جلب الطقس…", w / 2, h * 0.62f, w * 0.05f, Face.WORD, Palette.legendDim)
            return
        }
        val (desc, icon) = Weather.describe(d.code)
        val big = min(w * 0.24f, h * 0.15f)
        val top = pad + big
        c.glyph(icon, w - pad - big * 0.6f, top - big * 0.36f, big * 1.2f)
        val t = num(d.temp)
        c.say(t, pad, top, big, Face.DIGIT_BLACK, Palette.white, Paint.Align.LEFT)
        c.say("°", pad + width(t, big, Type.digitsBlack) + big * 0.04f, top, big * 0.7f, Face.DIGIT_BLACK, Palette.white, Paint.Align.LEFT)
        val small = min(w * 0.046f, h * 0.03f)
        c.say(desc, w - pad, top + big * 0.62f, small * 1.4f, Face.WORD_BOLD, Palette.white, Paint.Align.RIGHT)
        c.say("المحسوسة ${ltr(num(d.feels) + "°")}", pad, top + big * 0.62f, small, Face.WORD, Palette.legendDim, Paint.Align.LEFT)
        // في الشاشات القصيرة تُترك التفاصيل ويبقى التوقع، حتى لا يصغر الخط
        val compact = h < context.dp(420f)
        val rowsTop = top + big * 0.62f + small * 2.4f
        if (!compact) {
            c.destination("الرطوبة", "${d.humidity}", "%", pad, w - pad, rowsTop, small)
            c.destination("الرياح", num(d.wind), "كم/س", pad, w - pad, rowsTop + small * 2.6f, small)
            if (!s.outsideTemp.isNaN()) c.destination("حساس السيارة", "${num(s.outsideTemp)}°", "", pad, w - pad, rowsTop + small * 5.2f, small)
        }

        val days = d.days.drop(1).take(3)
        val dTop = if (compact) top + big * 0.62f + small * 1.4f else h * 0.56f
        val step = (h - pad * 1.6f - dTop) / 3
        days.forEachIndexed { i, day ->
            val y0 = dTop + step * i
            val r = RectF(pad * 0.6f, y0 + step * 0.06f, w - pad * 0.6f, y0 + step * 0.94f)
            // خط أبيض رفيع فوق كل يوم، كفواصل لوحات الوجهات
            fill.color = (Palette.white and 0x00FFFFFF) or 0x66000000
            c.drawRect(pad, y0, w - pad, y0 + dp1 * 1.5f, fill)
            val cy = r.centerY()
            val name = runCatching { dayName.format(iso.parse(day.date)!!) }.getOrDefault(day.date)
            c.say(name, r.right - pad * 0.6f, cy + small * 0.4f, small * 1.15f, Face.WORD_BOLD, Palette.white, Paint.Align.RIGHT)
            c.glyph(Weather.describe(day.code).second, w * 0.46f, cy, r.height() * 0.62f)
            c.say("${num(day.max)}°", r.left + pad * 0.6f + small * 2.6f, cy + small * 0.5f, small * 1.4f, Face.DIGIT_BLACK, Palette.white, Paint.Align.LEFT)
            c.say("${num(day.min)}°", r.left + pad * 0.6f, cy + small * 0.5f, small * 1.05f, Face.DIGIT, Palette.legendDim, Paint.Align.LEFT)
        }
        val ago = (System.currentTimeMillis() - d.at) / 60000
        val stamp = if (ago < 1) "محدّث الآن" else "قبل $ago دقيقة"
        c.say("$stamp · المس للتحديث", w / 2, h - pad * 0.45f, max(small * 0.85f, context.dp(13f)), Face.WORD, if (Weather.error != null) Palette.amber else Palette.legendDim)
    }
}

/** صفحة الموسيقى على لوحة زرقاء: الغلاف على لوح أبيض، والعنوان، وأزرار دائرية. */
class MusicPage(context: Context) : LinearLayout(context) {
    private val art = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val plate = FrameLayout(context)
    private val title = context.label("", 22f, Palette.white, true).apply {
        gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
    }
    private val artist = context.label("", 16f, Palette.legendDim).apply {
        gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
    }
    private val playIcon = context.icon(R.drawable.ic_play, 34, Palette.blue)
    private val listener: (NowPlaying) -> Unit = { show(it) }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        pad(context.dp(18))
        plate.background = rounded(Palette.white, context.dp(22f))
        plate.setPadding(context.dp(10), context.dp(10), context.dp(10), context.dp(10))
        art.background = rounded(Palette.white, context.dp(14f))
        art.clipToOutline = true
        plate.addView(art, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        addView(plate, LayoutParams(context.dp(168), context.dp(168)).apply { bottomMargin = context.dp(20) })
        addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(artist, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(6) })
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        row.addView(control(R.drawable.ic_prev, false) { MediaTracker.command("prev") })
        row.addView(control(0, true) { MediaTracker.command("toggle") })
        row.addView(control(R.drawable.ic_next, false) { MediaTracker.command("next") })
        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(22) })
    }

    private fun control(res: Int, primary: Boolean, onClick: () -> Unit): View {
        val size = context.dp(if (primary) 80 else 72)
        val f = FrameLayout(context).apply {
            background = if (primary) rounded(Palette.white, size / 2f) else rounded(0, size / 2f, context.dp(3), Palette.white)
            setOnClickListener { onClick() }
        }
        val ic = if (primary) playIcon else context.icon(res, 28)
        (ic.parent as? android.view.ViewGroup)?.removeView(ic)
        f.addView(ic, FrameLayout.LayoutParams(context.dp(if (primary) 34 else 28), context.dp(if (primary) 34 else 28), Gravity.CENTER))
        f.layoutParams = LayoutParams(size, size).apply { setMargins(context.dp(9), 0, context.dp(9), 0) }
        return f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        MediaTracker.addListener(listener)
    }

    override fun onDetachedFromWindow() {
        MediaTracker.removeListener(listener)
        super.onDetachedFromWindow()
    }

    private fun show(np: NowPlaying) {
        if (!MediaTracker.hasAccess(context)) {
            title.text = "اسمح بقراءة الإشعارات"
            artist.text = "المس هنا لفتح الإعداد"
            title.setOnClickListener {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            art.setImageResource(R.drawable.ic_music)
            art.imageTintList = android.content.res.ColorStateList.valueOf(Palette.blue)
            art.scaleType = ImageView.ScaleType.CENTER_INSIDE
            return
        }
        title.setOnClickListener(null)
        title.text = np.title.ifEmpty { "لا شيء يُشغَّل" }
        artist.text = if (np.app.isEmpty()) "" else listOf(np.artist, AppLauncher.label(context, np.app)).filter { it.isNotEmpty() }.joinToString(" · ")
        playIcon.setImageResource(if (np.playing) R.drawable.ic_pause else R.drawable.ic_play)
        val bmp = np.art
        if (bmp != null) {
            art.imageTintList = null
            art.scaleType = ImageView.ScaleType.CENTER_CROP
            art.setImageBitmap(bmp)
        } else {
            val appIcon = AppLauncher.icon(context, np.app)
            art.scaleType = ImageView.ScaleType.CENTER_INSIDE
            if (appIcon != null) { art.imageTintList = null; art.setImageDrawable(appIcon) }
            else {
                art.setImageResource(R.drawable.ic_music)
                art.imageTintList = android.content.res.ColorStateList.valueOf(Palette.blue)
            }
        }
    }
}
