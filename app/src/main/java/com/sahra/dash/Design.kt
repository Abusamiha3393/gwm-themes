package com.sahra.dash

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * تصميم العدادات الذي يضبطه السائق في المصمم.
 * الألوان الفارغة تعني «لون الثيم الأصلي».
 */
data class Design(
    val needleColor: String = "",
    val tickColor: String = "",
    val numberColor: String = "",
    val panelColor: String = "",
    val needleStyle: String = "classic",   // classic | thin | arrow
    val scale: Int = 100,                   // حجم العدادات 70..120٪
    val shift: Int = 0,                     // تحريك أفقي -20..20٪
    val swap: Boolean = false,              // تبديل مكان عداد السرعة والدورات
    val name: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("needleColor", needleColor); put("tickColor", tickColor)
        put("numberColor", numberColor); put("panelColor", panelColor)
        put("needleStyle", needleStyle); put("scale", scale); put("shift", shift)
        put("swap", swap); put("name", name)
    }

    companion object {
        val NEEDLES = listOf("classic" to "كلاسيكية", "thin" to "رفيعة", "arrow" to "سهم")

        val COLORS = listOf(
            "#F4E3C3", "#FFFFFF", "#E8A24A", "#E5583F", "#9CC46B",
            "#4FA3E8", "#A98BF0", "#F06EA9", "#2B2B2B", "#14100C",
        )

        fun fromJson(o: JSONObject?): Design {
            if (o == null) return Design()
            return Design(
                needleColor = o.optString("needleColor"),
                tickColor = o.optString("tickColor"),
                numberColor = o.optString("numberColor"),
                panelColor = o.optString("panelColor"),
                needleStyle = o.optString("needleStyle", "classic"),
                scale = o.optInt("scale", 100).coerceIn(70, 120),
                shift = o.optInt("shift", 0).coerceIn(-20, 20),
                swap = o.optBoolean("swap", false),
                name = o.optString("name"),
            )
        }
    }
}

/** حفظ التصميم الحالي والتصاميم المحفوظة بأسماء. */
class DesignStore(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("sahra_design", Context.MODE_PRIVATE)

    var current: Design
        get() = runCatching { Design.fromJson(JSONObject(sp.getString("current", "{}")!!)) }.getOrDefault(Design())
        set(v) = sp.edit().putString("current", v.toJson().toString()).apply()

    fun presets(): List<Design> = runCatching {
        val a = JSONArray(sp.getString("presets", "[]"))
        (0 until a.length()).map { Design.fromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun savePreset(d: Design) {
        val list = presets().filter { it.name != d.name } + d
        sp.edit().putString("presets", JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    fun deletePreset(name: String) {
        val list = presets().filter { it.name != name }
        sp.edit().putString("presets", JSONArray(list.map { it.toJson() }).toString()).apply()
    }
}

/** يجهّز البيانات التي تُرسل للثيم، ليستخدمها العرض والمعاينة معًا. */
object Payload {
    fun build(prefs: Prefs, design: Design, state: VehicleState = VehicleRepository.state): JSONObject {
        val data = state.toJson()
        val m = MediaTracker.current
        data.put("media", JSONObject().apply {
            put("title", m.title); put("artist", m.artist); put("playing", m.playing)
            put("app", m.app); put("art", m.artDataUrl)
        })
        data.put("style", prefs.styleJson().put("design", design.toJson()))
        data.put("alerts", if (prefs.alerts) Alerts.compute(state) else JSONArray())
        data.put("layout", prefs.layout.name.lowercase())
        data.put("theme", prefs.themeId)
        data.put("time", System.currentTimeMillis())
        return data
    }

    fun script(data: JSONObject) =
        "(function(d){window.SahraData=d;if(window.__sahra)window.__sahra(d);else if(window.onSahraData)window.onSahraData(d);})($data);"
}
