package com.sahra.dash

import android.content.Context
import org.json.JSONObject
import java.io.File

class Prefs(context: Context) {
    private val app = context.applicationContext
    private val sp = app.getSharedPreferences("sahra", Context.MODE_PRIVATE)

    private fun str(k: String, d: String) = sp.getString(k, d) ?: d
    private fun put(k: String, v: Any) = sp.edit().apply {
        when (v) {
            is String -> putString(k, v); is Int -> putInt(k, v)
            is Boolean -> putBoolean(k, v); is Float -> putFloat(k, v)
            is Long -> putLong(k, v)
        }
    }.apply()

    var lastUpdateCheck: Long
        get() = sp.getLong("last_update_check", 0L)
        set(v) = put("last_update_check", v)

    var autoUpdateCheck: Boolean
        get() = sp.getBoolean("auto_update", true)
        set(v) = put("auto_update", v)

    var themeId: String
        get() = str("theme", Themes.all.first().id)
        set(v) = put("theme", v)

    var autostart: Boolean
        get() = sp.getBoolean("autostart", true)
        set(v) = put("autostart", v)

    var demo: Boolean
        get() = sp.getBoolean("demo", false)
        set(v) = put("demo", v)

    /** رمز زر المقود الذي يُظهر ويُخفي العرض (0 = غير محدد). */
    var toggleKey: Int
        get() = sp.getInt("toggle_key", 0)
        set(v) = put("toggle_key", v)

    /** رمز زر المقود الذي ينتقل للثيم التالي (0 = غير محدد). */
    var nextThemeKey: Int
        get() = sp.getInt("next_key", 0)
        set(v) = put("next_key", v)

    var layout: OverlayLayout
        get() = OverlayLayout.entries.getOrElse(sp.getInt("layout", 0)) { OverlayLayout.FULL }
        set(v) = put("layout", v.ordinal)

    // ---------- تقسيم الشاشة ----------

    /** التطبيق الذي يُفتح في الجزء الفارغ بجانب العدادات (فارغ = لا شيء). */
    var panelApp: String
        get() = str("panel_app", "")
        set(v) = put("panel_app", v)

    var splitLeft: String
        get() = str("split_left", "")
        set(v) = put("split_left", v)

    var splitRight: String
        get() = str("split_right", "")
        set(v) = put("split_right", v)

    var splitRatio: Float
        get() = sp.getFloat("split_ratio", 0.5f)
        set(v) = put("split_ratio", v)

    // ---------- النافذة العائمة ----------

    /** يظهر عداد سرعة صغير عائم عندما تكون العدادات مخفية. */
    var miniGauge: Boolean
        get() = sp.getBoolean("mini_gauge", false)
        set(v) = put("mini_gauge", v)

    var miniX: Int
        get() = sp.getInt("mini_x", 40)
        set(v) = put("mini_x", v)

    var miniY: Int
        get() = sp.getInt("mini_y", 40)
        set(v) = put("mini_y", v)

    var floatingApp: String
        get() = str("floating_app", "")
        set(v) = put("floating_app", v)

    // ---------- مصمم العدادات ----------

    var accent: String
        get() = str("accent", Accents.all.first().hex)
        set(v) = put("accent", v)

    var speedMax: Int
        get() = sp.getInt("speed_max", 220)
        set(v) = put("speed_max", v)

    var rpmMax: Int
        get() = sp.getInt("rpm_max", 7000)
        set(v) = put("rpm_max", v)

    var mph: Boolean
        get() = sp.getBoolean("mph", false)
        set(v) = put("mph", v)

    // ---------- الخلفية ----------

    val wallpaperFile: File get() = File(app.filesDir, "wallpaper.jpg")

    /** يتغير مع كل خلفية جديدة حتى لا يعرض الثيم نسخة قديمة من الذاكرة. */
    var wallpaperVersion: Int
        get() = sp.getInt("wallpaper_v", 0)
        set(v) = put("wallpaper_v", v)

    var wallpaperDim: Int
        get() = sp.getInt("wallpaper_dim", 55)
        set(v) = put("wallpaper_dim", v)

    // ---------- التنبيهات ----------

    var alerts: Boolean
        get() = sp.getBoolean("alerts", true)
        set(v) = put("alerts", v)

    /** إعدادات الشكل كما تصل إلى الثيمات في d.style */
    fun styleJson(): JSONObject = JSONObject().apply {
        put("accent", accent)
        put("speedMax", speedMax)
        put("rpmMax", rpmMax)
        put("units", if (mph) "mph" else "kmh")
        val wp = wallpaperFile
        put("wallpaper", if (wp.exists()) "file://${wp.absolutePath}?v=$wallpaperVersion" else JSONObject.NULL)
        put("wallpaperDim", wallpaperDim / 100.0)
    }
}

enum class OverlayLayout(val label: String, val fraction: Float, val rightSide: Boolean) {
    FULL("ملء الشاشة", 1f, false),
    LEFT("النصف الأيسر", 0.5f, false),
    RIGHT("النصف الأيمن", 0.5f, true),
    LEFT_THIRD("الثلث الأيسر", 1f / 3, false),
    RIGHT_THIRD("الثلث الأيمن", 1f / 3, true),
}

data class Theme(val id: String, val name: String, val asset: String)

object Themes {
    val all = listOf(
        Theme("dune", "كثبان — عدادات كلاسيكية", "themes/dune.html"),
        Theme("trail", "مسار — الطرق الوعرة", "themes/trail.html"),
        Theme("pulse", "نبض — رقمي", "themes/pulse.html"),
        Theme("journey", "رحلة — معلومات وموسيقى", "themes/journey.html"),
        Theme("horizon", "أفق — قوس عريض", "themes/horizon.html"),
        Theme("track", "مضمار — أضواء التبديل", "themes/track.html"),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.first()
    fun next(id: String): Theme {
        val i = all.indexOfFirst { it.id == id }
        return all[(i + 1).mod(all.size)]
    }
}

data class Accent(val name: String, val hex: String)

object Accents {
    val all = listOf(
        Accent("رملي", "#E8A24A"),
        Accent("أحمر", "#E5583F"),
        Accent("أخضر", "#9CC46B"),
        Accent("أزرق", "#4FA3E8"),
        Accent("أبيض", "#EDEDED"),
        Accent("بنفسجي", "#A98BF0"),
    )
}
