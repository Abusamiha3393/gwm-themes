package com.sahra.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

/**
 * الإعدادات بتبويبات: عام، السيارة، متقدم، حول.
 * كل ميزة جديدة تضيف تبويبها أو قسمها هنا.
 */
class SettingsActivity : Activity() {

    private val tabs = listOf("عام", "السيارة", "متقدم", "حول")
    private var tab = 0
    private lateinit var prefs: Prefs
    private lateinit var tabsRow: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var scroll: ScrollView
    private var pending: Updater.Release? = null
    private var updateStatus = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Type.init(this)
        prefs = Prefs(this)
        window.statusBarColor = Palette.gantry
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        tab = savedInstanceState?.getInt("tab") ?: 0
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AsphaltDrawable(this@SettingsActivity)
            setPadding(dp(24), dp(16), dp(24), 0)
        }
        root.addView(header(R.drawable.ic_settings, "الإعدادات", Palette.blue) { finish() })
        tabsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(tabsRow, LinearLayout.LayoutParams(MATCH_PARENT, dp(64)).apply { topMargin = dp(14) })
        scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(40)) }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); out.putInt("tab", tab) }

    override fun onResume() { super.onResume(); render() }

    private fun render() {
        tabsRow.removeAllViews()
        tabs.forEachIndexed { i, name ->
            val on = i == tab
            tabsRow.addView(label(name, 17f, if (on) Palette.white else Palette.muted, on).apply {
                gravity = Gravity.CENTER
                background = if (on) smallSign(Palette.green) else rounded(Palette.shoulder, dp(10f), dp(2), Palette.kerb)
                setOnClickListener { tab = i; scroll.scrollTo(0, 0); render() }
                pressable(dp(10f))
            }, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { if (i > 0) marginStart = dp(8) })
        }
        body.removeAllViews()
        val f = Form(this, body)
        when (tab) {
            0 -> general(f)
            1 -> car(f)
            2 -> advanced(f)
            else -> about(f)
        }
    }

    private fun general(f: Form) {
        f.section("الصلاحيات")
        f.permission("العرض فوق التطبيقات", "لعرض الثيم على شاشة العدادات (الخطوة القادمة)", Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        f.section("التجربة")
        f.toggle("الوضع التجريبي (بيانات وهمية)", prefs.demo) { prefs.demo = it }
    }

    private fun car(f: Form) {
        f.section("بيانات السيارة")
        f.row("القيم الحية وفحص المفاتيح") { startActivity(Intent(this, CarDataActivity::class.java)) }
        f.section("القياسات")
        f.seek("تصحيح السرعة", 90, 110, Math.round(prefs.speedFactor * 100), { String.format(java.util.Locale.US, "%.2f", it / 100f) }) {
            prefs.speedFactor = it / 100f
        }
        f.note("عدّاد السيارة يزيد عادة عن السرعة الحقيقية بقليل. 1.00 يعني كما هو، و1.04 يعني أعلى بـ4%.")
        f.seek("سعة الخزان", 40, 140, prefs.tankLiters, { "$it لتر" }) { prefs.tankLiters = it }
    }

    private fun advanced(f: Form) {
        f.section("التحديثات")
        f.info("الإصدار الحالي", Updater.currentName(this))
        f.toggle("التحقق تلقائيًا كل 6 ساعات", prefs.autoUpdate) { prefs.autoUpdate = it }
        val rel = pending
        if (rel != null) f.row("ثبّت الإصدار 1.0.${rel.number}") { install(rel) }
        else f.row("تحقق الآن", updateStatus) { checkUpdate() }
        if (rel != null && updateStatus.isNotEmpty()) f.note(updateStatus)
    }

    private fun about(f: Form) {
        f.section("صحراء")
        f.note("تطبيق لشاشات GWM: ثيمات شاشة العدادات، وتقسيم الشاشة، ولوحة المعلومات. يُبنى خطوة خطوة، وكل خطوة تُجرَّب في السيارة قبل التي بعدها.", Palette.white)
        f.info("الإصدار", Updater.currentName(this))
        f.info("خدمة السيارة", if (GwmAdapter.isAvailable()) "متاحة" else "غير متاحة")
        f.section("الخطوط")
        f.note("Noto Kufi Arabic وOverpass، برخصة SIL Open Font License 1.1.")
    }

    private fun checkUpdate() {
        updateStatus = "جارٍ التحقق…"
        render()
        Thread {
            val r = Updater.check(this)
            runOnUiThread {
                when (r) {
                    is Updater.Result.Available -> { pending = r.release; updateStatus = "يوجد إصدار جديد" }
                    is Updater.Result.UpToDate -> updateStatus = "أحدث إصدار"
                    is Updater.Result.Failed -> updateStatus = r.message
                }
                if (!isFinishing) render()
            }
        }.start()
    }

    private fun install(r: Updater.Release) {
        if (!Updater.canInstall(this)) {
            Toast.makeText(this, "اسمح للتطبيق بتثبيت التطبيقات ثم ارجع", Toast.LENGTH_LONG).show()
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))) }
            return
        }
        updateStatus = "جارٍ التنزيل…"
        render()
        Thread {
            try {
                val file = Updater.download(this, r) { p -> runOnUiThread { updateStatus = "جارٍ التنزيل $p%" } }
                Updater.install(this, file)
                runOnUiThread { updateStatus = "اتبع نافذة التثبيت"; if (!isFinishing) render() }
            } catch (e: Exception) {
                runOnUiThread { updateStatus = "فشل التنزيل"; if (!isFinishing) render() }
            }
        }.start()
    }
}
