package com.sahra.dash

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * معلومات السيارة والجهاز، وأداة تشخيص تقرأ كل مفاتيح خدمة GWM كما هي.
 * نتيجة التشخيص يمكن نسخها أو مشاركتها لمعرفة ما تدعمه شاشة سيارتك بالضبط.
 */
class VehicleInfoActivity : Activity(), VehicleRepository.Listener {

    private val DIAG_KEYS = arrayOf(
        "car.basic.vin", "car.basic.ivivehiclespeed", "car.basic.vehicle_speed", "car.basic.engine_speed",
        "car.basic.coolant_temp", "car.basic.steering_wheel_angle", "car.basic.current_gear", "car.basic.gear_status",
        "car.basic.remain_fuel_percentage", "car.basic.remain_odometer", "car.basic.odometer", "car.basic.total_odometer",
        "car.basic.accumulated_odometer", "car.basic.cur_journey_odometer", "car.basic.battery_voltage",
        "car.basic.battery_power_level", "car.basic.outside_temp", "car.basic.inside_temp",
        "car.basic.master.oil.tank.level", "car.basic.engne_oil_level_notify", "car.basic.baropressure",
        "car.basic.altitude.status", "car.basic.tpms_status",
        "car.off_road_info.cco.status", "car.off_road_setting.coo_enable", "car.off_road_info.front.edl.state",
        "car.off_road_info.rear.edl.state", "car.off_road_info.middle.edl.state", "car.off_road_info.susp_lvl_adj_state",
        "car.off_road_info.cur_lvl_mode", "car.off_road_info.tod.systemoper_mode", "car.off_road_info.expert_mode.sw_state",
        "car.off_road_info.engine_flywheel_torque", "car.off_road_info.expected_torque", "car.off_road_info.four_low_mode_state",
        "car.off_road_info.driving_mod_man_shift", "car.off_road_info.pitch_angle", "car.off_road_info.roll_angle",
        "car.off_road_info.compass_direction", "car.off_road_info.altitude", "car.off_road_info.tire_pressure_fl",
        "car.off_road_info.engine_torque", "car.off_road_info.engine_rpm", "car.off_road_info.drive_mode",
        "sys.other.keyevent_notify",
    )

    private lateinit var prefs: Prefs
    private lateinit var infoText: TextView
    private lateinit var diagText: TextView
    private var lastDiag = ""

    private val bg = Color.parseColor("#14100C")
    private val card = Color.parseColor("#211A14")
    private val accent = Color.parseColor("#E8A24A")
    private val fg = Color.parseColor("#F4E3C3")
    private val muted = Color.parseColor("#A8957A")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(32))
        }
        root.addView(text("معلومات السيارة", 26f, fg, true))

        infoText = text("", 16f, fg).apply { setLineSpacing(0f, 1.35f) }
        root.addView(box(infoText))

        root.addView(text("التشخيص", 18f, accent, true).apply { setPadding(0, dp(18), 0, dp(4)) })
        root.addView(text("يقرأ كل مفاتيح خدمة السيارة كما هي. أرسل النتيجة للمطوّر عند وجود مشكلة في القراءة.", 14f, muted))
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(button("ابدأ التشخيص", true) { runDiagnostics() }, weighted())
        buttons.addView(button("نسخ") { copy() }, weighted(true))
        buttons.addView(button("مشاركة") { share() }, weighted(true))
        root.addView(buttons)
        diagText = text("لم يُشغَّل بعد.", 13f, fg).apply { typeface = Typeface.MONOSPACE; setTextIsSelectable(true) }
        root.addView(box(diagText))

        setContentView(ScrollView(this).apply { setBackgroundColor(bg); addView(root, MATCH_PARENT, WRAP_CONTENT) })
    }

    override fun onResume() {
        super.onResume()
        VehicleRepository.forceDemo = prefs.demo
        VehicleRepository.start(packageName)
        VehicleRepository.addListener(this)
    }

    override fun onPause() {
        VehicleRepository.removeListener(this)
        if (!OverlayService.isRunning) VehicleRepository.stop()
        super.onPause()
    }

    override fun onState(state: VehicleState) {
        fun f(v: Float, unit: String, d: Int = 0) =
            if (v.isNaN()) "—" else String.format(Locale.US, "%.${d}f", v) + unit
        val size = OverlayService.screenSize(this)
        infoText.text = buildString {
            append("الاتصال: ").append(when {
                state.live -> "متصل بخدمة السيارة ✓"
                state.demo && prefs.demo -> "الوضع التجريبي"
                else -> "الخدمة غير متاحة"
            }).append('\n')
            append("رقم الهيكل: ${state.vin.ifEmpty { "—" }}\n")
            append("العداد الكلي: ${f(state.odometerKm, " كم")}   المدى: ${f(state.rangeKm, " كم")}\n")
            append("الوقود: ${f(state.fuelPercent, "٪")}   البطارية: ${f(state.batteryVoltage, " فولت", 1)} (${f(state.batteryLevel, "٪")})\n")
            append("حرارة المحرك: ${f(state.coolantTemp, "°")}   الخارج: ${f(state.outsideTemp, "°")}   الداخل: ${f(state.insideTemp, "°")}\n")
            append("زاوية المقود: ${f(state.steeringAngle, "°", 1)}\n")
            append("الجهاز: ${Build.MANUFACTURER} ${Build.MODEL} — أندرويد ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            append("الشاشة: ${size.x}×${size.y} بكسل، الكثافة ${resources.displayMetrics.densityDpi} dpi\n")
            append("إصدار التطبيق: ${runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "—"}")
        }
    }

    private fun runDiagnostics() {
        diagText.text = "جارٍ القراءة…"
        Thread {
            val sb = StringBuilder()
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            sb.append("Sahra Dash diagnostics $stamp\n")
            sb.append("Device: ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            val available = GwmAdapter.isAvailable()
            sb.append("gwm_adapter service: ${if (available) "FOUND" else "NOT FOUND"}\n\n")
            if (available) {
                // قراءة جماعية ثم فردية للمقارنة، لأن بعض الشاشات ترفض مفتاحًا واحدًا فتفشل الدفعة كلها
                val batch = GwmAdapter.readKeys(packageName, DIAG_KEYS)
                sb.append("Batch read: ${if (batch == null) "FAILED" else "${batch.size} values"}\n\n")
                DIAG_KEYS.forEachIndexed { i, key ->
                    val single = GwmAdapter.readKeys(packageName, arrayOf(key))?.firstOrNull()
                    val b = batch?.getOrNull(i)
                    sb.append(key).append(" = ").append(single ?: "<null>")
                    if (batch != null && b != single) sb.append("   (batch: ").append(b ?: "<null>").append(')')
                    sb.append('\n')
                }
            } else {
                sb.append("This screen does not expose the service to apps, or the app lacks permission.\n")
            }
            lastDiag = sb.toString()
            runOnUiThread { diagText.text = lastDiag }
        }.start()
    }

    private fun copy() {
        if (lastDiag.isEmpty()) { toast("شغّل التشخيص أولًا"); return }
        val cm = getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("Sahra diagnostics", lastDiag))
        toast("تم النسخ")
    }

    private fun share() {
        if (lastDiag.isEmpty()) { toast("شغّل التشخيص أولًا"); return }
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, lastDiag)
        runCatching { startActivity(Intent.createChooser(i, "مشاركة التشخيص")) }.onFailure { toast("لا يوجد تطبيق للمشاركة") }
    }

    // ---------------- عناصر الواجهة ----------------

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun box(child: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = GradientDrawable().apply { setColor(card); cornerRadius = dp(14).toFloat() }
        addView(child)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) }
    }

    private fun weighted(gap: Boolean = false) = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
        setMargins(if (gap) dp(8) else 0, dp(8), 0, dp(8))
    }

    private fun button(label: String, primary: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 15f
        setTextColor(if (primary) bg else fg)
        background = GradientDrawable().apply {
            setColor(if (primary) accent else Color.parseColor("#2E251C")); cornerRadius = dp(10).toFloat()
        }
        setOnClickListener { onClick() }
    }
}
