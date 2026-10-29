package com.sahra.dash

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.math.max

/** شاشة الإعدادات: الصلاحيات، الثيم والموضع، تقسيم الشاشة، النوافذ العائمة، الشكل، الخلفية، أزرار المقود. */
class MainActivity : Activity(), VehicleRepository.Listener {

    companion object {
        private const val PICK_WALLPAPER = 41
    }

    private lateinit var prefs: Prefs
    private lateinit var liveText: TextView
    private lateinit var sourceText: TextView
    private lateinit var overlayBtn: Button
    private lateinit var mediaBtn: Button
    private lateinit var keysText: TextView
    private lateinit var panelBtn: Button
    private lateinit var splitLeftBtn: Button
    private lateinit var splitRightBtn: Button
    private lateinit var floatBtn: Button
    private lateinit var wallpaperText: TextView
    private lateinit var updateText: TextView
    private lateinit var updateBtn: Button
    private var pending: Updater.Release? = null
    private var updating = false
    private val accentViews = mutableListOf<Pair<String, View>>()
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
        if (prefs.autoUpdateCheck && Updater.due(this)) checkUpdate(quiet = true)
    }

    override fun onPause() {
        VehicleRepository.removeListener(this)
        if (!OverlayService.isRunning) VehicleRepository.stop()
        super.onPause()
    }

    /** يطبق التغييرات على العرض إن كان يعمل. */
    private fun reload() {
        if (OverlayService.isRunning) OverlayService.send(this, OverlayService.ACTION_RELOAD)
    }

    // ---------------- بناء الواجهة ----------------

    private fun build(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(32))
        }
        root.addView(text(getString(R.string.app_name), 28f, fg, bold = true))
        root.addView(text("عدادات وثيمات لشاشات هافال وتانك", 15f, muted).apply { setPadding(0, dp(2), 0, dp(12)) })

        // التحديثات
        val upd = section(root, "التحديثات")
        updateText = text("الإصدار الحالي ${Updater.currentName(this)}", 14f, muted)
        upd.addView(updateText)
        updateBtn = button("تحقق من وجود تحديث") { onUpdateButton() }
        upd.addView(updateBtn)
        upd.addView(switch("تحقق تلقائيًا من التحديثات", prefs.autoUpdateCheck) { prefs.autoUpdateCheck = it })

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
        run.addView(row(
            button("مصمم العدادات") { startActivity(Intent(this, DesignerActivity::class.java)) },
            button("معلومات السيارة والتشخيص") { startActivity(Intent(this, VehicleInfoActivity::class.java)) },
        ))
        run.addView(switch("تشغيل تلقائي عند تشغيل السيارة", prefs.autostart) { prefs.autostart = it })
        run.addView(switch("وضع تجريبي (بيانات وهمية للتجربة)", prefs.demo) {
            prefs.demo = it
            VehicleRepository.forceDemo = it
            reload()
        })
        run.addView(switch("تنبيهات الوقود والحرارة والبطارية والميلان", prefs.alerts) { prefs.alerts = it; reload() })

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
        Themes.all.forEachIndexed { i, t -> tg.addView(radio(t.name, i, t.id == prefs.themeId)) }
        tg.setOnCheckedChangeListener { _, id -> prefs.themeId = Themes.all[id].id; reload() }
        themes.addView(tg)

        // الموضع وتقسيم الشاشة
        val pos = section(root, "الموضع وتقسيم الشاشة")
        val pg = RadioGroup(this)
        OverlayLayout.entries.forEachIndexed { i, l -> pg.addView(radio(l.label, i, l == prefs.layout)) }
        pg.setOnCheckedChangeListener { _, id -> prefs.layout = OverlayLayout.entries[id]; reload() }
        pos.addView(pg)
        pos.addView(text("عند اختيار نصف أو ثلث، يُفتح هذا التطبيق في باقي الشاشة:", 14f, muted).apply { setPadding(0, dp(10), 0, 0) })
        panelBtn = button("") { pickApp(allowNone = true) { prefs.panelApp = it; refreshApps(); reload() } }
        pos.addView(panelBtn)

        // تقسيم بين تطبيقين
        val split = section(root, "تقسيم الشاشة بين تطبيقين")
        splitLeftBtn = button("") { pickApp(false) { prefs.splitLeft = it; refreshApps() } }
        splitRightBtn = button("") { pickApp(false) { prefs.splitRight = it; refreshApps() } }
        split.addView(splitLeftBtn); split.addView(splitRightBtn)
        val ratioLabel = text("", 14f, muted)
        split.addView(ratioLabel)
        split.addView(seek(30, 70, (prefs.splitRatio * 100).toInt()) {
            prefs.splitRatio = it / 100f
            ratioLabel.text = "نسبة التقسيم: ${it}٪ / ${100 - it}٪"
        })
        ratioLabel.text = "نسبة التقسيم: ${(prefs.splitRatio * 100).toInt()}٪ / ${100 - (prefs.splitRatio * 100).toInt()}٪"
        split.addView(button("قسّم الشاشة الآن", primary = true) { startSplit() })

        // النوافذ العائمة
        val fl = section(root, "النوافذ العائمة")
        fl.addView(switch("عداد سرعة صغير عائم عند إخفاء العدادات", prefs.miniGauge) {
            prefs.miniGauge = it
            if (it && !OverlayService.isRunning && Settings.canDrawOverlays(this)) {
                OverlayService.send(this, OverlayService.ACTION_HIDE)
            } else reload()
        })
        floatBtn = button("") { pickApp(false) { prefs.floatingApp = it; refreshApps() } }
        fl.addView(floatBtn)
        fl.addView(button("افتح التطبيق في نافذة عائمة", primary = true) {
            if (prefs.floatingApp.isEmpty()) { toast("اختر التطبيق أولًا"); return@button }
            if (!Settings.canDrawOverlays(this)) { startOverlay(); return@button }
            OverlayService.send(this, OverlayService.ACTION_FLOAT_APP)
        })
        fl.addView(text("إذا كانت شاشتك لا تدعم النوافذ العائمة، يُفتح التطبيق بحجم كامل.", 13f, muted))

        // مصمم العدادات
        val design = section(root, "شكل العدادات")
        design.addView(text("للألوان التفصيلية وشكل الإبرة والحجم والموضع، افتح «مصمم العدادات».", 13f, muted))
        design.addView(text("اللون", 14f, muted))
        val swatches = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, dp(10)) }
        Accents.all.forEach { a ->
            val v = View(this).apply {
                contentDescription = a.name
                setOnClickListener { prefs.accent = a.hex; refreshAccents(); reload() }
            }
            accentViews.add(a.hex to v)
            swatches.addView(v, LinearLayout.LayoutParams(dp(44), dp(44)).apply { setMargins(dp(4), 0, dp(4), 0) })
        }
        design.addView(swatches)
        refreshAccents()
        design.addView(text("أقصى سرعة على العداد", 14f, muted))
        design.addView(choices(listOf(160, 200, 220, 260), prefs.speedMax) { prefs.speedMax = it; reload() })
        design.addView(text("أقصى دورات", 14f, muted))
        design.addView(choices(listOf(6000, 7000, 8000), prefs.rpmMax) { prefs.rpmMax = it; reload() })
        design.addView(switch("السرعة بالميل (mph)", prefs.mph) { prefs.mph = it; reload() })

        // الخلفية
        val wp = section(root, "الخلفية")
        wallpaperText = text("", 14f, muted)
        wp.addView(wallpaperText)
        wp.addView(row(
            button("اختر صورة") { pickWallpaper() },
            button("إزالة") {
                prefs.wallpaperFile.delete(); prefs.wallpaperVersion = prefs.wallpaperVersion + 1
                refreshWallpaper(); reload()
            },
        ))
        val dimLabel = text("تعتيم الخلفية: ${prefs.wallpaperDim}٪", 14f, muted)
        wp.addView(dimLabel)
        wp.addView(seek(0, 90, prefs.wallpaperDim) { prefs.wallpaperDim = it; dimLabel.text = "تعتيم الخلفية: $it٪" }.apply {
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    prefs.wallpaperDim = p; dimLabel.text = "تعتيم الخلفية: $p٪"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) = reload()
            })
        })
        refreshWallpaper()

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
        refreshApps()

        return ScrollView(this).apply {
            setBackgroundColor(bg)
            addView(root, MATCH_PARENT, WRAP_CONTENT)
        }
    }

    // ---------------- الإجراءات ----------------

    private fun onUpdateButton() {
        val r = pending
        if (r == null) checkUpdate(quiet = false) else installUpdate(r)
    }

    private fun checkUpdate(quiet: Boolean) {
        if (updating) return
        updating = true
        if (!quiet) updateText.text = "جارٍ التحقق…"
        Thread {
            val result = Updater.check(this)
            runOnUiThread {
                updating = false
                val cur = "الإصدار الحالي ${Updater.currentName(this)}"
                when (result) {
                    is Updater.Result.Available -> {
                        pending = result.release
                        updateText.text = "$cur — يتوفر إصدار جديد 1.0.${result.release.number}"
                        updateBtn.text = "ثبّت التحديث 1.0.${result.release.number}"
                        updateBtn.background = GradientDrawable().apply { setColor(accent); cornerRadius = dp(10).toFloat() }
                        updateBtn.setTextColor(bg)
                    }
                    is Updater.Result.UpToDate -> updateText.text = "$cur — أحدث نسخة ✓"
                    is Updater.Result.Failed -> if (!quiet) updateText.text = "$cur — ${result.message}"
                }
            }
        }.start()
    }

    private fun installUpdate(r: Updater.Release) {
        if (updating) return
        if (!Updater.canInstall(this)) {
            toast("اسمح للتطبيق بتثبيت التحديثات، ثم ارجع واضغط الزر مرة أخرى")
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        updating = true
        updateText.text = "جارٍ التنزيل… 0٪"
        Thread {
            try {
                val file = Updater.download(this, r) { p -> runOnUiThread { updateText.text = "جارٍ التنزيل… $p٪" } }
                runOnUiThread { updateText.text = "اكتمل التنزيل، أكّد التثبيت في النافذة التي ستظهر" }
                Updater.install(this, file)
            } catch (e: Exception) {
                runOnUiThread { updateText.text = "فشل التنزيل، حاول مرة أخرى" }
            } finally {
                updating = false
            }
        }.start()
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        OverlayService.send(this, OverlayService.ACTION_SHOW)
    }

    private fun startSplit() {
        if (prefs.splitLeft.isEmpty() || prefs.splitRight.isEmpty()) { toast("اختر التطبيقين أولًا"); return }
        if (OverlayService.isRunning) OverlayService.send(this, OverlayService.ACTION_HIDE)
        val size = OverlayService.screenSize(this)
        if (!AppLauncher.splitTwo(this, prefs.splitLeft, prefs.splitRight, prefs.splitRatio, size.x, size.y)) {
            toast("تعذر فتح التطبيق")
        }
    }

    private fun pickApp(allowNone: Boolean, onPick: (String) -> Unit) {
        val apps = AppLauncher.apps(this)
        val labels = (if (allowNone) listOf("بدون تطبيق") else emptyList()) + apps.map { it.label }
        AlertDialog.Builder(this)
            .setTitle("اختر تطبيقًا")
            .setItems(labels.toTypedArray()) { _, which ->
                if (allowNone && which == 0) onPick("") else onPick(apps[which - if (allowNone) 1 else 0].packageName)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun pickWallpaper() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        try { startActivityForResult(i, PICK_WALLPAPER) } catch (_: Exception) {
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), PICK_WALLPAPER)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != PICK_WALLPAPER || resultCode != RESULT_OK || uri == null) return
        Thread {
            val ok = saveWallpaper(uri)
            runOnUiThread {
                if (ok) { prefs.wallpaperVersion = prefs.wallpaperVersion + 1; refreshWallpaper(); reload(); toast("تم تعيين الخلفية") }
                else toast("تعذرت قراءة الصورة")
            }
        }.start()
    }

    /** يصغّر الصورة إلى عرض الشاشة تقريبًا ويحفظها داخل التطبيق. */
    private fun saveWallpaper(uri: Uri): Boolean = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2048) sample *= 2
        val bmp = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        if (bmp == null) false else {
            prefs.wallpaperFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            true
        }
    } catch (_: Exception) { false }

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

    // ---------------- تحديث النصوص ----------------

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

    private fun refreshApps() {
        fun name(p: String) = if (p.isEmpty()) "لم يُختر" else AppLauncher.label(this, p)
        panelBtn.text = "التطبيق المجاور: ${if (prefs.panelApp.isEmpty()) "بدون" else name(prefs.panelApp)}"
        splitLeftBtn.text = "التطبيق الأيسر: ${name(prefs.splitLeft)}"
        splitRightBtn.text = "التطبيق الأيمن: ${name(prefs.splitRight)}"
        floatBtn.text = "تطبيق النافذة العائمة: ${name(prefs.floatingApp)}"
    }

    private fun refreshAccents() {
        accentViews.forEach { (hex, v) ->
            v.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(hex))
                if (hex.equals(prefs.accent, true)) setStroke(dp(4), Color.WHITE) else setStroke(dp(1), Color.parseColor("#55000000"))
            }
        }
    }

    private fun refreshWallpaper() {
        wallpaperText.text = if (prefs.wallpaperFile.exists()) "✓ خلفية مخصصة مفعّلة" else "بدون خلفية (لون الثيم الأصلي)"
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

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

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

    /** صف أزرار لاختيار قيمة واحدة من عدة قيم. */
    private fun choices(values: List<Int>, current: Int, onPick: (Int) -> Unit): RadioGroup {
        val g = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        values.forEachIndexed { i, v -> g.addView(radio(v.toString(), i, v == current)) }
        g.setOnCheckedChangeListener { _, id -> onPick(values[id]) }
        return g
    }

    private fun seek(lo: Int, hi: Int, value: Int, onChange: (Int) -> Unit) = SeekBar(this).apply {
        this.max = hi - lo
        progress = value - lo
        setPadding(dp(8), dp(12), dp(8), dp(12))
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) = onChange(p + lo)
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
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
