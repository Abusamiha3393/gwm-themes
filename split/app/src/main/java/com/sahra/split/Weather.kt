package com.sahra.split

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet
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

    /** رموز WMO إلى وصف ورمز. */
    fun describe(code: Int): Pair<String, String> = when (code) {
        0 -> "صحو" to "☀️"
        1 -> "صحو غالبًا" to "🌤️"
        2 -> "غائم جزئيًا" to "⛅"
        3 -> "غائم" to "☁️"
        45, 48 -> "ضباب" to "🌫️"
        51, 53, 55, 56, 57 -> "رذاذ" to "🌦️"
        61, 63, 66 -> "مطر" to "🌧️"
        65, 67 -> "مطر غزير" to "🌧️"
        71, 73, 75, 77, 85, 86 -> "ثلج" to "❄️"
        80, 81, 82 -> "زخات مطر" to "🌦️"
        95, 96, 99 -> "عاصفة رعدية" to "⛈️"
        else -> "—" to "🌡️"
    }
}

class WeatherPage(context: Context) : LivePage(context) {
    override val tickMs = 60_000L
    private val onWeather: () -> Unit = { invalidate() }
    private val dayName = SimpleDateFormat("EEEE", Locale("ar"))
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
        val d = Weather.data
        if (d == null) {
            c.label(Weather.error ?: "جارٍ جلب الطقس…", w / 2, h / 2, w * 0.055f, Palette.muted)
            return
        }
        val (desc, icon) = Weather.describe(d.code)
        val big = min(w * 0.22f, h * 0.14f)
        c.label(icon, w * 0.27f, h * 0.2f, big * 1.1f)
        c.label("${num(d.temp)}°", w * 0.7f, h * 0.2f, big * 1.2f, Palette.fg, true)
        c.label(desc, w / 2, h * 0.3f, w * 0.065f, Palette.accent, true)
        val small = w * 0.048f
        c.label("المحسوسة ${num(d.feels)}° · الرطوبة ${d.humidity}% · الرياح ${num(d.wind)} كم/س", w / 2, h * 0.37f, small, Palette.muted)
        if (!s.outsideTemp.isNaN()) c.label("حساس السيارة ${num(s.outsideTemp)}°", w / 2, h * 0.43f, small, Palette.muted)

        val days = d.days.drop(1).take(3)
        val top = h * 0.5f
        val step = (h * 0.42f) / 3
        days.forEachIndexed { i, day ->
            val y = top + step * i + step * 0.6f
            fill.color = Palette.card
            c.drawRoundRect(w * 0.04f, top + step * i + step * 0.08f, w * 0.96f, top + step * (i + 1) - step * 0.08f, step * 0.2f, step * 0.2f, fill)
            val name = runCatching { dayName.format(iso.parse(day.date)!!) }.getOrDefault(day.date)
            c.label(name, w * 0.92f, y, small * 1.1f, Palette.fg, true, Paint.Align.RIGHT)
            c.label(Weather.describe(day.code).second, w * 0.5f, y, small * 1.4f)
            c.label("${num(day.max)}° / ${num(day.min)}°", w * 0.08f, y, small * 1.1f, Palette.fg, false, Paint.Align.LEFT)
        }
        val ago = (System.currentTimeMillis() - d.at) / 60000
        val stamp = if (ago < 1) "محدّث الآن" else "قبل $ago دقيقة"
        c.label("$stamp · المس للتحديث · Open-Meteo", w / 2, h * 0.97f, small * 0.8f, if (Weather.error != null) Palette.warn else Palette.muted)
    }
}

/** صفحة الموسيقى: الغلاف والعنوان وأزرار التحكم. */
class MusicPage(context: Context) : LinearLayout(context) {
    private val art = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val title = context.label("", 20f, Palette.fg, true).apply {
        gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
    }
    private val artist = context.label("", 16f, Palette.muted).apply {
        gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
    }
    private val playBtn = context.pill("▶") { MediaTracker.command("toggle") }
    private val listener: (NowPlaying) -> Unit = { show(it) }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        pad(context.dp(16))
        addView(art, LayoutParams(context.dp(160), context.dp(160)).apply { bottomMargin = context.dp(16) })
        addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(artist, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER }
        val lp = { LayoutParams(context.dp(72), context.dp(56)).apply { setMargins(context.dp(6), context.dp(16), context.dp(6), 0) } }
        row.addView(context.pill("⏮") { MediaTracker.command("prev") }, lp())
        row.addView(playBtn, lp())
        row.addView(context.pill("⏭") { MediaTracker.command("next") }, lp())
        addView(row)
        art.background = rounded(Palette.card, context.dp(16f))
        art.clipToOutline = true
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
            art.setImageDrawable(null)
            return
        }
        title.setOnClickListener(null)
        title.text = np.title.ifEmpty { "لا يوجد تشغيل" }
        artist.text = if (np.app.isEmpty()) "" else listOf(np.artist, AppLauncher.label(context, np.app)).filter { it.isNotEmpty() }.joinToString(" · ")
        playBtn.text = if (np.playing) "⏸" else "▶"
        val bmp = np.art
        if (bmp != null) art.setImageBitmap(bmp) else art.setImageDrawable(AppLauncher.icon(context, np.app))
    }
}
