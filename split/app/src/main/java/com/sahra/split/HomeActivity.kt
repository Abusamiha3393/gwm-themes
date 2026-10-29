package com.sahra.split

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * الشاشة الرئيسية (لانشر): لوحة المعلومات على جانب، وخانتا التطبيقين، وشريط أوامر.
 * زر «تقسيم» يفتح التطبيقين في مكانيهما بجانب اللوحة.
 */
class HomeActivity : Activity() {

    companion object {
        /** يمنع إعادة الفتح التلقائي كلما رجع السائق للشاشة الرئيسية؛ يكفي مرة بعد التشغيل. */
        @Volatile private var autoSplitDone = false
    }

    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        VehicleRepository.start(this)
    }

    override fun onResume() {
        super.onResume()
        Sun.refreshLocation(this)
        Sun.applyDayNight(this)
        setContentView(build())
        OverlayService.sync(this)
        if (!autoSplitDone && prefs.autoSplit && intent?.getBooleanExtra("no_split", false) != true) {
            autoSplitDone = true
            window.decorView.postDelayed({ split() }, 1200)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.getBooleanExtra("split", false) == true) window.decorView.postDelayed({ split() }, 300)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // نحن الشاشة الرئيسية: لا شيء خلفنا
    }

    private fun split() {
        val p = prefs.preset
        if (p.navApp.isEmpty() && p.musicApp.isEmpty()) {
            Toast.makeText(this, "اختر تطبيقي الملاحة والموسيقى أولًا (اضغط مطولًا على الخانة)", Toast.LENGTH_LONG).show()
            return
        }
        AppLauncher.applyPreset(this, p, prefs.windowMode)
    }

    private fun build(): View {
        val p = prefs.preset
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setBackgroundColor(Palette.bg)
        }
        val size = AppLauncher.screenSize(this)
        val showPanel = p.side != PanelSide.NONE && !prefs.floatingPanel
        val panel = if (showPanel) SplitInfoView(this, p.page) { page -> prefs.updatePreset { it.copy(page = page) } }.apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        } else null
        val main = buildMain(p)
        val panelLp = LinearLayout.LayoutParams((size.x * p.panelWidth).toInt(), MATCH_PARENT)
        val mainLp = LinearLayout.LayoutParams(0, MATCH_PARENT, 1f)
        if (panel != null && p.side == PanelSide.LEFT) root.addView(panel, panelLp)
        root.addView(main, mainLp)
        if (panel != null && p.side == PanelSide.RIGHT) root.addView(panel, panelLp)
        return root
    }

    private fun buildMain(p: Preset): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }

        // شريط الإعدادات المسبقة
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        prefs.presets().forEachIndexed { i, preset ->
            val on = i == prefs.presetIndex
            chips.addView(TextView(this).apply {
                text = preset.name
                textSize = 15f
                setTextColor(if (on) Palette.bg else Palette.fg)
                background = rounded(if (on) Palette.accent else Palette.card, dp(16f))
                setPadding(dp(14), dp(8), dp(14), dp(8))
                setOnClickListener {
                    prefs.presetIndex = i
                    if (prefs.autoDayNight) {
                        prefs.autoDayNight = false
                        Toast.makeText(this@HomeActivity, "أُوقف التبديل التلقائي نهار/ليل", Toast.LENGTH_SHORT).show()
                    }
                    setContentView(build())
                    OverlayService.sync(this@HomeActivity)
                }
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(6) })
        }
        col.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) })

        if (!Settings.canDrawOverlays(this) || !MediaTracker.hasAccess(this)) {
            col.addView(TextView(this).apply {
                text = "⚠ أكمل الصلاحيات من الإعدادات حتى تعمل كل الخصائص"
                textSize = 15f
                setTextColor(Palette.bg)
                background = rounded(Palette.accent, dp(10f))
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setOnClickListener { startActivity(Intent(this@HomeActivity, SettingsActivity::class.java)) }
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
        }

        val due = Logbook.dueReminders(this)
        if (due.isNotEmpty()) {
            col.addView(TextView(this).apply {
                text = "⏰ " + due.joinToString(" · ") { it.text }
                textSize = 15f
                setTextColor(Palette.fg)
                background = rounded(Palette.warn, dp(10f))
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setOnClickListener { startActivity(Intent(this@HomeActivity, LogActivity::class.java).putExtra("tab", 2)) }
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
        }

        // خانتا التطبيقين، بنفس نسبة التقسيم. الترتيب الفعلي يتبع الشاشة (يسار ثم يمين)
        val slots = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        val navW = if (p.musicApp.isEmpty()) 1f else if (p.navApp.isEmpty()) 0f else p.ratio
        if (p.navApp.isNotEmpty() || p.musicApp.isEmpty()) {
            slots.addView(slot("الملاحة", p.navApp, true), LinearLayout.LayoutParams(0, MATCH_PARENT, navW.coerceAtLeast(0.3f)).apply { marginEnd = dp(8) })
        }
        if (p.musicApp.isNotEmpty() || p.navApp.isEmpty()) {
            slots.addView(slot("الموسيقى", p.musicApp, false), LinearLayout.LayoutParams(0, MATCH_PARENT, (1f - navW).coerceAtLeast(0.3f)))
        }
        col.addView(slots, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(10); bottomMargin = dp(10) })

        // شريط الأوامر
        val dock = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val lp = { LinearLayout.LayoutParams(0, dp(60), 1f).apply { setMargins(dp(4), 0, dp(4), 0) } }
        dock.addView(pill("⧉  تقسيم") { split() }, lp())
        dock.addView(pill("▦  التطبيقات") { startActivity(Intent(this, AppsActivity::class.java)) }, lp())
        dock.addView(pill("⇄  تبديل") {
            prefs.updatePreset { it.copy(navApp = it.musicApp, musicApp = it.navApp) }
            setContentView(build())
        }, lp())
        dock.addView(pill("☰  السجل") { startActivity(Intent(this, LogActivity::class.java)) }, lp())
        dock.addView(pill("⚙  الإعدادات") { startActivity(Intent(this, SettingsActivity::class.java)) }, lp())
        col.addView(dock, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return col
    }

    private fun slot(title: String, pkg: String, nav: Boolean): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = rounded(Palette.card, dp(18f), dp(1), Palette.line)
        }
        val icon = ImageView(this).apply { setImageDrawable(AppLauncher.icon(this@HomeActivity, pkg)) }
        if (pkg.isNotEmpty()) box.addView(icon, LinearLayout.LayoutParams(dp(72), dp(72)))
        box.addView(label(if (pkg.isEmpty()) "اختر تطبيق $title" else AppLauncher.label(this, pkg), 18f, Palette.fg, true).apply {
            gravity = Gravity.CENTER; setPadding(dp(8), dp(10), dp(8), 0)
        })
        box.addView(label(if (pkg.isEmpty()) "اضغط مطولًا للاختيار" else "$title · المس للفتح · مطولًا للتغيير", 13f, Palette.muted).apply {
            gravity = Gravity.CENTER
        })
        box.setOnClickListener {
            if (pkg.isEmpty()) pick(title, nav) else {
                val size = AppLauncher.screenSize(this)
                val l = AppLauncher.layoutFor(prefs.preset, size.x, size.y, dp(4))
                AppLauncher.launch(this, pkg, if (nav) l.nav else l.music, prefs.windowMode)
            }
        }
        box.setOnLongClickListener { pick(title, nav); true }
        return box
    }

    private fun pick(title: String, nav: Boolean) {
        AppPicker.show(this, "تطبيق $title", allowNone = true) { pkg ->
            prefs.updatePreset { if (nav) it.copy(navApp = pkg) else it.copy(musicApp = pkg) }
            setContentView(build())
        }
    }
}
