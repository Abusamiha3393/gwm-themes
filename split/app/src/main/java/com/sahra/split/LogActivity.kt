package com.sahra.split

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

/** السجل: الرحلات، التعبئات، التذكيرات، الإحصاءات. */
class LogActivity : Activity() {

    companion object {
        val TABS = listOf("الرحلات", "التعبئات", "التذكيرات", "الإحصاءات")
        private const val DAY_MS = 24 * 60 * 60 * 1000L
    }

    private var tab = 0
    private lateinit var list: LinearLayout
    private lateinit var tabsRow: LinearLayout
    private val dateFmt = SimpleDateFormat("EEEE d/M · HH:mm", Locale("ar"))
    private val dayFmt = SimpleDateFormat("d MMMM yyyy", Locale("ar"))
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        tab = intent.getIntExtra("tab", 0).coerceIn(0, TABS.size - 1)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.bg)
            setPadding(dp(20), dp(14), dp(20), dp(14))
        }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(label("السجل", 24f, Palette.fg, true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        head.addView(pill("رجوع") { finish() })
        root.addView(head)
        tabsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(tabsRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(10), 0, dp(20)) }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        tabsRow.removeAllViews()
        TABS.forEachIndexed { i, name ->
            val on = i == tab
            tabsRow.addView(TextView(this).apply {
                text = name
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(if (on) Palette.bg else Palette.fg)
                background = rounded(if (on) Palette.accent else Palette.card, dp(14f))
                setPadding(dp(8), dp(10), dp(8), dp(10))
                setOnClickListener { tab = i; render() }
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
        }
        list.removeAllViews()
        when (tab) {
            0 -> trips()
            1 -> refuels()
            2 -> reminders()
            else -> stats()
        }
    }

    // ---------------- أدوات ----------------

    private fun card(title: String, line1: String, line2: String = "", titleColor: Int = Palette.fg): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Palette.card, dp(12f))
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        c.addView(label(title, 16f, titleColor, true))
        if (line1.isNotEmpty()) c.addView(label(line1, 15f, Palette.fg).apply { setPadding(0, dp(4), 0, 0) })
        if (line2.isNotEmpty()) c.addView(label(line2, 13f, Palette.muted).apply { setPadding(0, dp(2), 0, 0) })
        list.addView(c, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })
        return c
    }

    private fun empty(text: String) {
        list.addView(label(text, 15f, Palette.muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(40), 0, dp(40)) },
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun actions(vararg items: Pair<String, () -> Unit>) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        items.forEach { (t, f) -> row.addView(pill(t, f), LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) }) }
        list.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })
    }

    private fun confirm(msg: String, yes: () -> Unit) {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setMessage(msg)
            .setPositiveButton("نعم") { _, _ -> yes(); render() }
            .setNegativeButton("لا", null)
            .show()
    }

    private fun duration(sec: Long): String {
        val m = sec / 60
        return if (m < 60) "$m د" else "${m / 60} س ${m % 60} د"
    }

    // ---------------- الرحلات ----------------

    private fun trips() {
        val tank = Prefs(this).tankLiters
        val all = Logbook.trips(this)
        if (all.isEmpty()) {
            empty("لا توجد رحلات بعد.\nتُسجَّل الرحلة تلقائيًا عند التحرك، وتنتهي بعد إطفاء المحرك دقيقتين أو التوقف 10 دقائق.")
            return
        }
        actions("حذف الكل" to { confirm("حذف كل الرحلات المحفوظة؟") { Logbook.clearTrips(this) } })
        all.forEach { t ->
            val title = dateFmt.format(Date(t.start)) + " ← " + timeFmt.format(Date(t.end)) + if (!t.done) "  · جارية" else ""
            val line1 = "${num(t.km, 1)} كم · ${duration(t.driveSec)} · متوسط ${num(t.avgSpeed)} كم/س · أقصى ${num(t.maxSpeed)}"
            val liters = t.liters(tank)
            val cons = if (liters.isNaN() || t.km < 5f) Float.NaN else liters / t.km * 100f
            val line2 = "العداد ${num(t.startOdo)} ← ${num(t.endOdo)} · وقود ${num(liters, 1)} لتر · ${num(cons, 1)} ل/100 كم"
            card(title, line1, line2, if (t.done) Palette.fg else Palette.accent).setOnLongClickListener {
                confirm("حذف هذه الرحلة؟") { Logbook.deleteTrip(this, t.start) }; true
            }
        }
        list.addView(label("اضغط مطولًا على رحلة لحذفها. يُحفظ آخر ${Logbook.MAX_TRIPS} رحلة.", 13f, Palette.muted))
    }

    // ---------------- التعبئات ----------------

    private fun refuels() {
        val all = Logbook.refuels(this)
        if (all.isEmpty()) {
            empty("لا توجد تعبئات بعد.\nتُرصد التعبئة تلقائيًا عندما ترتفع نسبة الوقود 5% أو أكثر.")
            return
        }
        actions("حذف الكل" to { confirm("حذف كل التعبئات المحفوظة؟") { Logbook.clearRefuels(this) } })
        all.forEach { r ->
            val line1 = "${num(r.fromPct)}% ← ${num(r.toPct)}% · نحو ${num(r.liters)} لتر · العداد ${num(r.odo)} كم"
            val line2 = if (r.prevConsumption.isNaN()) "استهلاك الخزان السابق: غير كافٍ للحساب"
            else "الخزان السابق: ${num(r.prevKm)} كم بمعدل ${num(r.prevConsumption, 1)} ل/100 كم"
            card(dateFmt.format(Date(r.at)), line1, line2).setOnLongClickListener {
                confirm("حذف هذه التعبئة؟") { Logbook.deleteRefuel(this, r.at) }; true
            }
        }
        list.addView(label("اللترات تقديرية من نسبة الوقود وسعة الخزان (${num(Prefs(this).tankLiters)} لتر، تُغيَّر من الإعدادات).", 13f, Palette.muted))
    }

    // ---------------- التذكيرات ----------------

    private fun reminders() {
        val all = Logbook.reminders(this)
        actions("+ تذكير جديد" to { editReminder(null) })
        if (all.isEmpty()) {
            empty("لا توجد تذكيرات.\nمثال: تغيير الزيت بعد 5000 كم، أو تجديد التأمين بتاريخ محدد.")
            return
        }
        val odo = VehicleRepository.state.odometerKm
        all.sortedBy { if (it.due(odo)) 0 else 1 }.forEach { r ->
            val due = r.due(odo)
            val detail = if (r.byOdo) {
                val left = if (odo.isNaN()) Float.NaN else (r.target - odo).toFloat()
                "عند العداد ${num(r.target.toFloat())} كم" + if (due) " · حان الموعد" else " · بقي ${num(left)} كم"
            } else {
                val days = ((r.target - System.currentTimeMillis()) / DAY_MS).toLong()
                "بتاريخ ${dayFmt.format(Date(r.target.toLong()))}" + if (due) " · حان الموعد" else " · بقي $days يوم"
            }
            card((if (due) "⚠ " else "") + r.text, detail, "", if (due) Palette.warn else Palette.fg).setOnClickListener {
                AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(r.text)
                    .setItems(arrayOf("تعديل", "حذف")) { _, w ->
                        if (w == 0) editReminder(r) else confirm("حذف التذكير؟") { Logbook.deleteReminder(this, r.id) }
                    }.show()
            }
        }
        list.addView(label("يصلك إشعار عند حلول الموعد. الحد الأقصى ${Logbook.MAX_REMINDERS} تذكيرًا.", 13f, Palette.muted))
    }

    private fun editReminder(old: Reminder?) {
        val odo = VehicleRepository.state.odometerKm
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val text = EditText(this).apply { hint = "نص التذكير"; setText(old?.text ?: ""); setTextColor(Palette.fg) }
        box.addView(text)
        val group = RadioGroup(this)
        val kinds = listOf("بعد عدد من الكيلومترات", "عند قراءة عداد محددة", "بعد عدد من الأيام")
        kinds.forEachIndexed { i, k ->
            group.addView(RadioButton(this).apply { this.text = k; id = 100 + i; setTextColor(Palette.fg) })
        }
        group.check(if (old == null) 100 else if (old.byOdo) 101 else 102)
        box.addView(group)
        val value = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Palette.fg)
            hint = "الرقم"
            if (old != null) setText(if (old.byOdo) old.target.roundToLong().toString()
            else ((old.target - System.currentTimeMillis()) / DAY_MS).toLong().coerceAtLeast(0).toString())
        }
        box.addView(value)
        box.addView(label("العداد الحالي: ${num(odo)} كم", 13f, Palette.muted))
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(if (old == null) "تذكير جديد" else "تعديل التذكير")
            .setView(box)
            .setPositiveButton("حفظ") { _, _ ->
                val t = text.text.toString().trim()
                val n = value.text.toString().toDoubleOrNull()
                if (t.isEmpty() || n == null) { toast("اكتب النص والرقم"); return@setPositiveButton }
                val kind = group.checkedRadioButtonId - 100
                if (kind == 0 && odo.isNaN()) { toast("لا توجد قراءة عداد حالية؛ اختر «عند قراءة عداد محددة»"); return@setPositiveButton }
                val target = when (kind) {
                    0 -> odo + n
                    1 -> n
                    else -> (System.currentTimeMillis() + (n * DAY_MS)).toDouble()
                }
                val r = Reminder(old?.id ?: System.currentTimeMillis(), t, kind != 2, target, notified = false)
                if (!Logbook.saveReminder(this, r)) toast("وصلت للحد الأقصى ${Logbook.MAX_REMINDERS} تذكيرًا")
                render()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    // ---------------- الإحصاءات ----------------

    private fun stats() {
        val st = Logbook.stats(this)
        val s = VehicleRepository.state
        val p = Prefs(this)
        card("منذ ${dayFmt.format(Date(st.since))}", "المسافة المسجلة ${num(st.km, 1)} كم · ساعات المحرك ${duration(st.engineSec)}")
        card("الأقصى", "السرعة ${num(st.maxSpeed)} كم/س · الدورات ${num(st.maxRpm)} د/د · حرارة المحرك ${num(st.maxCoolant)}°")
        card("حرارة الخارج", "الأدنى ${num(st.minOutside)}° · الأعلى ${num(st.maxOutside)}°")
        card("الآن", "العداد ${num(s.odometerKm)} كم · الوقود ${num(s.fuelPercent)}% · المدى ${num(s.rangeKm)} كم",
            "الاستهلاك منذ آخر تعبئة ${num(FuelTracker.avgSinceRefuel(this, s), 1)} ل/100 كم · البطارية ${num(s.batteryVoltage, 1)} فولت")
        val now = java.util.Calendar.getInstance()
        card("الشمس اليوم", "الشروق ${Sun.format(Sun.minutes(now, p.lat.toDouble(), p.lon.toDouble(), true))} · " +
            "الغروب ${Sun.format(Sun.minutes(now, p.lat.toDouble(), p.lon.toDouble(), false))}")
        actions("تصفير الإحصاءات" to { confirm("تصفير كل الإحصاءات والبدء من الآن؟") { Logbook.resetStats(this) } })
        list.addView(label("تُسجَّل من بيانات السيارة الحية فقط، وما دام التطبيق يعمل (فعّل الكرة العائمة ليبقى يعمل في الخلفية).", 13f, Palette.muted))
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
}
