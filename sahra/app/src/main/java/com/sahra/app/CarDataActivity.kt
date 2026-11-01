package com.sahra.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * بيانات السيارة: القيم الحية، وفحص كل المفاتيح المعروفة لنعرف ما تدعمه سيارتك
 * قبل بناء أي ميزة عليه. النتيجة تُنسخ أو تُشارك لتصل للمطوّر.
 */
class CarDataActivity : Activity(), Car.Listener {

    private lateinit var form: Form
    private val live = LinkedHashMap<String, TextView>()
    private lateinit var probeBox: LinearLayout
    private lateinit var probeStatus: TextView
    private var report = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Type.init(this)
        window.statusBarColor = Palette.gantry
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        val scroll = ScrollView(this).apply { background = AsphaltDrawable(this@CarDataActivity); isVerticalScrollBarEnabled = false }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(16), dp(24), dp(40)) }
        scroll.addView(body)
        form = Form(this, body)
        body.addView(header(R.drawable.ic_speed, "بيانات السيارة", Palette.blue) { finish() })

        form.section("القيم الحية")
        listOf("المصدر", "السرعة", "الدوران", "الغيار", "الوقود", "المدى", "العداد", "حرارة المحرك", "البطارية",
            "حرارة الخارج", "حرارة الداخل", "زاوية المقود", "الميلان الأمامي", "الميلان الجانبي", "البوصلة", "الارتفاع", "وضع القيادة")
            .forEach { live[it] = form.info(it, "—") }

        form.section("فحص المفاتيح")
        form.note("يقرأ كل مفتاح معروف من خدمة السيارة ويبيّن قيمته. المفاتيح التي تعطي قيمة هي ما نبني عليه الميزات. أرسل النتيجة لي بعد الفحص.")
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(signButton(R.drawable.ic_road, "افحص الآن", Palette.green) { runProbe(CarKeys.PROBE) }, LinearLayout.LayoutParams(0, dp(64), 1f).apply { marginEnd = dp(10) })
        buttons.addView(signButton(R.drawable.ic_log, "نسخ", Palette.blue) { copy() }, LinearLayout.LayoutParams(0, dp(64), 1f).apply { marginEnd = dp(10) })
        buttons.addView(signButton(R.drawable.ic_swap, "مشاركة", Palette.blue) { share() }, LinearLayout.LayoutParams(0, dp(64), 1f))
        form.add(buttons)

        // تجربة مفتاح بالاسم
        val custom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val input = EditText(this).apply {
            hint = "مفتاح آخر، مثل car.basic.xxx"
            setTextColor(Palette.white); setHintTextColor(Palette.muted)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            textDirection = View.TEXT_DIRECTION_LTR
            textSize = 16f * uiScale()
        }
        custom.addView(input, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        custom.addView(signButton(null, "اقرأ", Palette.blue) {
            val k = input.text.toString().trim()
            if (k.isNotEmpty()) runProbe(listOf("مفتاح يدوي" to listOf(k)), append = true)
        }, LinearLayout.LayoutParams(WRAP_CONTENT, dp(64)).apply { marginStart = dp(10) })
        form.add(custom)

        probeStatus = form.note("لم يُشغَّل الفحص بعد.")
        probeBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(probeBox)
        setContentView(scroll)
    }

    override fun onResume() { super.onResume(); Car.start(this); Car.listen(this) }
    override fun onPause() { Car.unlisten(this); super.onPause() }

    override fun onCar(s: CarState) {
        fun set(k: String, v: String) { live[k]?.text = v }
        set("المصدر", when { s.live -> "السيارة"; s.demo -> "تجريبي"; Car.available == false -> "غير متاحة"; else -> "…" })
        set("السرعة", "${num(s.speed)} كم/س")
        set("الدوران", num(s.rpm))
        set("الغيار", if (s.gear.isEmpty()) "—" else s.gear + if (s.gearFromCar) "" else " (تقدير)")
        set("الوقود", "${num(s.fuel)}%")
        set("المدى", "${num(s.range)} كم")
        set("العداد", "${num(s.odometer)} كم")
        set("حرارة المحرك", "${num(s.coolant)}°")
        set("البطارية", "${num(s.battery, 1)} V")
        set("حرارة الخارج", "${num(s.outside)}°")
        set("حرارة الداخل", "${num(s.inside)}°")
        set("زاوية المقود", "${num(s.steering)}°")
        set("الميلان الأمامي", "${num(s.pitch, 1)}°")
        set("الميلان الجانبي", "${num(s.roll, 1)}°")
        set("البوصلة", "${num(s.compass)}°")
        set("الارتفاع", "${num(s.altitude)} م")
        set("وضع القيادة", s.driveMode.ifEmpty { "—" })
    }

    private fun runProbe(groups: List<Pair<String, List<String>>>, append: Boolean = false) {
        if (!GwmAdapter.isAvailable()) {
            probeStatus.text = "خدمة السيارة غير متاحة على هذا الجهاز، فلا يمكن الفحص."
            return
        }
        probeStatus.text = "جارٍ الفحص…"
        Thread {
            val results = groups.map { (title, keys) -> title to Car.probe(this, keys) }
            runOnUiThread {
                if (!append) { probeBox.removeAllViews(); report = "" }
                var found = 0; var total = 0
                val sb = StringBuilder(report)
                results.forEach { (title, map) ->
                    val f = Form(this, probeBox)
                    f.section(title)
                    sb.append("## ").append(title).append('\n')
                    map.forEach { (k, v) ->
                        total++
                        val has = !v.isNullOrBlank() && v != "null"
                        if (has) found++
                        val row = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            background = rounded(Palette.shoulder, dp(10f), dp(1), if (has) Palette.green else Palette.kerb)
                            setPadding(dp(16), dp(10), dp(16), dp(10))
                        }
                        row.addView(label(k, 14f, if (has) Palette.white else Palette.muted).apply { textDirection = View.TEXT_DIRECTION_LTR })
                        row.addView(label(if (has) v!! else "لا قيمة", 16f, if (has) Palette.amber else Palette.laneDash, has, digitsFace = has).apply {
                            textDirection = View.TEXT_DIRECTION_LTR; setPadding(0, dp(4), 0, 0)
                        })
                        f.add(row)
                        sb.append(k).append(" = ").append(v ?: "—").append('\n')
                    }
                }
                report = sb.toString()
                probeStatus.text = "اكتمل: $found من $total مفتاحًا أعطى قيمة."
            }
        }.start()
    }

    private fun fullReport() = "صحراء ${Updater.currentName(this)} — فحص مفاتيح السيارة\n\n" + report.ifEmpty { "لم يُشغَّل الفحص." }

    private fun copy() {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("sahra", fullReport()))
        Toast.makeText(this, "نُسخت النتيجة", Toast.LENGTH_SHORT).show()
    }

    private fun share() {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, fullReport())
        runCatching { startActivity(Intent.createChooser(i, "مشاركة نتيجة الفحص")) }
    }
}
