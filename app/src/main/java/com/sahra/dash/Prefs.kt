package com.sahra.dash

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("sahra", Context.MODE_PRIVATE)

    var themeId: String
        get() = sp.getString("theme", Themes.all.first().id) ?: Themes.all.first().id
        set(v) = sp.edit().putString("theme", v).apply()

    var autostart: Boolean
        get() = sp.getBoolean("autostart", true)
        set(v) = sp.edit().putBoolean("autostart", v).apply()

    var demo: Boolean
        get() = sp.getBoolean("demo", false)
        set(v) = sp.edit().putBoolean("demo", v).apply()

    /** رمز زر المقود الذي يُظهر ويُخفي العرض (0 = غير محدد). */
    var toggleKey: Int
        get() = sp.getInt("toggle_key", 0)
        set(v) = sp.edit().putInt("toggle_key", v).apply()

    /** رمز زر المقود الذي ينتقل للثيم التالي (0 = غير محدد). */
    var nextThemeKey: Int
        get() = sp.getInt("next_key", 0)
        set(v) = sp.edit().putInt("next_key", v).apply()

    var layout: OverlayLayout
        get() = OverlayLayout.entries.getOrElse(sp.getInt("layout", 0)) { OverlayLayout.FULL }
        set(v) = sp.edit().putInt("layout", v.ordinal).apply()
}

enum class OverlayLayout(val label: String) {
    FULL("ملء الشاشة"), LEFT("النصف الأيسر"), RIGHT("النصف الأيمن"), LEFT_THIRD("الثلث الأيسر"), RIGHT_THIRD("الثلث الأيمن")
}

data class Theme(val id: String, val name: String, val asset: String)

object Themes {
    val all = listOf(
        Theme("dune", "كثبان — عدادات كلاسيكية", "themes/dune.html"),
        Theme("trail", "مسار — الطرق الوعرة", "themes/trail.html"),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.first()
    fun next(id: String): Theme {
        val i = all.indexOfFirst { it.id == id }
        return all[(i + 1).mod(all.size)]
    }
}
