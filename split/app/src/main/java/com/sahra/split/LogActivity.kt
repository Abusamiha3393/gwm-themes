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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

/**
 * السجل: الرحلات (لوحات خضراء)، التعبئات (لوحات خدمات زرقاء)، التذكيرات (زرقاء، وكهرمانية عند حلولها)،
 * والإحصاءات (بنية). كل إدخال لوحة: الرمز والعنوان يمينًا، والرقم الكبير يسارًا كلوحة المسافات.
 */
class LogActivity : Activity() {

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L
    }

    private data class Tab(val name: String, val icon: Int, val color: Int)
    private val tabs = listOf(
        Tab("الرحلات", R.drawable.ic_road, Palette.green),
        Tab("التعبئات", R.drawable.ic_fuel, Palette.blue),
        Tab("التذكيرات", R.drawable.ic_bell, Palette.blue),
        Tab("الإحصاءات", R.drawable.ic_stats, Palette.brown),
    )

    private var tab = 0
    private lateinit var list: LinearLayout
    private lateinit var tabsRow: LinearLayout
    private val dateFmt = SimpleDateFormat("EEEE d/M · HH:mm", ArabicLatn)
    private val dayFmt = SimpleDateFormat("d MMMM yyyy", ArabicLatn)
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Type.init(this)
        window.statusBarColor = Palette.gantry
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        tab = intent.getIntExtra("tab", 0).coerceIn(0, tabs.size - 1)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AsphaltDrawable(this@LogActivity)
            setPadding(dp(20), dp(16), dp(20), dp(8))
        }
        root.addView(header(R.drawable.ic_log, "السجل") { finish() })
        tabsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(tabsRow, LinearLayout.LayoutParams(MATCH_PARENT, dp(64)).apply { topMargin = dp(14) })
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(14), 0, dp(24)) }
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
        tabs.forEachIndexed { i, t ->
            val on = i == tab
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                background = if (on) smallSign(t.color) else rounded(Palette.shoulder, dp(10f), dp(2), Palette.kerb)
                setOnClickListener { tab = i; render() }
            }
            row.addView(icon(t.icon, 24, if (on) Palette.white else Palette.muted), LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(10) })
            row.addView(label(t.name, 16f, if (on) Palette.white else Palette.muted, on))
            tabsRow.addView(row, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { if (i > 0) marginStart = dp(8) })
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

    /** لوحة إدخال: رمز وعنوان وسطران يمينًا، ورقم كبير بوحدته يسارًا. */
    private fun entry(color: Int, res: Int, title: String, line1: String, line2: String, value: String, unit: String, ink: Int = Palette.white): View {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = sign(color)
            setPadding(dp(24), dp(18), dp(24), dp(18))
        }
        c.addView(icon(res, 40, ink), LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(18) })
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(label(title, 18f, ink, true))
        val dim = if (ink == Palette.white) Palette.legendDim else (ink and 0x00FFFFFF) or 0xCC000000.toInt()
        if (line1.isNotEmpty()) texts.addView(label(line1, 15f, dim).apply { setPadding(0, dp(6), 0, 0) })
        if (line2.isNotEmpty()) texts.addView(label(line2, 14f, dim).apply { setPadding(0, dp(4), 0, 0) })
        c.addView(texts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        if (value.isNotEmpty()) {
            val v = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM; layoutDirection = View.LAYOUT_DIRECTION_LTR }
            v.addView(label(value, 34f, ink, true, digitsFace = true))
            if (unit.isNotEmpty()) v.addView(label(unit, 15f, dim).apply { setPadding(dp(8), 0, 0, dp(4)) })
            c.addView(v, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(16) })
        }
        list.addView(c, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })
        return c
    }

    private fun empty(res: Int, text: String) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(0, dp(48), 0, dp(48)) }
        box.addView(icon(res, 64, Palette.laneDash))
        box.addView(label(text, 16f, Palette.muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(18), 0, 0); setLineSpacing(dp(4f), 1f) })
        list.addView(box, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun actions(vararg items: Triple<Int, String, () -> Unit>) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        items.forEach { (res, t, f) ->
            row.addView(signButton(res, t, Palette.blue, f), LinearLayout.LayoutParams(WRAP_CONTENT, dp(60)).apply { marginEnd = dp(10) })
        }
        list.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(14) })
    }

    /** أمر هادئ (حذف وتصفير): بلا لون لوحة، حتى لا يغري باللمس. */
    private fun quietAction(res: Int, text: String, onClick: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(0, dp(10f), dp(2), Palette.kerb)
            setPadding(dp(18), 0, dp(18), 0)
            minimumHeight = dp(64)
            setOnClickListener { onClick() }
        }
        row.addView(icon(res, 22, Palette.muted), LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(10) })
        row.addView(label(text, 15f, Palette.muted))
        val wrap = LinearLayout(this).apply { gravity = Gravity.END }
        wrap.addView(row, LinearLayout.LayoutParams(WRAP_CONTENT, dp(64)))
        list.addView(wrap, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(12) })
    }

    private fun note(text: String) {
        list.addView(label(text, 14f, Palette.muted).apply { setPadding(dp(4), dp(6), dp(4), 0) })
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
            empty(R.drawable.ic_road, "لا توجد رحلات بعد.\nتُسجَّل الرحلة تلقائيًا عند التحرك، وتنتهي بعد إطفاء المحرك دقيقتين أو التوقف 10 دقائق.")
            return
        }
        quietAction(R.drawable.ic_trash, "حذف الكل") { confirm("حذف كل الرحلات المحفوظة؟") { Logbook.clearTrips(this) } }
        all.forEach { t ->
            val title = dateFmt.format(Date(t.start)) + " ← " + timeFmt.format(Date(t.end)) + if (!t.done) "  · جارية الآن" else ""
            val liters = t.liters(tank)
            val cons = if (liters.isNaN() || t.km < 5f) Float.NaN else liters / t.km * 100f
            val line1 = "${duration(t.driveSec)} · متوسط ${num(t.avgSpeed)} كم/س · أقصى ${num(t.maxSpeed)} كم/س"
            val line2 = listOfNotNull(
                if (liters.isNaN()) null else "وقود ${num(liters, 1)} لتر",
                if (cons.isNaN()) null else "${num(cons, 1)} ل/100 كم",
                if (t.endOdo.isNaN()) null else "العداد ${num(t.endOdo)} كم",
            ).joinToString(" · ")
            entry(if (t.done) Palette.green else Palette.amber, R.drawable.ic_road, title, line1, line2, num(t.km, 1), "كم",
                if (t.done) Palette.white else Palette.ink).setOnLongClickListener {
                confirm("حذف هذه الرحلة؟") { Logbook.deleteTrip(this, t.start) }; true
            }
        }
        note("اضغط مطولًا على رحلة لحذفها. يُحفظ آخر ${Logbook.MAX_TRIPS} رحلة.")
    }

    // ---------------- التعبئات ----------------

    private fun refuels() {
        val all = Logbook.refuels(this)
        if (all.isEmpty()) {
            empty(R.drawable.ic_fuel, "لا توجد تعبئات بعد.\nتُرصد التعبئة تلقائيًا عندما ترتفع نسبة الوقود 5% أو أكثر.")
            return
        }
        quietAction(R.drawable.ic_trash, "حذف الكل") { confirm("حذف كل التعبئات المحفوظة؟") { Logbook.clearRefuels(this) } }
        all.forEach { r ->
            val line1 = "${num(r.fromPct)}% ← ${num(r.toPct)}% · العداد ${num(r.odo)} كم"
            val line2 = if (r.prevConsumption.isNaN()) "استهلاك الخزان السابق: المسافة غير كافية للحساب"
            else "الخزان السابق: ${num(r.prevKm)} كم بمعدل ${num(r.prevConsumption, 1)} ل/100 كم"
            entry(Palette.blue, R.drawable.ic_fuel, dateFmt.format(Date(r.at)), line1, line2, num(r.liters), "لتر").setOnLongClickListener {
                confirm("حذف هذه التعبئة؟") { Logbook.deleteRefuel(this, r.at) }; true
            }
        }
        note("اللترات تقديرية من نسبة الوقود وسعة الخزان (${num(Prefs(this).tankLiters)} لتر، تُغيَّر من الإعدادات).")
    }

    // ---------------- التذكيرات ----------------

    private fun reminders() {
        val all = Logbook.reminders(this)
        actions(Triple(R.drawable.ic_plus, "تذكير جديد") { editReminder(null) })
        if (all.isEmpty()) {
            empty(R.drawable.ic_bell, "لا توجد تذكيرات.\nمثال: تغيير الزيت بعد 5000 كم، أو تجديد التأمين بتاريخ محدد.")
            return
        }
        val odo = VehicleRepository.state.odometerKm
        all.sortedBy { if (it.due(odo)) 0 else 1 }.forEach { r ->
            val due = r.due(odo)
            val value: String; val unit: String; val line: String
            if (r.byOdo) {
                val left = if (odo.isNaN()) Float.NaN else (r.target - odo).toFloat()
                value = if (due) "الآن" else num(left); unit = if (due) "" else "كم"
                line = "عند العداد ${num(r.target.toFloat())} كم"
            } else {
                val days = ((r.target - System.currentTimeMillis()) / DAY_MS).toLong()
                value = if (due) "الآن" else "$days"; unit = if (due) "" else "يوم"
                line = "بتاريخ ${dayFmt.format(Date(r.target.toLong()))}"
            }
            entry(if (due) Palette.amber else Palette.blue, if (due) R.drawable.ic_warning else R.drawable.ic_bell,
                r.text, line, if (due) "حان الموعد" else "", value, unit, if (due) Palette.ink else Palette.white).setOnClickListener {
                AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(r.text)
                    .setItems(arrayOf("تعديل", "حذف")) { _, w ->
                        if (w == 0) editReminder(r) else confirm("حذف التذكير؟") { Logbook.deleteReminder(this, r.id) }
                    }.show()
            }
        }
        note("يصلك إشعار عند حلول الموعد. الحد الأقصى ${Logbook.MAX_REMINDERS} تذكيرًا.")
    }

    private fun editReminder(old: Reminder?) {
        val odo = VehicleRepository.state.odometerKm
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val text = EditText(this).apply { hint = "نص التذكير"; setText(old?.text ?: "") }
        box.addView(text)
        val group = RadioGroup(this)
        val kinds = listOf("بعد عدد من الكيلومترات", "عند قراءة عداد محددة", "بعد عدد من الأيام")
        kinds.forEachIndexed { i, k -> group.addView(RadioButton(this).apply { this.text = k; id = 100 + i }) }
        group.check(if (old == null) 100 else if (old.byOdo) 101 else 102)
        box.addView(group)
        val value = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "الرقم"
            if (old != null) setText(if (old.byOdo) old.target.roundToLong().toString()
            else ((old.target - System.currentTimeMillis()) / DAY_MS).toLong().coerceAtLeast(0).toString())
        }
        box.addView(value)
        box.addView(label("العداد الحالي: ${num(odo)} كم", 14f, Palette.muted).apply { setPadding(0, dp(8), 0, 0) })
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
        val since = "منذ ${dayFmt.format(Date(st.since))}"
        entry(Palette.green, R.drawable.ic_road, "المسافة المسجلة", since, "", num(st.km, 1), "كم")
        entry(Palette.brown, R.drawable.ic_clock, "ساعات تشغيل المحرك", since, "", duration(st.engineSec), "")
        entry(Palette.green, R.drawable.ic_speed, "أقصى سرعة", "أقصى دوران ${num(st.maxRpm)} د/د", "", num(st.maxSpeed), "كم/س")
        entry(Palette.brown, R.drawable.ic_thermo, "أعلى حرارة للمحرك", "حرارة الخارج: الأدنى ${ltr(num(st.minOutside) + "°")} · الأعلى ${ltr(num(st.maxOutside) + "°")}", "", num(st.maxCoolant), "°")
        entry(Palette.blue, R.drawable.ic_fuel, "الاستهلاك منذ آخر تعبئة",
            "الوقود ${num(s.fuelPercent)}% · المدى ${num(s.rangeKm)} كم", "البطارية ${num(s.batteryVoltage, 1)} فولت",
            num(FuelTracker.avgSinceRefuel(this, s), 1), "ل/100")
        val now = java.util.Calendar.getInstance()
        entry(Palette.brown, R.drawable.ic_sun, "الشمس اليوم",
            "الشروق ${Sun.format(Sun.minutes(now, p.lat.toDouble(), p.lon.toDouble(), true))} · الغروب ${Sun.format(Sun.minutes(now, p.lat.toDouble(), p.lon.toDouble(), false))}",
            "", "", "")
        quietAction(R.drawable.ic_trash, "تصفير الإحصاءات") { confirm("تصفير كل الإحصاءات والبدء من الآن؟") { Logbook.resetStats(this) } }
        note("تُسجَّل من بيانات السيارة الحية فقط، وما دام التطبيق يعمل (فعّل الكرة العائمة ليبقى يعمل في الخلفية).")
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
}
