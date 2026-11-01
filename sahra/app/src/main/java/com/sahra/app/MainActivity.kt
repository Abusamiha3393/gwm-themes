package com.sahra.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * الشاشة الرئيسية في مرحلة الأساس: حالة الاتصال بالسيارة، أربع قيم حية،
 * وخارطة الميزات التي نبنيها خطوة خطوة، وزرّا «بيانات السيارة» و«الإعدادات».
 */
class MainActivity : Activity(), Car.Listener {

    private lateinit var status: TextView
    private lateinit var statusBox: LinearLayout
    private val values = LinkedHashMap<String, TextView>()

    /** خارطة البناء: الاسم، الوصف، الحالة (0 جاهز، 1 الآن، 2 قادم). */
    private val roadmap = listOf(
        Triple("الأساس", "بيانات السيارة، الإعدادات، التحديث", 1),
        Triple("ثيمات العدادات", "عرض الثيم على شاشة العدادات بالمقاس الصحيح، وثيمك «هافال»", 2),
        Triple("مصمم الثيمات", "تسحب العناصر بإصبعك، مع مواضع مؤشرات السيارة", 2),
        Triple("تقسيم الشاشة", "الملاحة والموسيقى جنبًا إلى جنب", 2),
        Triple("لوحة المعلومات", "الساعة، الوقود، الطقس، الطرق الوعرة", 2),
        Triple("الكرة العائمة والسجل", "قائمة سريعة، الرحلات، التعبئات، التذكيرات", 2),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Type.init(this)
        window.statusBarColor = Palette.gantry
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        val scroll = ScrollView(this).apply { background = AsphaltDrawable(this@MainActivity); isVerticalScrollBarEnabled = false }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(18), dp(24), dp(32)) }
        scroll.addView(body)

        // العنوان وحالة الاتصال
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = sign(Palette.green)
            setPadding(dp(22), dp(12), dp(26), dp(12))
        }
        title.addView(icon(R.drawable.ic_fork, 36), LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(14) })
        title.addView(label("صحراء", 28f, Palette.white, true))
        top.addView(title)
        top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        statusBox = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(10)) }
        status = label("…", 16f, Palette.white, true)
        statusBox.addView(status)
        top.addView(statusBox)
        body.addView(top)

        // أربع قيم حية
        val grid = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("السرعة" to "كم/س", "الدوران" to "د/د", "الوقود" to "%", "الغيار" to "").forEachIndexed { i, (name, unit) ->
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = sign(if (i % 2 == 0) Palette.blue else Palette.brown)
                setPadding(dp(12), dp(18), dp(12), dp(18))
            }
            box.addView(label(name, 15f, Palette.legendDim))
            val v = label("—", 44f, Palette.white, true, digitsFace = true).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(2)) }
            box.addView(v)
            box.addView(label(unit, 13f, Palette.legendDim))
            values[name] = v
            grid.addView(box, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { if (i > 0) marginStart = dp(12) })
        }
        body.addView(grid, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(20) })

        // الأزرار
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(signButton(R.drawable.ic_speed, "بيانات السيارة", Palette.blue) { startActivity(Intent(this, CarDataActivity::class.java)) },
            LinearLayout.LayoutParams(0, dp(72), 1f).apply { marginEnd = dp(12) })
        actions.addView(signButton(R.drawable.ic_settings, "الإعدادات", Palette.blue) { startActivity(Intent(this, SettingsActivity::class.java)) },
            LinearLayout.LayoutParams(0, dp(72), 1f))
        body.addView(actions, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(16) })

        // خارطة البناء
        val form = Form(this, body)
        form.section("خارطة البناء")
        roadmap.forEach { (name, desc, state) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = if (state == 1) smallSign(Palette.green) else rounded(Palette.shoulder, dp(12f), dp(1), Palette.kerb)
                setPadding(dp(20), dp(14), dp(20), dp(14))
            }
            row.addView(icon(when (state) { 0 -> R.drawable.ic_check; 1 -> R.drawable.ic_up; else -> R.drawable.ic_clock }, 26,
                if (state == 2) Palette.muted else Palette.white), LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(16) })
            val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(label(name, 18f, if (state == 2) Palette.muted else Palette.white, true))
            texts.addView(label(desc, 14f, if (state == 1) Palette.legendDim else Palette.muted).apply { setPadding(0, dp(4), 0, 0) })
            row.addView(texts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            row.addView(label(when (state) { 0 -> "جاهز"; 1 -> "الآن"; else -> "قادم" }, 15f, if (state == 2) Palette.muted else Palette.white, state == 1))
            form.add(row)
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        Car.start(this)
        Car.listen(this)
        if (Prefs(this).autoUpdate && Updater.due(this)) Thread { Updater.check(this) }.start()
    }

    override fun onPause() { Car.unlisten(this); super.onPause() }

    override fun onCar(s: CarState) {
        val (text, color) = when {
            s.live -> "متصل بالسيارة" to Palette.green
            s.demo && Prefs(this).demo -> "وضع تجريبي" to Palette.brown
            Car.available == false -> "خدمة السيارة غير متاحة — بيانات تجريبية" to Palette.brown
            else -> "جارٍ الاتصال…" to Palette.kerb
        }
        status.text = text
        statusBox.background = smallSign(color)
        values["السرعة"]?.text = num(s.speed)
        values["الدوران"]?.text = num(s.rpm)
        values["الوقود"]?.text = num(s.fuel)
        values["الغيار"]?.text = s.gear.ifEmpty { "—" }
    }
}
