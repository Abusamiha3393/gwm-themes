package com.sahra.split

import android.content.Context
import org.json.JSONObject

/**
 * يترجم gear_status الخام إلى حرف (P, R, N, D, M).
 * القيم تختلف بين الطرازات، فيتعلّمها السائق مرة واحدة: يضع الغيار على وضع ويضغط زره.
 * المعروف من هافال H9 (تجربة 2026-11-04): 0 = N، 2 = D، 3 = P، 5 = M. ما لم يُتعلَّم يُستنتج من السرعة والدوران.
 */
object Gears {
    val LABELS = listOf("P", "R", "N", "D", "M")
    private val DEFAULTS = mapOf(0 to "N", 2 to "D", 3 to "P", 5 to "M")

    fun map(context: Context): Map<Int, String> {
        val o = runCatching { JSONObject(Prefs(context).gearMap) }.getOrNull() ?: return DEFAULTS
        val m = HashMap<Int, String>(DEFAULTS)
        o.keys().forEach { k -> k.toIntOrNull()?.let { m[it] = o.optString(k) } }
        return m
    }

    /** يربط القيمة الخام الحالية بحرف، ويعيد الحرف الذي كان مربوطًا بها قبل ذلك (إن وُجد). */
    fun learn(context: Context, raw: Int, label: String): String? {
        val p = Prefs(context)
        val o = runCatching { JSONObject(p.gearMap) }.getOrDefault(JSONObject())
        val before = map(context)[raw]
        o.put(raw.toString(), label)
        p.gearMap = o.toString()
        return before?.takeIf { it != label }
    }

    fun reset(context: Context) { Prefs(context).gearMap = "" }

    /** الحرف الذي يُعرض، مع رقم الغيار في D و M إن عُرف (D5، M1). */
    fun label(context: Context, s: VehicleState): String {
        val base = if (s.gearRaw >= 0) map(context)[s.gearRaw] else null
        val letter = base ?: when {
            s.rpm < 100f && s.speed < 1f -> "P"
            s.speed < 1f -> "N"
            else -> "D"
        }
        return if ((letter == "D" || letter == "M") && s.gearNo > 0) "$letter${s.gearNo}" else letter
    }

    /** الحرف وحده بلا رقم، للثيمات التي تعرض P/R/N/D فقط. */
    fun letter(context: Context, s: VehicleState): String = label(context, s).take(1)
}
