package com.sahra.dash

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.util.Locale

/** شاشة الإعدادات: الصلاحيات، اختيار الثيم والموضع، أزرار المقود، والبيانات الحية. */
class MainActivity : Activity(), VehicleRepository.Listener {

    private lateinit var prefs: Prefs
    private lateinit var liveText: TextView
    private lateinit var sourceText: TextView
    private lateinit var overlayBtn: Button
    private lateinit var mediaBtn: Button
    private lateinit var keysText: TextView
    private var learning: ((SteeringAction) -> Unit)? = null
    private var learnDialog: AlertDialog? = null

    private val bg = Color.parseColor("#14100C")
    private val card = Color.parseColor("#211A14")
    private val accent = Color.parseColor("#E8A24A")
    private val fg = Color.parseColor("#F4E3C3")
    private val muted = Color.parseColor("#A8957A")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        setContentView(build())
    }

    override fun onResume() {
        super.onResume()
        VehicleRepository.forceDemo = prefs.demo
        VehicleRepository.start(packageName)
        VehicleRepository.addListener(this)
        refreshPermissions()
    }

    override fun onPause() {
        VehicleRepository.removeListener(this)
        if (!OverlayService.isRunning) VehicleRepository.stop()
        super.onPause()
    }

    // ---------------- بناء الواجهة ----------------

    private fun build(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(32))
        }
        root.addView(text(getString(R.string.app_name), 28f, fg, bold = true))
        root.addView(text("عدادات وثيمات لشاشات هافال وتانك", 15f, muted).apply { setPadding(0, dp(2), 0, dp(12)) })

        // البيانات الحية
        val live = section(root, "بيانات السيارة")
        sourceText = text("", 14f, muted)
        liveText = text("", 16f, fg).apply { typeface = Typeface.MONOSPACE; setLineSpacing(0f, 1.3f) }
        live.addView(sourceText); live.addView(liveText)

        // التشغيل
        val run = section(root, "التشغيل")
        run.addView(row(
            button("إظهار العدادات", primary = true) { startOverlay() },
            button("إيقاف") { OverlayService.send(this, OverlayService.ACTION_STOP) },
        ))
        run.addView(switch("تشغيل تلقائي عند تشغيل السيارة", prefs.autostart) { prefs.autostart = it })
        run.addView(switch("وضع تجريبي (بيانات وهمية للتجربة)", prefs.demo) {
            prefs.demo = it
            VehicleRepository.forceDemo = it
            if (OverlayService.isRunning) OverlayService.send(this, OverlayService.ACTION_RELOAD)
        })

        // الصلاحيات
        val perms = section(root, "الصلاحيات")
        overlayBtn = button("") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        mediaBtn = button("") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        perms.addView(overlayBtn); perms.addView(mediaBtn)

        // الثيمات
        val themes = section(root, "الثيم")
        val tg = RadioGroup(this)
        Themes.all.forEachIndexed { i, t ->
            tg.addView(radio(t.name, i, t.id == prefs.themeId))
        }
        tg.setOnCheckedChangeListener { _, id ->
            prefs.themeId = Themes.all[id].id
            if (OverlayService.isRunning) OverlayService.send(this, OverlayService.ACTION_RELOAD)
        }
        themes.addView(tg)

        // الموضع
        val pos = section(root, "موضع العدادات على الشاشة")
        val pg = RadioGroup(this)
        OverlayLayout.entries.forEachIndexed { i, l -> pg.addView(radio(l.label, i, l == prefs.layout)) }
        pg.setOnCheckedChangeListener { _, id ->
            prefs.layout = OverlayLayout.entries[id]
            if (OverlayService.isRunning) OverlayService.send(this, OverlayService.ACTION_RELOAD)
        }
        pos.addView(pg)

        // أزرار المقود
        val keys = section(root, "أزرار المقود")
        keysText = text("", 14f, muted)
        keys.addView(keysText)
        keys.addView(row(
            button("تعلّم زر الإظهار/الإخفاء") { learn { prefs.toggleKey = it.code } },
            button("تعلّم زر الثيم التالي") { learn { prefs.nextThemeKey = it.code } },
        ))
        keys.addView(button("مسح الأزرار") { prefs.toggleKey = 0; prefs.nextThemeKey = 0; refreshKeys() })
        refreshKeys()

        return ScrollView(this).apply {
            setBackgroundColor(bg)
            addView(root, MATCH_PARENT, WRAP_CONTENT)
        }
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        OverlayService.send(this, OverlayService.ACTION_SHOW)
    }

    private fun refreshPermissions() {
        val overlay = Settings.canDrawOverlays(this)
        overlayBtn.text = if (overlay) "✓ العرض فوق التطبيقات مسموح" else "اسمح بالعرض فوق التطبيقات (مطلوب)"
        val media = MediaTracker.hasAccess(this)
        mediaBtn.text = if (media) "✓ عرض الأغنية الحالية مسموح" else "اسمح بقراءة الإشعارات (لعرض الأغنية الحالية)"
    }

    private fun refreshKeys() {
        fun name(c: Int) = if (c == 0) "غير محدد" else "رمز $c"
        keysText.text = "زر الإظهار/الإخفاء: ${name(prefs.toggleKey)}\nزر الثيم التالي: ${name(prefs.nextThemeKey)}"
    }

    private fun learn(save: (SteeringAction) -> Unit) {
        learning = { a ->
            save(a)
            learnDialog?.dismiss()
            refreshKeys()
        }
        learnDialog = AlertDialog.Builder(this)
            .setTitle("اضغط زر المقود الآن")
            .setMessage("اضغط الزر الذي تريد استخدامه على المقود.")
            .setNegativeButton("إلغاء", null)
            .setOnDismissListener { learning = null }
            .show()
    }

    // ---------------- البيانات الحية ----------------

    override fun onState(state: VehicleState) {
        sourceText.text = when {
            state.live -> "متصل بالسيارة ✓"
            state.demo && prefs.demo -> "الوضع التجريبي"
            else -> "لم يُعثر على خدمة السيارة — تُعرض بيانات تجريبية"
        }
        fun f(v: Float, unit: String, d: Int = 0) =
            if (v.isNaN()) "—" else String.format(Locale.US, "%.${d}f", v) + unit
        liveText.text = buildString {
            append("السرعة ${f(state.speed, " كم/س")}   الدورات ${f(state.rpm, "")}   الغيار ${state.gear}\n")
            append("الوقود ${f(state.fuelPercent, "٪")}   المدى ${f(state.rangeKm, " كم")}   العداد ${f(state.odometerKm, " كم")}\n")
            append("المحرك ${f(state.coolantTemp, "°")}   الخارج ${f(state.outsideTemp, "°")}   البطارية ${f(state.batteryVoltage, "V", 1)}\n")
            append("الميلان ${f(state.pitch, "°", 1)}   الانحراف ${f(state.roll, "°", 1)}   البوصلة ${f(state.compass, "°")}   الارتفاع ${f(state.altitude, " م")}")
            if (state.vin.isNotEmpty()) append("\nرقم الهيكل ${state.vin}")
        }
    }

    override fun onSteeringKey(action: SteeringAction) {
        learning?.invoke(action)
    }

    // ---------------- عناصر الواجهة ----------------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun section(parent: LinearLayout, title: String): LinearLayout {
        parent.addView(text(title, 18f, accent, bold = true).apply { setPadding(0, dp(18), 0, dp(8)) })
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply { setColor(card); cornerRadius = dp(14).toFloat() }
        }
        parent.addView(box, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return box
    }

    private fun button(label: String, primary: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 16f
        setTextColor(if (primary) bg else fg)
        background = GradientDrawable().apply {
            setColor(if (primary) accent else Color.parseColor("#2E251C"))
            cornerRadius = dp(10).toFloat()
        }
        setPadding(dp(16), dp(12), dp(16), dp(12))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(0, dp(6), 0, dp(6)) }
    }

    private fun row(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEachIndexed { i, v ->
            addView(v, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                setMargins(if (i > 0) dp(8) else 0, dp(6), 0, dp(6))
            })
        }
    }

    @Suppress("DEPRECATION")
    private fun switch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
        text = label
        textSize = 16f
        setTextColor(fg)
        isChecked = checked
        setPadding(0, dp(10), 0, dp(10))
        setOnCheckedChangeListener { _, v -> onChange(v) }
    }

    private fun radio(label: String, id: Int, checked: Boolean) = RadioButton(this).apply {
        this.id = id
        text = label
        textSize = 16f
        setTextColor(fg)
        isChecked = checked
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(10), dp(8), dp(10))
    }
}
