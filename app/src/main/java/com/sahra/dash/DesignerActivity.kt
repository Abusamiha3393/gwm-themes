package com.sahra.dash

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * مصمم العدادات: معاينة حية للثيم بمقاس شاشة السيارة، وأدوات لتغيير الألوان والإبرة والحجم والموضع.
 * يحفظ التغييرات فورًا، ويطبقها على العرض عند الخروج.
 */
class DesignerActivity : Activity(), VehicleRepository.Listener {

    private lateinit var prefs: Prefs
    private lateinit var store: DesignStore
    private var design = Design()
    private lateinit var preview: WebView
    private var ready = false
    private lateinit var controls: LinearLayout

    private val bg = Color.parseColor("#14100C")
    private val card = Color.parseColor("#211A14")
    private val accent = Color.parseColor("#E8A24A")
    private val fg = Color.parseColor("#F4E3C3")
    private val muted = Color.parseColor("#A8957A")

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        store = DesignStore(this)
        design = store.current
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val screen = OverlayService.screenSize(this)
        val landscape = screen.x > screen.y
        // المعاينة بنسبة شاشة السيارة 1920×720 حتى يظهر الثيم بتخطيطه الكامل
        val pw = if (landscape) (screen.x * 0.58f).roundToInt() else screen.x - dp(24)
        val ph = min((pw * 720f / 1920f).roundToInt(), (screen.y * 0.8f).roundToInt())

        preview = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = true
            addJavascriptInterface(PreviewHost(), "SahraHost")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) { ready = true; push() }
            }
        }

        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(text("المعاينة", 16f, accent, true))
            addView(preview, LinearLayout.LayoutParams(pw, ph).apply { topMargin = dp(8) })
            addView(themePicker())
        }

        controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(32))
        }
        buildControls()
        val scroll = ScrollView(this).apply { addView(controls, MATCH_PARENT, WRAP_CONTENT) }

        val root = LinearLayout(this).apply {
            orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setBackgroundColor(bg)
            addView(left, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
            addView(scroll, LinearLayout.LayoutParams(if (landscape) 0 else MATCH_PARENT, if (landscape) MATCH_PARENT else 0, 1f))
        }
        setContentView(root)
        loadTheme()
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
        if (OverlayService.isRunning) OverlayService.send(this, OverlayService.ACTION_RELOAD)
        super.onPause()
    }

    override fun onDestroy() {
        preview.destroy()
        super.onDestroy()
    }

    private fun loadTheme() {
        ready = false
        preview.loadUrl("file:///android_asset/" + Themes.byId(prefs.themeId).asset)
    }

    override fun onState(state: VehicleState) = push()

    private fun push() {
        if (!ready) return
        // المعاينة دائمًا بملء الشاشة بغض النظر عن موضع العرض المختار
        val data = Payload.build(prefs, design).put("layout", "full")
        preview.evaluateJavascript(Payload.script(data), null)
    }

    private fun update(d: Design) {
        design = d
        store.current = d
        push()
    }

    // ---------------- الأدوات ----------------

    private fun buildControls() {
        controls.removeAllViews()
        controls.addView(text("مصمم العدادات", 24f, fg, true))
        controls.addView(text("التغييرات تُحفظ تلقائيًا وتُطبّق على العرض عند الخروج.", 13f, muted))

        colorRow("لون الإبرة", design.needleColor) { update(design.copy(needleColor = it)) }
        colorRow("لون العلامات", design.tickColor) { update(design.copy(tickColor = it)) }
        colorRow("لون الأرقام", design.numberColor) { update(design.copy(numberColor = it)) }
        colorRow("لون اللوحات والإطار", design.panelColor) { update(design.copy(panelColor = it)) }

        header("شكل الإبرة")
        val g = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        Design.NEEDLES.forEachIndexed { i, (id, label) -> g.addView(radio(label, i, design.needleStyle == id)) }
        g.setOnCheckedChangeListener { _, i -> update(design.copy(needleStyle = Design.NEEDLES[i].first)) }
        controls.addView(g)

        header("حجم العدادات")
        val sizeLabel = text("${design.scale}٪", 14f, muted)
        controls.addView(sizeLabel)
        controls.addView(seek(70, 120, design.scale) { sizeLabel.text = "$it٪"; update(design.copy(scale = it)) })

        header("تحريك العدادات أفقيًا")
        val shiftLabel = text(shiftText(design.shift), 14f, muted)
        controls.addView(shiftLabel)
        controls.addView(seek(-20, 20, design.shift) { shiftLabel.text = shiftText(it); update(design.copy(shift = it)) })

        controls.addView(switch("تبديل مكان عداد السرعة والدورات", design.swap) { update(design.copy(swap = it)) })

        header("لون الثيم")
        val sw = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Accents.all.forEach { a ->
            sw.addView(swatch(a.hex, a.hex.equals(prefs.accent, true)) {
                prefs.accent = a.hex; buildControls(); push()
            }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { setMargins(dp(3), dp(4), dp(3), dp(4)) })
        }
        controls.addView(HorizontalScrollView(this).apply { addView(sw) })

        header("التصاميم المحفوظة")
        val presets = store.presets()
        if (presets.isEmpty()) controls.addView(text("لا توجد تصاميم محفوظة بعد.", 14f, muted))
        presets.forEach { p ->
            controls.addView(row(
                button("تطبيق: ${p.name}", weight = 3f) { update(p); buildControls(); toast("تم تطبيق ${p.name}") },
                button("حذف", weight = 1f) { store.deletePreset(p.name); buildControls() },
            ))
        }
        controls.addView(row(
            button("حفظ التصميم باسم…", primary = true) { askName() },
            button("إعادة الافتراضي") { update(Design()); buildControls() },
        ))
    }

    private fun shiftText(v: Int) = when {
        v == 0 -> "في المنتصف"
        v > 0 -> "يمينًا $v٪"
        else -> "يسارًا ${-v}٪"
    }

    private fun askName() {
        val input = EditText(this).apply { hint = "مثلًا: ليلي"; setText(design.name) }
        AlertDialog.Builder(this)
            .setTitle("اسم التصميم")
            .setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "تصميم ${store.presets().size + 1}" }
                val d = design.copy(name = name)
                store.savePreset(d); update(d); buildControls()
                toast("تم الحفظ")
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun themePicker(): View {
        val g = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        Themes.all.forEachIndexed { i, t -> g.addView(radio(t.name.substringBefore(" —"), i, t.id == prefs.themeId)) }
        g.setOnCheckedChangeListener { _, i -> prefs.themeId = Themes.all[i].id; loadTheme() }
        return HorizontalScrollView(this).apply { addView(g); setPadding(0, dp(8), 0, 0) }
    }

    private fun colorRow(title: String, current: String, onPick: (String) -> Unit) {
        header(title)
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        r.addView(TextView(this).apply {
            text = "افتراضي"; textSize = 12f; gravity = Gravity.CENTER
            setTextColor(if (current.isEmpty()) bg else fg)
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(if (current.isEmpty()) accent else Color.parseColor("#2E251C"))
            }
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { onPick(""); buildControls() }
        }, LinearLayout.LayoutParams(WRAP_CONTENT, dp(40)).apply { setMargins(dp(3), dp(4), dp(3), dp(4)) })
        Design.COLORS.forEach { hex ->
            r.addView(swatch(hex, hex.equals(current, true)) { onPick(hex); buildControls() },
                LinearLayout.LayoutParams(dp(40), dp(40)).apply { setMargins(dp(3), dp(4), dp(3), dp(4)) })
        }
        controls.addView(HorizontalScrollView(this).apply { addView(r) })
    }

    /** المعاينة لا تحتاج أوامر حقيقية؛ وجود هذا الكائن يمنع البيانات الوهمية في common.js. */
    inner class PreviewHost {
        @JavascriptInterface fun close() {}
        @JavascriptInterface fun nextTheme() {}
        @JavascriptInterface fun media(cmd: String) {}
        @JavascriptInterface fun openApp(pkg: String) {}
        @JavascriptInterface fun floatApp() {}
        @JavascriptInterface fun openSettings() {}
        @JavascriptInterface fun themes(): String = "[]"
    }

    // ---------------- عناصر الواجهة ----------------

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun header(s: String) = controls.addView(text(s, 15f, accent, true).apply { setPadding(0, dp(16), 0, dp(4)) })

    private fun swatch(hex: String, selected: Boolean, onClick: () -> Unit) = View(this).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(hex))
            if (selected) setStroke(dp(4), accent) else setStroke(dp(1), Color.parseColor("#55FFFFFF"))
        }
        setOnClickListener { onClick() }
    }

    private fun button(label: String, primary: Boolean = false, weight: Float = 1f, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 15f
        setTextColor(if (primary) bg else fg)
        background = GradientDrawable().apply {
            setColor(if (primary) accent else Color.parseColor("#2E251C")); cornerRadius = dp(10).toFloat()
        }
        setPadding(dp(12), dp(10), dp(12), dp(10))
        tag = weight
        setOnClickListener { onClick() }
    }

    private fun row(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEachIndexed { i, v ->
            addView(v, LinearLayout.LayoutParams(0, WRAP_CONTENT, (v.tag as? Float) ?: 1f).apply {
                setMargins(if (i > 0) dp(8) else 0, dp(6), 0, dp(6))
            })
        }
    }

    private fun seek(lo: Int, hi: Int, value: Int, onChange: (Int) -> Unit) = SeekBar(this).apply {
        max = hi - lo
        progress = value - lo
        setPadding(dp(8), dp(12), dp(8), dp(12))
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) onChange(p + lo) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    @Suppress("DEPRECATION")
    private fun switch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
        text = label; textSize = 15f; setTextColor(fg); isChecked = checked
        setPadding(0, dp(12), 0, dp(12))
        setOnCheckedChangeListener { _, v -> onChange(v) }
    }

    private fun radio(label: String, id: Int, checked: Boolean) = RadioButton(this).apply {
        this.id = id; text = label; textSize = 15f; setTextColor(fg); isChecked = checked
        setPadding(dp(6), dp(8), dp(12), dp(8))
    }
}
