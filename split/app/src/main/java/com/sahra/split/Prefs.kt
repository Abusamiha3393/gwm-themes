package com.sahra.split

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** جانب لوحة المعلومات. */
enum class PanelSide(val label: String) { LEFT("يسار"), RIGHT("يمين"), NONE("مخفية") }

/**
 * إعداد مسبق: أي تطبيقين يُفتحان، وبأي نسبة، وأين تظهر لوحة المعلومات وبأي عرض، وأي صفحة فيها.
 * هناك 7 إعدادات، ويمكن تبديلها تلقائيًا بين النهار والليل.
 */
data class Preset(
    val name: String,
    val navApp: String = "",
    val musicApp: String = "",
    val ratio: Float = 0.6f,
    val side: PanelSide = PanelSide.LEFT,
    val panelWidth: Float = 0.28f,
    val page: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name); put("nav", navApp); put("music", musicApp)
        put("ratio", ratio.toDouble()); put("side", side.name)
        put("width", panelWidth.toDouble()); put("page", page)
    }

    companion object {
        fun fromJson(o: JSONObject, fallbackName: String) = Preset(
            name = o.optString("name", fallbackName),
            navApp = o.optString("nav"),
            musicApp = o.optString("music"),
            ratio = o.optDouble("ratio", 0.6).toFloat().coerceIn(0.3f, 0.7f),
            side = runCatching { PanelSide.valueOf(o.optString("side")) }.getOrDefault(PanelSide.LEFT),
            panelWidth = o.optDouble("width", 0.28).toFloat().coerceIn(0.2f, 0.45f),
            page = o.optInt("page", 0),
        )
    }
}

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("split", Context.MODE_PRIVATE)

    private fun put(k: String, v: Any) = sp.edit().apply {
        when (v) {
            is String -> putString(k, v); is Int -> putInt(k, v)
            is Boolean -> putBoolean(k, v); is Float -> putFloat(k, v)
            is Long -> putLong(k, v)
        }
    }.apply()

    // ---------- عام ----------

    var demo: Boolean
        get() = sp.getBoolean("demo", false)
        set(v) = put("demo", v)

    /** يفتح التطبيقين تلقائيًا عند الرجوع للشاشة الرئيسية. */
    var autoSplit: Boolean
        get() = sp.getBoolean("auto_split", true)
        set(v) = put("auto_split", v)

    var autostart: Boolean
        get() = sp.getBoolean("autostart", true)
        set(v) = put("autostart", v)

    /** الكرة العائمة (قائمة سريعة وعداد سرعة). */
    var ball: Boolean
        get() = sp.getBoolean("ball", true)
        set(v) = put("ball", v)

    var ballX: Int
        get() = sp.getInt("ball_x", 24)
        set(v) = put("ball_x", v)

    var ballY: Int
        get() = sp.getInt("ball_y", 200)
        set(v) = put("ball_y", v)

    /** لوحة المعلومات كنافذة عائمة فوق التطبيقات بدل الشاشة الرئيسية. */
    var floatingPanel: Boolean
        get() = sp.getBoolean("floating_panel", false)
        set(v) = put("floating_panel", v)

    /** وضع فتح النوافذ: عائمة بحدود (5)، تقسيم أندرويد (3)، أو كامل (1). */
    var windowMode: Int
        get() = sp.getInt("window_mode", AppLauncher.MODE_FREEFORM)
        set(v) = put("window_mode", v)

    /** 0 = عقارب، 1 = رقمية */
    var clockStyle: Int
        get() = sp.getInt("clock_style", 0)
        set(v) = put("clock_style", v)

    // ---------- الإعدادات المسبقة ----------

    var presetIndex: Int
        get() = sp.getInt("preset", 0).coerceIn(0, PRESETS - 1)
        set(v) = put("preset", v.coerceIn(0, PRESETS - 1))

    fun presets(): List<Preset> {
        val arr = runCatching { JSONArray(sp.getString("presets", "[]")) }.getOrDefault(JSONArray())
        return (0 until PRESETS).map { i ->
            val o = arr.optJSONObject(i)
            if (o != null) Preset.fromJson(o, "إعداد ${i + 1}") else Preset("إعداد ${i + 1}")
        }
    }

    fun savePreset(i: Int, p: Preset) {
        val list = presets().toMutableList()
        list[i.coerceIn(0, PRESETS - 1)] = p
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        put("presets", arr.toString())
    }

    val preset: Preset get() = presets()[presetIndex]

    fun updatePreset(f: (Preset) -> Preset) = savePreset(presetIndex, f(preset))

    var autoDayNight: Boolean
        get() = sp.getBoolean("auto_day_night", false)
        set(v) = put("auto_day_night", v)

    var dayPreset: Int
        get() = sp.getInt("day_preset", 0)
        set(v) = put("day_preset", v)

    var nightPreset: Int
        get() = sp.getInt("night_preset", 1)
        set(v) = put("night_preset", v)

    // ---------- الموقع والطقس ----------

    /** آخر موقع معروف أو الموقع اليدوي (الرياض افتراضيًا). */
    var lat: Float
        get() = sp.getFloat("lat", 24.7136f)
        set(v) = put("lat", v)

    var lon: Float
        get() = sp.getFloat("lon", 46.6753f)
        set(v) = put("lon", v)

    var weatherJson: String
        get() = sp.getString("weather_json", "") ?: ""
        set(v) = put("weather_json", v)

    var weatherAt: Long
        get() = sp.getLong("weather_at", 0L)
        set(v) = put("weather_at", v)

    // ---------- الوقود ----------

    var tankLiters: Float
        get() = sp.getFloat("tank", 80f)
        set(v) = put("tank", v)

    var refuelOdo: Float
        get() = sp.getFloat("refuel_odo", -1f)
        set(v) = put("refuel_odo", v)

    var refuelFuel: Float
        get() = sp.getFloat("refuel_fuel", 0f)
        set(v) = put("refuel_fuel", v)

    var lastFuel: Float
        get() = sp.getFloat("last_fuel", Float.NaN)
        set(v) = put("last_fuel", v)

    var refuelAt: Long
        get() = sp.getLong("refuel_at", 0L)
        set(v) = put("refuel_at", v)

    // ---------- التحديث ----------

    var lastUpdateCheck: Long
        get() = sp.getLong("last_update_check", 0L)
        set(v) = put("last_update_check", v)

    var autoUpdateCheck: Boolean
        get() = sp.getBoolean("auto_update", true)
        set(v) = put("auto_update", v)

    companion object {
        const val PRESETS = 7
    }
}
