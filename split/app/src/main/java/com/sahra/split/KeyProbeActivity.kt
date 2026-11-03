package com.sahra.split

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * مستكشف المفاتيح: يقرأ قائمة مفاتيح مرشحة من خدمة gwm_adapter كل ثانية ويعرض قيمها.
 * الغرض معرفة أي المفاتيح تجيب في هذه السيارة (الغيار، الإطارات، مثبت السرعة، وضع القيادة…).
 * المفتاح الذي تغيّرت قيمته في آخر خمس ثوانٍ يظهر بالكهرماني.
 */
class KeyProbeActivity : Activity() {

    companion object {
        /** مفاتيح مرشحة. ما لا تعرفه السيارة يبقى بلا قيمة. */
        val CANDIDATES = listOf(
            // الحركة والمحرك
            "car.basic.vehicle_speed", "car.basic.ivivehiclespeed", "car.basic.engine_speed",
            "car.off_road_info.engine_rpm", "car.basic.average_speed",
            // الغيار
            "car.basic.current_gear", "car.basic.gear_status", "car.basic.gear_position",
            "car.off_road_info.driving_mod_man_shift",
            // الوقود والمسافات
            "car.basic.remain_fuel_percentage", "car.basic.remain_odometer", "car.basic.total_odometer",
            "car.basic.odometer", "car.basic.accumulated_odometer", "car.basic.trip_odometer",
            "car.basic.cur_journey_odometer", "car.basic.cur_journey_time", "car.basic.cur_journey_avg_fuel",
            "car.basic.average_fuel_consumption", "car.basic.avg_fuel_consumption",
            "car.basic.instant_fuel_consumption", "car.basic.master.oil.tank.level",
            // الحرارة والكهرباء
            "car.basic.coolant_temp", "car.basic.outside_temp", "car.basic.inside_temp",
            "car.basic.battery_voltage", "car.basic.battery_power_level", "car.basic.engne_oil_level_notify",
            "car.basic.baropressure",
            // الإطارات
            "car.basic.tire_pressure_fl", "car.basic.tire_pressure_fr",
            "car.basic.tire_pressure_rl", "car.basic.tire_pressure_rr",
            "car.off_road_info.tire_pressure_fl", "car.off_road_info.tire_pressure_fr",
            "car.off_road_info.tire_pressure_rl", "car.off_road_info.tire_pressure_rr",
            "car.basic.tire_temp_fl", "car.basic.tire_temp_fr", "car.basic.tire_temp_rl", "car.basic.tire_temp_rr",
            "car.basic.tpms_status",
            // مثبت السرعة
            "car.basic.cruise_status", "car.basic.cruise_speed", "car.basic.acc_status", "car.basic.acc_set_speed",
            // وضع القيادة والطرق الوعرة
            "car.off_road_info.drive_mode", "car.basic.drive_mode", "car.off_road_info.cur_lvl_mode",
            "car.off_road_info.four_low_mode_state", "car.off_road_info.tod.systemoper_mode",
            "car.off_road_info.expert_mode.sw_state", "car.off_road_info.susp_lvl_adj_state",
            "car.off_road_info.front.edl.state", "car.off_road_info.middle.edl.state", "car.off_road_info.rear.edl.state",
            "car.off_road_info.cco.status", "car.off_road_setting.coo_enable",
            "car.off_road_info.engine_torque", "car.off_road_info.engine_flywheel_torque",
            "car.off_road_info.expected_torque",
            // الاتجاه والميلان
            "car.basic.steering_wheel_angle", "car.off_road_info.pitch_angle", "car.off_road_info.roll_angle",
            "car.off_road_info.compass_direction", "car.off_road_info.altitude", "car.basic.altitude.status",
            "car.basic.vin",
        )
        private const val CHUNK = 20
        private const val FRESH_MS = 5_000L
    }

    private val main = Handler(Looper.getMainLooper())
    private val keys = CANDIDATES.toMutableList()
    private val values = HashMap<String, String?>()
    private val changedAt = HashMap<String, Long>()
    private val rows = HashMap<String, TextView>()
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private var onlyAnswering = false
    @Volatile private var running = false
    @Volatile private var worker: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Type.init(this)
        window.statusBarColor = Palette.gantry
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        build()
    }

    override fun onResume() {
        super.onResume()
        running = true
        worker?.interrupt()
        worker = Thread(::loop, "key-probe").apply { isDaemon = true; start() }
    }

    override fun onPause() {
        running = false
        worker?.interrupt()
        worker = null
        super.onPause()
    }

    private fun build() {
        val scroll = ScrollView(this).apply { background = AsphaltDrawable(this@KeyProbeActivity); isVerticalScrollBarEnabled = false }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(40))
        }
        scroll.addView(body)
        body.addView(header(R.drawable.ic_stats, "مستكشف مفاتيح السيارة", Palette.blue) { finish() })

        status = label("…", 16f, Palette.muted).apply { setPadding(0, dp(12), 0, dp(4)) }
        body.addView(status)
        body.addView(label("الشاشات: ${displays()}", 14f, Palette.muted).apply { setPadding(0, 0, 0, dp(12)) })
        body.addView(label("حرّك السيارة أو غيّر الغيار أو شغّل مثبت السرعة: المفتاح الذي يتغيّر يظهر بالكهرماني.", 15f, Palette.white)
            .apply { setPadding(0, 0, 0, dp(12)) })

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun action(text: String, color: Int, onClick: () -> Unit) {
            actions.addView(signButton(null, text, color, onClick),
                LinearLayout.LayoutParams(WRAP_CONTENT, dp(64)).apply { marginEnd = dp(10) })
        }
        action("نسخ النتائج", Palette.blue) { copy() }
        action("حفظ في ملف", Palette.blue) { saveFile() }
        action("إضافة مفتاح", Palette.green) { addKey() }
        action("المجيبة فقط", Palette.brown) { onlyAnswering = !onlyAnswering; refresh() }
        body.addView(actions, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(16) })

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(list)
        keys.forEach { addRow(it) }
        setContentView(scroll)
    }

    private fun addRow(key: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            minimumHeight = dp(48)
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = rounded(Palette.shoulder, dp(10f))
        }
        row.addView(label(key, 15f, Palette.muted), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val v = label("—", 18f, Palette.muted, bold = true, digitsFace = true)
        row.addView(v, LinearLayout.LayoutParams(dp(260), WRAP_CONTENT))
        rows[key] = v
        list.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(6) })
    }

    private fun loop() {
        val me = Thread.currentThread()
        while (running && worker === me) {
            val snapshot = synchronized(keys) { keys.toList() }
            val read = readAll(snapshot)
            // الخرائط تُعدَّل على الخيط الرئيسي فقط
            main.post {
                if (isFinishing) return@post
                val now = System.currentTimeMillis()
                if (read != null) {
                    snapshot.forEachIndexed { i, k ->
                        val v = read[i]?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
                        if (values.containsKey(k) && values[k] != v) changedAt[k] = now
                        values[k] = v
                    }
                }
                refresh()
                setStatus(read != null)
            }
            try { Thread.sleep(1000) } catch (e: InterruptedException) { return }
        }
    }

    /** يقرأ على دفعات، وإن فشلت دفعة يقرأ مفاتيحها واحدًا واحدًا حتى لا يُسقط مفتاحٌ سيئ الباقي. */
    private fun readAll(all: List<String>): Array<String?>? {
        if (!GwmAdapter.isAvailable()) return null
        val out = arrayOfNulls<String>(all.size)
        all.chunked(CHUNK).forEachIndexed { c, chunk ->
            val r = GwmAdapter.readKeys(packageName, chunk.toTypedArray())
            if (r != null) {
                chunk.indices.forEach { out[c * CHUNK + it] = r.getOrNull(it) }
            } else {
                chunk.forEachIndexed { i, k ->
                    out[c * CHUNK + i] = GwmAdapter.readKeys(packageName, arrayOf(k))?.getOrNull(0)
                }
            }
        }
        return out
    }

    private fun refresh() {
        val now = System.currentTimeMillis()
        rows.forEach { (k, tv) ->
            val v = values[k]
            val fresh = (changedAt[k] ?: 0L) > now - FRESH_MS
            tv.text = v ?: "—"
            tv.setTextColor(when {
                fresh -> Palette.amber
                v != null -> Palette.white
                else -> Palette.muted
            })
            (tv.parent as? View)?.visibility = if (onlyAnswering && v == null) View.GONE else View.VISIBLE
        }
    }

    private fun setStatus(ok: Boolean) {
        val answering = values.values.count { it != null }
        status.text = if (ok) "الخدمة تجيب · $answering من ${rows.size} مفتاحًا لها قيمة"
        else "خدمة gwm_adapter لا تجيب على هذا الجهاز"
        status.setTextColor(if (ok) Palette.white else Palette.amber)
    }

    private fun displays(): String {
        val dm = getSystemService(DisplayManager::class.java) ?: return "—"
        return dm.displays.joinToString("، ") { d ->
            val m = android.util.DisplayMetrics().also { @Suppress("DEPRECATION") d.getRealMetrics(it) }
            val flags = buildList {
                if (d.flags and Display.FLAG_PRIVATE != 0) add("خاصة")
                if (d.flags and Display.FLAG_PRESENTATION != 0) add("عرض")
                if (d.flags and Display.FLAG_SECURE != 0) add("آمنة")
            }.joinToString("/")
            "${d.displayId} ${m.widthPixels}×${m.heightPixels}" + if (flags.isNotEmpty()) " ($flags)" else ""
        }
    }

    private fun report(): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val sb = StringBuilder("Sahra Split key probe $time\nversion ${Updater.currentName(this)}\ndisplays: ${displays()}\n\n")
        rows.keys.sorted().forEach { k -> sb.append(k).append(" = ").append(values[k] ?: "—").append('\n') }
        return sb.toString()
    }

    private fun copy() {
        val cm = getSystemService(ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newPlainText("keys", report()))
        Toast.makeText(this, "نُسخت النتائج", Toast.LENGTH_SHORT).show()
    }

    private fun saveFile() {
        val dir = getExternalFilesDir(null) ?: filesDir
        val name = "keys-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
        val f = File(dir, name)
        val ok = runCatching { f.writeText(report()) }.isSuccess
        Toast.makeText(this, if (ok) "حُفظ في ${f.absolutePath}" else "تعذّر الحفظ", Toast.LENGTH_LONG).show()
    }

    private fun addKey() {
        val input = EditText(this).apply { setText("car.basic."); setTextColor(Palette.white); setSelection(text.length) }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("اسم المفتاح")
            .setView(input)
            .setPositiveButton("إضافة") { _, _ ->
                val k = input.text.toString().trim()
                if (k.isNotEmpty() && !rows.containsKey(k)) {
                    synchronized(keys) { keys.add(0, k) }
                    addRow(k)
                    list.getChildAt(list.childCount - 1)?.let { list.removeView(it); list.addView(it, 0) }
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
}
