package com.sahra.split

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * محرر ثيم العدادات، في شاشة مستقلة:
 * معاينة حية للثيم، وقائمة عناصره (مع إظهار وإخفاء)، وتحريك العنصر المحدد وتكبيره،
 * ونافذة الملاحة (موضعها ومقاسها)، والخلفية. كل تعديل يُحفظ للثيم ويظهر على العدادات خلال ثانية،
 * والعنصر المحدد يُحاط بإطار متقطع على العدادات نفسها حتى يراه السائق في مكانه الحقيقي.
 */
class ThemeEditorActivity : Activity() {

    private data class Item(val id: String, val name: String, val hiddenByDefault: Boolean)

    companion object {
        private const val NAV = "nav"
        private val STEPS = listOf(1, 10, 50)
    }

    private lateinit var prefs: Prefs
    private lateinit var preview: ThemeView
    private lateinit var list: LinearLayout
    private lateinit var controls: LinearLayout
    private lateinit var themeName: TextView
    private val main = Handler(Looper.getMainLooper())
    private var relayoutPending = false
    private val relayout = Runnable { relayoutPending = false; NavCluster.relayout(this) }

    private var items = listOf<Item>()
    private var backgrounds = listOf<Pair<String, String>>()
    private var canvasW = 1920f
    private var canvasH = 720f
    private var sel = NAV
    private var step = 10
    private var edit = JSONObject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        Type.init(this)
        window.statusBarColor = Palette.gantry
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        VehicleRepository.start(this)
        edit = loadEdit()
        buildUi()
        renderList()
        renderControls()
    }

    override fun onResume() {
        super.onResume()
        ClusterPayload.editSel = sel
        pushAll()
    }

    override fun onPause() {
        // أزل إطار التحديد من العدادات، وطبّق مقاس الملاحة المعلّق فورًا
        ClusterPayload.editSel = ""
        ClusterService.instance?.pushNow()
        if (relayoutPending) { main.removeCallbacks(relayout); relayout.run() }
        super.onPause()
    }

    // ---------------- الواجهة ----------------

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AsphaltDrawable(this@ThemeEditorActivity)
            setPadding(dp(20), dp(14), dp(20), dp(14))
        }

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(header(R.drawable.ic_move, "محرر ثيم العدادات", Palette.green) { finish() },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        top.addView(themeStepper(), LinearLayout.LayoutParams(WRAP_CONTENT, dp(68)).apply { marginStart = dp(12) })
        root.addView(top)

        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(14), 0, 0) }

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val listScroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(list) }
        body.addView(listScroll, LinearLayout.LayoutParams(dp(320), MATCH_PARENT).apply { marginEnd = dp(16) })

        val side = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        preview = ThemeView(this, interactive = false)
        val frame = AspectFrame(this, ClusterService.aspect(this)).apply {
            background = rounded(Color.BLACK, dp(10f), dp(2), Palette.kerb)
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        frame.addView(preview, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        side.addView(frame, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(12), 0, 0) }
        val controlsScroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(controls) }
        side.addView(controlsScroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        body.addView(side, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
        root.addView(body, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)

        preview.onElements = { json -> onElements(json) }
        preview.load(prefs.clusterTheme)
    }

    /** ‹ اسم الثيم › لاختيار الثيم الذي يُحرَّر (وهو نفسه الظاهر على العدادات). */
    private fun themeStepper(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        themeName = label(ClusterThemes.byId(prefs.clusterTheme).name, 18f, Palette.white, bold = true).apply {
            gravity = Gravity.CENTER
            minWidth = dp(150)
            setPadding(dp(10), 0, dp(10), 0)
        }
        // في العربية: السابق إلى اليمين (›) والتالي إلى اليسار (‹)
        box.addView(signButton(null, "›", Palette.blue) { switchTheme(-1) }, LinearLayout.LayoutParams(dp(64), dp(64)))
        box.addView(themeName)
        box.addView(signButton(null, "‹", Palette.blue) { switchTheme(1) }, LinearLayout.LayoutParams(dp(64), dp(64)))
        return box
    }

    private fun renderList() {
        list.removeAllViews()
        list.addView(itemRow(NAV, "نافذة الملاحة", null))
        if (items.isEmpty()) {
            list.addView(label("هذا الثيم لا يدعم تحريك عناصره بعد. ثيمات الصور (هافال) تدعمه. نافذة الملاحة تعمل مع كل الثيمات.",
                14f, Palette.muted).apply { setPadding(dp(6), dp(10), dp(6), dp(10)); setLineSpacing(dp(3f), 1f) })
        }
        items.forEach { list.addView(itemRow(it.id, it.name, isHidden(it.id))) }
    }

    /** سطر عنصر: اسمه (يحدده عند اللمس)، وعين للإظهار والإخفاء. */
    private fun itemRow(id: String, name: String, hidden: Boolean?): View {
        val on = id == sel
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64)
            setPadding(dp(14), dp(4), dp(8), dp(4))
            background = if (on) smallSign(Palette.green) else rounded(Palette.shoulder, dp(10f), dp(1), Palette.kerb)
            contentDescription = name
            setOnClickListener { select(id) }
        }.also { it.pressable(dp(10f)) }
        row.addView(label(name, 16f, if (hidden == true) Palette.muted else Palette.white, bold = on).apply { maxLines = 2 },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        if (hidden != null) {
            val eye = FrameLayout(this).apply {
                contentDescription = if (hidden) "إظهار $name" else "إخفاء $name"
                setOnClickListener { setHidden(id, !hidden) }
            }.also { it.pressable(dp(24f)) }
            eye.addView(icon(if (hidden) R.drawable.ic_eye_off else R.drawable.ic_eye, 28, if (hidden) Palette.muted else Palette.white),
                FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER))
            row.addView(eye, LinearLayout.LayoutParams(dp(56), dp(56)))
        }
        return row.also {
            it.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(6) }
        }
    }

    private fun renderControls() {
        controls.removeAllViews()
        val isNav = sel == NAV
        val name = if (isNav) "نافذة الملاحة" else items.firstOrNull { it.id == sel }?.name ?: sel

        // الاسم والإظهار
        val r1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(label(name, 20f, Palette.white, bold = true))
        titles.addView(label(positionText(), 15f, Palette.muted).apply { setPadding(0, dp(4), 0, 0) })
        r1.addView(titles, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        if (!isNav) {
            val hidden = isHidden(sel)
            r1.addView(signButton(if (hidden) R.drawable.ic_eye_off else R.drawable.ic_eye, if (hidden) "مخفي" else "ظاهر",
                if (hidden) Palette.brown else Palette.green) { setHidden(sel, !hidden) },
                LinearLayout.LayoutParams(WRAP_CONTENT, dp(64)))
        } else {
            r1.addView(label(if (prefs.navOnCluster) "الملاحة على العدادات الآن" else "الملاحة ليست على العدادات", 14f,
                if (prefs.navOnCluster) Palette.white else Palette.muted))
        }
        controls.addView(r1)

        // الخطوة، والأسهم، والحجم
        val r2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        val stepsCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        stepsCol.addView(label("الخطوة (نقطة)", 14f, Palette.muted).apply { setPadding(0, 0, 0, dp(6)) })
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        STEPS.forEach { st ->
            chips.addView(signButton(null, "$st", if (st == step) Palette.green else Palette.blue) { step = st; renderControls() },
                LinearLayout.LayoutParams(dp(68), dp(64)).apply { marginEnd = dp(6) })
        }
        stepsCol.addView(chips)
        r2.addView(stepsCol)
        r2.addView(dpad(), LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = dp(20) })

        val sizeCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (isNav) {
            sizeCol.addView(stepperRow("العرض", "${px(prefs.navW, canvasW)}") { d -> nudgeNavSize(d, 0) })
            sizeCol.addView(stepperRow("الارتفاع", "${px(prefs.navH, canvasH)}") { d -> nudgeNavSize(0, d) })
        } else {
            sizeCol.addView(stepperRow("الحجم", "${(scale(sel) * 100).roundToInt()}%") { d -> setScale(sel, scale(sel) + d * 0.05f) })
        }
        r2.addView(sizeCol, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(20) })
        controls.addView(r2)

        // الإرجاع والخلفية
        val r3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        r3.addView(signButton(R.drawable.ic_back, if (isNav) "المقاس الافتراضي" else "إرجاع العنصر", Palette.blue) { resetSelected() },
            LinearLayout.LayoutParams(WRAP_CONTENT, dp(64)).apply { marginEnd = dp(10) })
        if (backgrounds.isNotEmpty()) r3.addView(backgroundStepper(), LinearLayout.LayoutParams(WRAP_CONTENT, dp(64)).apply { marginEnd = dp(10) })
        r3.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        r3.addView(signButton(R.drawable.ic_trash, "إرجاع الثيم كله", Palette.red) { confirmResetAll() },
            LinearLayout.LayoutParams(WRAP_CONTENT, dp(64)))
        controls.addView(r3)
    }

    /** أربعة أسهم بالاتجاهات الحقيقية (لا تنقلب مع العربية). */
    private fun dpad(): View {
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        fun arrow(deg: Float, desc: String, dx: Int, dy: Int): View {
            val b = FrameLayout(this).apply {
                background = smallSign(Palette.blue)
                contentDescription = desc
                setOnClickListener { move(dx, dy) }
            }.also { it.pressable(dp(10f)) }
            val ic = icon(R.drawable.ic_up, 28)
            ic.rotation = deg
            b.addView(ic, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER))
            return b
        }
        fun gap() = View(this)
        fun line(vararg v: View) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            v.forEach { addView(it, LinearLayout.LayoutParams(dp(64), dp(64)).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }) }
        }
        grid.addView(line(gap(), arrow(0f, "أعلى", 0, -1), gap()))
        grid.addView(line(arrow(270f, "يسار", -1, 0), gap(), arrow(90f, "يمين", 1, 0)))
        grid.addView(line(gap(), arrow(180f, "أسفل", 0, 1), gap()))
        return grid
    }

    /** عنوان، ثم − القيمة +. */
    private fun stepperRow(title: String, value: String, onDelta: (Int) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3), 0, dp(3))
        }
        row.addView(label(title, 16f, Palette.white), LinearLayout.LayoutParams(dp(90), WRAP_CONTENT))
        row.addView(signButton(null, "−", Palette.blue) { onDelta(-1) }, LinearLayout.LayoutParams(dp(64), dp(64)))
        row.addView(label(ltr(value), 20f, Palette.white, bold = true, digitsFace = true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(dp(96), WRAP_CONTENT))
        row.addView(signButton(null, "+", Palette.blue) { onDelta(1) }, LinearLayout.LayoutParams(dp(64), dp(64)))
        return row
    }

    /** ‹ اسم الخلفية ›: الافتراضية، خلفيات الثيم، بدون خلفية. */
    private fun backgroundStepper(): View {
        val options = listOf("" to "الخلفية الافتراضية") + backgrounds + listOf("none" to "بدون خلفية")
        val cur = options.indexOfFirst { it.first == edit.optString("bg", "") }.coerceAtLeast(0)
        val box = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun go(d: Int) {
            val next = options[(cur + d + options.size) % options.size].first
            if (next.isEmpty()) edit.remove("bg") else edit.put("bg", next)
            saveEdit(); renderControls()
        }
        box.addView(signButton(null, "›", Palette.brown) { go(-1) }, LinearLayout.LayoutParams(dp(64), dp(64)))
        box.addView(label(options[cur].second, 16f, Palette.white, bold = true).apply {
            gravity = Gravity.CENTER; setPadding(dp(10), 0, dp(10), 0); minWidth = dp(150)
        })
        box.addView(signButton(null, "‹", Palette.brown) { go(1) }, LinearLayout.LayoutParams(dp(64), dp(64)))
        return box
    }

    // ---------------- الأفعال ----------------

    private fun select(id: String) {
        sel = id
        ClusterPayload.editSel = id
        renderList()
        renderControls()
        pushAll()
    }

    private fun switchTheme(delta: Int) {
        val all = ClusterThemes.all
        val i = all.indexOfFirst { it.id == prefs.clusterTheme }.coerceAtLeast(0)
        val t = all[(i + delta + all.size) % all.size]
        prefs.clusterTheme = t.id
        edit = loadEdit()
        items = emptyList()
        backgrounds = emptyList()
        sel = NAV
        ClusterPayload.editSel = sel
        themeName.text = t.name
        preview.load(t.id)
        ClusterService.reload(this)
        renderList()
        renderControls()
    }

    private fun move(dx: Int, dy: Int) {
        if (sel == NAV) {
            prefs.navX = (prefs.navX + dx * step / canvasW).coerceAtMost(1f - prefs.navW)
            prefs.navY = (prefs.navY + dy * step / canvasH).coerceAtMost(1f - prefs.navH)
            scheduleRelayout()
        } else {
            val e = el(sel)
            e.put("dx", e.optInt("dx", 0) + dx * step)
            e.put("dy", e.optInt("dy", 0) + dy * step)
            saveEdit()
        }
        renderControls()
        pushAll()
    }

    private fun nudgeNavSize(dw: Int, dh: Int) {
        prefs.navW = (prefs.navW + dw * step / canvasW).coerceAtMost(1f - prefs.navX)
        prefs.navH = (prefs.navH + dh * step / canvasH).coerceAtMost(1f - prefs.navY)
        scheduleRelayout()
        renderControls()
        pushAll()
    }

    private fun setScale(id: String, s: Float) {
        el(id).put("s", (s.coerceIn(0.3f, 3f) * 100).roundToInt() / 100.0)
        saveEdit()
        renderControls()
    }

    private fun setHidden(id: String, hidden: Boolean) {
        el(id).put("h", hidden)
        saveEdit()
        renderList()
        renderControls()
    }

    private fun resetSelected() {
        if (sel == NAV) {
            prefs.resetNavRect()
            scheduleRelayout()
            pushAll()
        } else {
            edit.optJSONObject("els")?.remove(sel)
            saveEdit()
            renderList()
        }
        renderControls()
    }

    private fun confirmResetAll() {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setMessage("إرجاع كل عناصر «${ClusterThemes.byId(prefs.clusterTheme).name}» وخلفيته كما صممها الثيم؟ نافذة الملاحة لا تتغير.")
            .setPositiveButton("إرجاع") { _, _ ->
                edit = JSONObject()
                saveEdit()
                renderList()
                renderControls()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    /** الثيم أبلغ بعناصره: {canvas:{w,h}, elements:[{id,name,hidden}], backgrounds:[{id,name}]} */
    private fun onElements(json: String) {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return
        o.optJSONObject("canvas")?.let {
            canvasW = it.optDouble("w", 1920.0).toFloat().coerceAtLeast(1f)
            canvasH = it.optDouble("h", 720.0).toFloat().coerceAtLeast(1f)
        }
        val els = o.optJSONArray("elements") ?: JSONArray()
        items = (0 until els.length()).mapNotNull { i ->
            els.optJSONObject(i)?.let { Item(it.optString("id"), it.optString("name", it.optString("id")), it.optBoolean("hidden", false)) }
        }.filter { it.id.isNotEmpty() }
        val bgs = o.optJSONArray("backgrounds") ?: JSONArray()
        backgrounds = (0 until bgs.length()).mapNotNull { i ->
            bgs.optJSONObject(i)?.let { it.optString("id") to it.optString("name", it.optString("id")) }
        }.filter { it.first.isNotEmpty() }
        if (sel != NAV && items.none { it.id == sel }) sel = NAV
        renderList()
        renderControls()
    }

    // ---------------- الحفظ ----------------

    private fun loadEdit(): JSONObject = runCatching { JSONObject(prefs.themeEdit(prefs.clusterTheme)) }.getOrDefault(JSONObject())

    private fun el(id: String): JSONObject {
        val els = edit.optJSONObject("els") ?: JSONObject().also { edit.put("els", it) }
        return els.optJSONObject(id) ?: JSONObject().also { els.put(id, it) }
    }

    private fun isHidden(id: String): Boolean {
        val o = edit.optJSONObject("els")?.optJSONObject(id)
        return if (o != null && o.has("h")) o.optBoolean("h") else items.firstOrNull { it.id == id }?.hiddenByDefault ?: false
    }

    private fun scale(id: String): Float = edit.optJSONObject("els")?.optJSONObject(id)?.optDouble("s", 1.0)?.toFloat() ?: 1f

    private fun saveEdit() {
        prefs.setThemeEdit(prefs.clusterTheme, edit.toString())
        pushAll()
    }

    private fun pushAll() {
        preview.push(force = true)
        ClusterService.instance?.pushNow()
    }

    private fun scheduleRelayout() {
        main.removeCallbacks(relayout)
        relayoutPending = true
        main.postDelayed(relayout, 900)
    }

    private fun px(fraction: Float, size: Float) = (fraction * size).roundToInt()

    private fun positionText(): String = if (sel == NAV) {
        "الموضع ${ltr("${px(prefs.navX, canvasW)} , ${px(prefs.navY, canvasH)}")} · المقاس ${ltr("${px(prefs.navW, canvasW)}×${px(prefs.navH, canvasH)}")}"
    } else {
        val o = edit.optJSONObject("els")?.optJSONObject(sel)
        val dx = o?.optInt("dx", 0) ?: 0
        val dy = o?.optInt("dy", 0) ?: 0
        "الإزاحة ${ltr("x ${signed(dx)} · y ${signed(dy)}")} نقطة"
    }

    private fun signed(v: Int) = if (v > 0) "+$v" else "$v"

    /** إطار يحافظ على نسبة شاشة العدادات، ولا يتجاوز 60% من الارتفاع المتاح. */
    private class AspectFrame(context: Context, private val aspect: Float) : FrameLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            var w = MeasureSpec.getSize(widthMeasureSpec)
            var h = (w / aspect).toInt()
            val hMode = MeasureSpec.getMode(heightMeasureSpec)
            val avail = MeasureSpec.getSize(heightMeasureSpec)
            if (hMode != MeasureSpec.UNSPECIFIED && avail > 0 && h > avail * 0.6f) {
                h = (avail * 0.6f).toInt()
                w = (h * aspect).toInt()
            }
            super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
        }
    }
}
