package com.sahra.dash

import org.json.JSONArray
import org.json.JSONObject

/** تنبيهات بسيطة تُعرض في أعلى كل ثيم. level: warn أو danger */
object Alerts {
    fun compute(s: VehicleState): JSONArray {
        val out = JSONArray()
        fun add(id: String, text: String, level: String) =
            out.put(JSONObject().put("id", id).put("text", text).put("level", level))

        if (!s.fuelPercent.isNaN()) {
            when {
                s.fuelPercent <= 7f -> add("fuel", "الوقود منخفض جدًا", "danger")
                s.fuelPercent <= 12f -> add("fuel", "الوقود منخفض", "warn")
            }
        }
        if (!s.coolantTemp.isNaN()) {
            when {
                s.coolantTemp >= 115f -> add("coolant", "حرارة المحرك مرتفعة جدًا — توقف بأمان", "danger")
                s.coolantTemp >= 106f -> add("coolant", "حرارة المحرك مرتفعة", "warn")
            }
        }
        if (!s.batteryVoltage.isNaN() && s.batteryVoltage > 1f) {
            // أثناء دوران المحرك يجب أن يشحن المولد فوق 13 فولت تقريبًا
            if (s.rpm > 600f && s.batteryVoltage < 12.6f) add("battery", "البطارية لا تشحن", "warn")
            else if (s.batteryVoltage < 11.8f) add("battery", "جهد البطارية منخفض", "warn")
        }
        if (!s.pitch.isNaN() && kotlin.math.abs(s.pitch) >= 30f) add("pitch", "ميلان أمامي خطر", "danger")
        if (!s.roll.isNaN() && kotlin.math.abs(s.roll) >= 30f) add("roll", "ميلان جانبي خطر", "danger")
        return out
    }
}
