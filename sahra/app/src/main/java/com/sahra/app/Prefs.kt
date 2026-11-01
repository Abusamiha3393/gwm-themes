package com.sahra.app

import android.content.Context

/** إعدادات التطبيق. كل ميزة جديدة تضيف إعداداتها هنا في قسم خاص بها. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("sahra", Context.MODE_PRIVATE)

    private fun put(k: String, v: Any) = sp.edit().apply {
        when (v) {
            is String -> putString(k, v); is Int -> putInt(k, v)
            is Boolean -> putBoolean(k, v); is Float -> putFloat(k, v)
            is Long -> putLong(k, v)
        }
    }.apply()

    // ---------- عام ----------

    /** بيانات وهمية متحركة بدل بيانات السيارة (للتجربة). */
    var demo: Boolean
        get() = sp.getBoolean("demo", false)
        set(v) = put("demo", v)

    var autostart: Boolean
        get() = sp.getBoolean("autostart", true)
        set(v) = put("autostart", v)

    // ---------- السيارة ----------

    /** معامل تصحيح السرعة: عداد السيارة يزيد عادة عن السرعة الحقيقية بقليل. */
    var speedFactor: Float
        get() = sp.getFloat("speed_factor", 1f)
        set(v) = put("speed_factor", v)

    var tankLiters: Int
        get() = sp.getInt("tank", 80)
        set(v) = put("tank", v)

    // ---------- التحديث ----------

    var autoUpdate: Boolean
        get() = sp.getBoolean("auto_update", true)
        set(v) = put("auto_update", v)

    var lastUpdateCheck: Long
        get() = sp.getLong("last_update_check", 0L)
        set(v) = put("last_update_check", v)
}
