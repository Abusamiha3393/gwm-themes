package com.sahra.split

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

/** الإعدادات: الصلاحيات، الإعدادات المسبقة، نهار/ليل، النوافذ، الوقود، الموقع، التحديثات. */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var body: LinearLayout
    private var scrollY = 0
    private var pending: Updater.Release? = null
    private var updateStatus = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        Type.init(this)
        window.statusBarColor = Palette.gantry
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    override fun onResume() {
        super.onResume()
        render()
        if (prefs.autoUpdateCheck && Updater.due(this)) checkUpdate()
    }

    private fun render() {
        val scroll = ScrollView(this).apply { background = AsphaltDrawable(this@SettingsActivity); isVerticalScrollBarEnabled = false }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(40))
        }
        scroll.addView(body)
        build()
        setContentView(scroll)
        scroll.post { scroll.scrollTo(0, scrollY) }
        scroll.viewTreeObserver.addOnScrollChangedListener { scrollY = scroll.scrollY }
    }

    private fun build() {
        body.addView(header(R.drawable.ic_settings, "الإعدادات", Palette.blue) { finish() })
        val st = VehicleRepository.state
        note(when {
            st.live -> "متصل ببيانات السيارة"
            st.demo -> "بيانات تجريبية — لم يُعثر على خدمة السيارة أو الوضع التجريبي مفعّل"
            else -> "بانتظار بيانات السيارة…"
        }, if (st.live) Palette.white else Palette.muted)

        // ---------------- الصلاحيات ----------------
        section("الصلاحيات")
        permission("العرض فوق التطبيقات", "للكرة العائمة واللوحة العائمة", Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        permission("قراءة الإشعارات", "لمعرفة الأغنية الحالية والتحكم بها", MediaTracker.hasAccess(this)) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        permission("إمكانية الوصول", "لزر «رجوع» في الكرة العائمة", BackService.enabled()) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        val hasLoc = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        permission("الموقع", "للطقس وأوقات الشروق والغروب", hasLoc) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 5)
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val ok = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            permission("الإشعارات", "لتنبيهات التذكيرات", ok) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 6)
            }
        }
        permission("الشاشة الرئيسية الافتراضية", "اختر «صحراء سبليت» ليكون اللانشر", isDefaultHome()) {
            runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }

        // ---------------- الإعداد المسبق ----------------
        val idx = prefs.presetIndex
        val p = prefs.preset
        section("الإعداد المسبق الحالي: ${p.name}")
        choice("اختر الإعداد", prefs.presets().map { it.name }, idx) { prefs.presetIndex = it; render() }
        row("الاسم", p.name) {
            val input = EditText(this).apply { setText(p.name); setTextColor(Palette.white) }
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("اسم الإعداد").setView(input)
                .setPositiveButton("حفظ") { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isNotEmpty()) { prefs.updatePreset { it.copy(name = name) }; render() }
                }
                .setNegativeButton("إلغاء", null).show()
        }
        row("تطبيق الملاحة", AppLauncher.label(this, p.navApp)) {
            AppPicker.show(this, "تطبيق الملاحة", true) { pkg -> prefs.updatePreset { it.copy(navApp = pkg) }; render() }
        }
        row("تطبيق الموسيقى", AppLauncher.label(this, p.musicApp)) {
            AppPicker.show(this, "تطبيق الموسيقى", true) { pkg -> prefs.updatePreset { it.copy(musicApp = pkg) }; render() }
        }
        seek("نسبة الملاحة من المساحة", 30, 70, (p.ratio * 100).toInt(), { "$it%" }) { v ->
            prefs.updatePreset { it.copy(ratio = v / 100f) }
        }
        choice("مكان لوحة المعلومات", PanelSide.entries.map { it.label }, p.side.ordinal) { i ->
            prefs.updatePreset { it.copy(side = PanelSide.entries[i]) }; render()
        }
        seek("عرض لوحة المعلومات", 20, 45, (p.panelWidth * 100).toInt(), { "$it%" }) { v ->
            prefs.updatePreset { it.copy(panelWidth = v / 100f) }
        }
        row("نسخ هذا الإعداد إلى…", "") {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("انسخ «${p.name}» إلى")
                .setItems(prefs.presets().map { it.name }.toTypedArray()) { _, i ->
                    if (i != idx) { prefs.savePreset(i, p.copy(name = prefs.presets()[i].name)); toast("تم النسخ") }
                }.show()
        }

        // ---------------- نهار وليل ----------------
        section("التبديل التلقائي بين النهار والليل")
        toggle("تفعيل", prefs.autoDayNight) { prefs.autoDayNight = it; Sun.applyDayNight(this); render() }
        val names = prefs.presets().map { it.name }
        choice("إعداد النهار", names, prefs.dayPreset) { prefs.dayPreset = it; Sun.applyDayNight(this); render() }
        choice("إعداد الليل", names, prefs.nightPreset) { prefs.nightPreset = it; Sun.applyDayNight(this); render() }
        val now = Calendar.getInstance()
        note("الشروق اليوم ${Sun.format(Sun.minutes(now, prefs.lat.toDouble(), prefs.lon.toDouble(), true))} · " +
            "الغروب ${Sun.format(Sun.minutes(now, prefs.lat.toDouble(), prefs.lon.toDouble(), false))}", Palette.muted)

        // ---------------- النوافذ ----------------
        section("النوافذ")
        val modes = listOf(AppLauncher.MODE_FREEFORM, AppLauncher.MODE_SPLIT_PRIMARY, AppLauncher.MODE_FULLSCREEN)
        choice("طريقة فتح التطبيقين", listOf("نوافذ بحدود", "تقسيم أندرويد", "كاملة"), modes.indexOf(prefs.windowMode).coerceAtLeast(0)) {
            prefs.windowMode = modes[it]; render()
        }
        note("إن لم تحترم الشاشة حدود النوافذ، جرّب «تقسيم أندرويد»، أو اجعل لوحة المعلومات عائمة.", Palette.muted)
        toggle("فتح التقسيم تلقائيًا عند تشغيل السيارة", prefs.autoSplit) { prefs.autoSplit = it }
        toggle("الكرة العائمة (عداد سرعة وقائمة سريعة)", prefs.ball) { prefs.ball = it; OverlayService.sync(this) }
        toggle("لوحة المعلومات عائمة فوق التطبيقات", prefs.floatingPanel) { prefs.floatingPanel = it; OverlayService.sync(this) }
        toggle("التشغيل التلقائي مع السيارة", prefs.autostart) { prefs.autostart = it }

        // ---------------- الساعة والوقود ----------------
        section("الساعة والوقود")
        choice("شكل الساعة", listOf("أرقام", "عقارب"), prefs.clockStyle) { prefs.clockStyle = it; render() }
        seek("سعة الخزان", 40, 140, prefs.tankLiters.toInt(), { "$it لتر" }) { prefs.tankLiters = it.toFloat() }
        val refuel = if (prefs.refuelAt > 0) android.text.format.DateFormat.format("yyyy/MM/dd HH:mm", prefs.refuelAt).toString() else "لم تُرصد بعد"
        row("آخر تعبئة", refuel) {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setMessage("اعتبار الآن نقطة تعبئة جديدة؟ يبدأ حساب الاستهلاك من جديد.")
                .setPositiveButton("نعم") { _, _ ->
                    val s = VehicleRepository.state
                    if (!s.odometerKm.isNaN() && !s.fuelPercent.isNaN()) {
                        prefs.refuelOdo = s.odometerKm; prefs.refuelFuel = s.fuelPercent; prefs.refuelAt = System.currentTimeMillis()
                        render()
                    } else toast("لا توجد قراءة وقود حالية")
                }
                .setNegativeButton("لا", null).show()
        }

        // ---------------- الموقع ----------------
        section("الموقع")
        note("المستخدم للطقس والشروق: ${num(prefs.lat, 3)}, ${num(prefs.lon, 3)}", Palette.white)
        row("تحديث من موقع السيارة الآن", "") {
            Sun.refreshLocation(this)
            Weather.ensure(this, force = true)
            render()
        }

        // ---------------- متقدم ----------------
        section("متقدم")
        toggle("الوضع التجريبي (بيانات وهمية)", prefs.demo) { prefs.demo = it; VehicleRepository.forceDemo = it }

        // ---------------- التحديثات ----------------
        section("التحديثات")
        note("الإصدار الحالي ${Updater.currentName(this)}${if (updateStatus.isNotEmpty()) " · $updateStatus" else ""}", Palette.white)
        toggle("التحقق تلقائيًا كل 6 ساعات", prefs.autoUpdateCheck) { prefs.autoUpdateCheck = it }
        val rel = pending
        if (rel != null) row("ثبّت الإصدار 0.1.${rel.number}", "") { install(rel) }
        else row("تحقق الآن", "") { checkUpdate() }
    }

    // ---------------- أدوات البناء ----------------

    /** عنوان القسم: لسان أخضر صغير كلسان لوحة المخرج. */
    private fun section(title: String) {
        val tab = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = smallSign(Palette.green)
            setPadding(dp(18), dp(10), dp(18), dp(10))
        }
        tab.addView(label(title, 17f, Palette.white, true))
        val row = LinearLayout(this).apply { setPadding(0, dp(28), 0, dp(10)) }
        row.addView(tab)
        body.addView(row)
    }

    private fun note(text: String, color: Int) {
        body.addView(label(text, 14f, color).apply { setPadding(dp(4), dp(6), dp(4), dp(6)); setLineSpacing(dp(3f), 1f) })
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(Palette.shoulder, dp(12f), dp(1), Palette.kerb)
        setPadding(dp(20), dp(14), dp(20), dp(14))
        minimumHeight = dp(68)
    }

    private fun add(v: View) = body.addView(v, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })

    private fun row(title: String, value: String, onClick: () -> Unit) {
        val c = card()
        c.addView(label(title, 17f), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        if (value.isNotEmpty()) c.addView(label(value, 16f, Palette.muted).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(dp(12), 0, dp(10), 0)
            maxWidth = dp(320)
        })
        c.addView(icon(R.drawable.ic_chev, 22, Palette.muted))
        c.setOnClickListener { onClick() }
        add(c)
    }

    private fun permission(title: String, desc: String, ok: Boolean, onClick: () -> Unit) {
        val c = card()
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(label(title, 17f, Palette.white, true))
        texts.addView(label(desc, 14f, Palette.muted).apply { setPadding(0, dp(4), 0, 0) })
        c.addView(texts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        if (ok) {
            c.addView(icon(R.drawable.ic_check, 24, Palette.white), LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(8) })
            c.addView(label("ممنوحة", 15f, Palette.white))
        } else {
            val badge = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(Palette.amber, dp(8f))
                setPadding(dp(14), dp(8), dp(14), dp(8))
            }
            badge.addView(label("امنح", 15f, Palette.ink, true))
            c.addView(badge)
        }
        c.setOnClickListener { runCatching { onClick() } }
        add(c)
    }

    private fun toggle(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
        val c = card()
        c.addView(label(title, 17f), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val sw = Switch(this).apply {
            isChecked = value
            thumbTintList = android.content.res.ColorStateList(states, intArrayOf(Palette.white, Palette.muted))
            trackTintList = android.content.res.ColorStateList(states, intArrayOf(Palette.green, Palette.laneDash))
            setOnCheckedChangeListener { _, b -> onChange(b) }
        }
        c.addView(sw)
        c.setOnClickListener { sw.toggle() }
        add(c)
    }

    /** اختيار من عدة: كالمسارات، المختار لوحة خضراء. */
    private fun choice(title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Palette.shoulder, dp(12f), dp(1), Palette.kerb)
            setPadding(dp(20), dp(14), dp(20), dp(16))
        }
        c.addView(label(title, 17f))
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        options.forEachIndexed { i, o ->
            val on = i == selected
            chips.addView(label(o, 16f, if (on) Palette.white else Palette.muted, on).apply {
                gravity = Gravity.CENTER
                background = if (on) smallSign(Palette.green) else rounded(Palette.asphalt, dp(10f), dp(2), Palette.kerb)
                setPadding(dp(18), dp(12), dp(18), dp(12))
                minHeight = dp(52)
                setOnClickListener { if (!on) onPick(i) }
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8); topMargin = dp(12) })
        }
        c.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) })
        add(c)
    }

    private fun seek(title: String, min: Int, max: Int, value: Int, fmt: (Int) -> String, onChange: (Int) -> Unit) {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Palette.shoulder, dp(12f), dp(1), Palette.kerb)
            setPadding(dp(20), dp(14), dp(20), dp(14))
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(label(title, 17f), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val v = label(fmt(value), 22f, Palette.white, true, digitsFace = true)
        top.addView(v)
        c.addView(top)
        c.addView(SeekBar(this).apply {
            this.max = max - min
            progress = (value - min).coerceIn(0, max - min)
            progressTintList = android.content.res.ColorStateList.valueOf(Palette.green)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Palette.laneDash)
            thumbTintList = android.content.res.ColorStateList.valueOf(Palette.white)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { v.text = fmt(p + min) }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) { onChange((sb?.progress ?: 0) + min) }
            })
        }, LinearLayout.LayoutParams(MATCH_PARENT, dp(48)).apply { topMargin = dp(6) })
        add(c)
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    private fun isDefaultHome(): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val r = packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
        return r?.activityInfo?.packageName == packageName
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Sun.refreshLocation(this)
        render()
    }

    // ---------------- التحديث ----------------

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
            toast("اسمح للتطبيق بتثبيت التطبيقات ثم ارجع")
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))) }
            return
        }
        updateStatus = "جارٍ التنزيل…"
        render()
        Thread {
            try {
                val f = Updater.download(this, r) { p -> runOnUiThread { updateStatus = "جارٍ التنزيل $p%" } }
                Updater.install(this, f)
                runOnUiThread { updateStatus = "اتبع نافذة التثبيت"; if (!isFinishing) render() }
            } catch (e: Exception) {
                runOnUiThread { updateStatus = "فشل التنزيل"; if (!isFinishing) render() }
            }
        }.start()
    }
}
