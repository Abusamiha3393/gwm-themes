package com.sahra.split

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast

/**
 * الشاشة الرئيسية: جسر لوحات المعلومات على جانب، وشريط المسارات (الإعدادات المسبقة) في الأعلى،
 * ولوحتا مخرجين (الملاحة والموسيقى) بنسبة التقسيم، وصف لوحات الأوامر في الأسفل.
 */
class HomeActivity : Activity() {

    companion object {
        /** يمنع إعادة الفتح التلقائي كلما رجع السائق للشاشة الرئيسية؛ يكفي مرة بعد التشغيل. */
        @Volatile private var autoSplitDone = false
    }

    private lateinit var prefs: Prefs
    private var musicTitle: android.widget.TextView? = null
    private var musicSub: android.widget.TextView? = null
    private var musicPlay: ImageView? = null
    private val mediaListener: (NowPlaying) -> Unit = { showMedia(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        Type.init(this)
        window.statusBarColor = Palette.gantry
        window.navigationBarColor = Palette.asphalt
        VehicleRepository.start(this)
    }

    override fun onResume() {
        super.onResume()
        Sun.refreshLocation(this)
        Sun.applyDayNight(this)
        render()
        MediaTracker.addListener(mediaListener)
        OverlayService.sync(this)
        if (!autoSplitDone && prefs.autoSplit) {
            autoSplitDone = true
            window.decorView.postDelayed({ split() }, 1200)
        }
    }

    override fun onPause() {
        MediaTracker.removeListener(mediaListener)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.getBooleanExtra("split", false) == true) window.decorView.postDelayed({ split() }, 300)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // نحن الشاشة الرئيسية: لا شيء خلفنا
    }

    private fun render() {
        musicTitle = null; musicSub = null; musicPlay = null
        setContentView(build())
        showMedia(MediaTracker.current)
    }

    private fun split() {
        val p = prefs.preset
        if (p.navApp.isEmpty() && p.musicApp.isEmpty()) {
            Toast.makeText(this, "اختر تطبيقي الملاحة والموسيقى أولًا (اضغط مطولًا على اللوحة)", Toast.LENGTH_LONG).show()
            return
        }
        AppLauncher.applyPreset(this, p, prefs.windowMode)
    }

    private fun build(): View {
        val p = prefs.preset
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            background = AsphaltDrawable(this@HomeActivity)
        }
        val size = AppLauncher.screenSize(this)
        val showPanel = p.side != PanelSide.NONE && !prefs.floatingPanel
        val panel = if (showPanel) SplitInfoView(this, p.page) { page -> prefs.updatePreset { it.copy(page = page) } }.apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        } else null
        val main = buildMain(p)
        val panelLp = LinearLayout.LayoutParams((size.x * p.panelWidth).toInt(), MATCH_PARENT)
        val mainLp = LinearLayout.LayoutParams(0, MATCH_PARENT, 1f)
        if (panel != null && p.side == PanelSide.LEFT) { root.addView(panel, panelLp); root.addView(kerb(), LinearLayout.LayoutParams(dp(2), MATCH_PARENT)) }
        root.addView(main, mainLp)
        if (panel != null && p.side == PanelSide.RIGHT) { root.addView(kerb(), LinearLayout.LayoutParams(dp(2), MATCH_PARENT)); root.addView(panel, panelLp) }
        return root
    }

    private fun kerb() = View(this).apply { setBackgroundColor(Palette.kerb) }

    private fun buildMain(p: Preset): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        // المسارات: كل إعداد مسبق مسار، والمسار الحالي مضاء بالأخضر
        // تظهر الإعدادات المستخدمة فقط، ومسار «+» يفتح أول إعداد فارغ
        val all = prefs.presets()
        val used = all.indices.filter { i -> i == prefs.presetIndex || all[i].navApp.isNotEmpty() || all[i].musicApp.isNotEmpty() }
        val free = all.indices.firstOrNull { it !in used }
        val lanes = used.map { LaneStrip.Lane(it, all[it].name) } + listOfNotNull(free?.let { LaneStrip.Lane(it, "إعداد جديد", plus = true) })
        col.addView(LaneStrip(this, lanes, prefs.presetIndex, nightIndex()) { lane ->
            val i = lane.index
            prefs.presetIndex = i
            if (lane.plus) {
                startActivity(Intent(this, SettingsActivity::class.java))
                return@LaneStrip
            }
            if (prefs.autoDayNight) {
                prefs.autoDayNight = false
                Toast.makeText(this, "أُوقف التبديل التلقائي بين النهار والليل", Toast.LENGTH_SHORT).show()
            }
            render()
            OverlayService.sync(this)
        }, LinearLayout.LayoutParams(MATCH_PARENT, dp(64)))

        // تنبيهات: الصلاحيات الناقصة، ثم التذكيرات المستحقة
        if (!Settings.canDrawOverlays(this) || !MediaTracker.hasAccess(this)) {
            col.addView(banner(R.drawable.ic_settings, "أكمل الصلاحيات من الإعدادات حتى تعمل كل الخصائص", "", Palette.blue, Palette.white) {
                startActivity(Intent(this, SettingsActivity::class.java))
            }, bannerLp())
        }
        Logbook.dueReminders(this).firstOrNull()?.let { r ->
            val more = Logbook.dueReminders(this).size - 1
            col.addView(banner(R.drawable.ic_warning, r.text + if (more > 0) "  (+$more)" else "", "حان الموعد", Palette.amber, Palette.ink) {
                startActivity(Intent(this, LogActivity::class.java).putExtra("tab", 2))
            }, bannerLp())
        }

        // لوحتا المخرجين بنسبة التقسيم، بترتيبهما الفعلي على الشاشة (يسار ثم يمين)
        val exits = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        val both = p.navApp.isNotEmpty() && p.musicApp.isNotEmpty()
        val navW = if (!both) 0.5f else p.ratio
        exits.addView(exit(true, p.navApp), LinearLayout.LayoutParams(0, MATCH_PARENT, navW).apply { marginEnd = dp(14) })
        exits.addView(exit(false, p.musicApp), LinearLayout.LayoutParams(0, MATCH_PARENT, 1f - navW))
        col.addView(exits, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(10); bottomMargin = dp(14) })

        // صف لوحات الأوامر
        val dock = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
        dock.addView(signButton(R.drawable.ic_split, "تقسيم الشاشة", Palette.green) { split() }.apply { minimumHeight = dp(84) },
            LinearLayout.LayoutParams(0, dp(84), 1.6f).apply { marginEnd = dp(12) })
        dockItem(dock, R.drawable.ic_apps, "التطبيقات") { startActivity(Intent(this, AppsActivity::class.java)) }
        dockItem(dock, R.drawable.ic_swap, "تبديل") {
            prefs.updatePreset { it.copy(navApp = it.musicApp, musicApp = it.navApp) }
            render()
        }
        dockItem(dock, R.drawable.ic_log, "السجل") { startActivity(Intent(this, LogActivity::class.java)) }
        dockItem(dock, R.drawable.ic_settings, "الإعدادات") { startActivity(Intent(this, SettingsActivity::class.java)) }
        col.addView(dock, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return col
    }

    private fun nightIndex(): Int = if (prefs.autoDayNight) prefs.nightPreset else -1

    private fun bannerLp() = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) }

    private fun banner(res: Int, text: String, side: String, color: Int, ink: Int, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(color, dp(10f))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener { onClick() }
        }.also { it.pressable(dp(10f)) }
        row.addView(icon(res, 28, ink), LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(12) })
        row.addView(label(text, 17f, ink, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        if (side.isNotEmpty()) row.addView(label(side, 16f, ink, true))
        return row
    }

    /** لوحة مخرج: لسان صغير بالاسم فوق اللوحة، وفيها التطبيق على لوح أبيض. */
    private fun exit(nav: Boolean, pkg: String): View {
        val color = if (nav) Palette.green else Palette.blue
        val title = if (nav) "الملاحة" else "الموسيقى"
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val tab = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(color)
                val r = dp(10f)
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        // سهم اللسان يشير إلى جهة نافذة التطبيق: الملاحة يسارًا والموسيقى يمينًا
        tab.addView(icon(R.drawable.ic_nav, 22).apply { scaleX = if (nav) -1f else 1f }, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(8) })
        tab.addView(label(title, 16f, Palette.white, true))
        val tabRow = LinearLayout(this).apply { gravity = Gravity.END; layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(dp(22), 0, dp(22), 0) }
        tabRow.addView(tab)
        col.addView(tabRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val board = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = sign(color)
            val pd = dp(22)
            setPadding(pd + dp(8), pd + dp(4), pd + dp(8), pd)
        }
        val app = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val plate = FrameLayout(this).apply { background = rounded(Palette.white, dp(20f)) }
        val appIcon = AppLauncher.icon(this, pkg)
        if (appIcon != null) plate.addView(ImageView(this).apply { setImageDrawable(appIcon) }, FrameLayout.LayoutParams(dp(66), dp(66), Gravity.CENTER))
        else plate.addView(icon(R.drawable.ic_plus, 40, color), FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER))
        app.addView(plate, LinearLayout.LayoutParams(dp(92), dp(92)).apply { marginEnd = dp(20) })
        val names = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val name = label(if (pkg.isEmpty()) "اختر تطبيق $title" else AppLauncher.label(this, pkg), 30f, Palette.white, true).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        names.addView(name)
        val sub = label(if (pkg.isEmpty()) "اضغط مطولًا للاختيار" else "المس للفتح · مطولًا للتغيير", 15f, Palette.legendDim).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(8), 0, 0)
        }
        names.addView(sub)
        app.addView(names, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        board.addView(app)
        board.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

        val foot = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        if (nav) {
            foot.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            // السهم يشير إلى جهة نافذة الملاحة على الشاشة
            foot.addView(icon(R.drawable.ic_nav, 56).apply { scaleX = if (navOnLeft()) -1f else 1f })
        } else if (pkg.isNotEmpty()) {
            musicTitle = name; musicSub = sub
            foot.layoutDirection = View.LAYOUT_DIRECTION_LTR
            foot.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            foot.addView(roundControl(R.drawable.ic_prev, false) { MediaTracker.command("prev") })
            val pp = roundControl(R.drawable.ic_play, true) { MediaTracker.command("toggle") }
            musicPlay = (pp as FrameLayout).getChildAt(0) as ImageView
            foot.addView(pp)
            foot.addView(roundControl(R.drawable.ic_next, false) { MediaTracker.command("next") })
        }
        board.addView(foot, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        board.setOnClickListener {
            if (pkg.isEmpty()) pick(title, nav) else {
                val size = AppLauncher.screenSize(this)
                val l = AppLauncher.layoutFor(prefs.preset, size.x, size.y, dp(4))
                AppLauncher.launch(this, pkg, if (nav) l.nav else l.music, prefs.windowMode)
            }
        }
        board.setOnLongClickListener { pick(title, nav); true }
        board.pressable(dp(14f))
        col.addView(board, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        return col
    }

    private fun navOnLeft() = true

    private fun roundControl(res: Int, primary: Boolean, onClick: () -> Unit): View {
        val size = dp(if (primary) 80 else 72)
        val f = FrameLayout(this).apply {
            background = if (primary) rounded(Palette.white, size / 2f) else rounded(0, size / 2f, dp(3), Palette.white)
            setOnClickListener { onClick() }
        }.also { it.pressable(size / 2f) }
        f.addView(icon(res, 32, if (primary) Palette.blue else Palette.white), FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER))
        f.layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(dp(7), 0, dp(7), 0) }
        return f
    }

    private fun showMedia(np: NowPlaying) {
        val t = musicTitle ?: return
        val pkg = prefs.preset.musicApp
        if (np.title.isNotEmpty() && (np.app == pkg || np.playing)) {
            t.text = np.title
            musicSub?.text = np.artist.ifEmpty { AppLauncher.label(this, np.app) }
        } else {
            t.text = AppLauncher.label(this, pkg)
            musicSub?.text = "المس للفتح · مطولًا للتغيير"
        }
        musicPlay?.setImageResource(if (np.playing) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun dockItem(dock: LinearLayout, res: Int, text: String, onClick: () -> Unit) {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val sq = FrameLayout(this).apply {
            background = smallSign(Palette.blue)
            setOnClickListener { onClick() }
            contentDescription = text
        }.also { it.pressable(dp(10f)) }
        sq.addView(icon(res, 38), FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER))
        col.addView(sq, LinearLayout.LayoutParams(dp(84), dp(84)))
        col.addView(label(text, 14f, Palette.muted).apply { setPadding(0, dp(6), 0, 0) })
        dock.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
    }

    private fun pick(title: String, nav: Boolean) {
        AppPicker.show(this, "تطبيق $title", allowNone = true) { pkg ->
            prefs.updatePreset { if (nav) it.copy(navApp = pkg) else it.copy(musicApp = pkg) }
            render()
        }
    }
}

/** أرضية الإسفلت: لون داكن دافئ وخطوط طولية خافتة كأثر علامات الطريق. */
class AsphaltDrawable(context: Context) : android.graphics.drawable.Drawable() {
    private val step = context.dp(48f)
    private val line = Paint().apply { color = 0x09FFFFFF }
    override fun draw(c: Canvas) {
        c.drawColor(Palette.asphalt)
        var x = bounds.left + step
        while (x < bounds.right) { c.drawRect(x, bounds.top.toFloat(), x + step / 24, bounds.bottom.toFloat(), line); x += step }
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = android.graphics.PixelFormat.OPAQUE
}

/**
 * شريط المسارات: الإعدادات المسبقة كمسارات طريق بخطوط متقطعة بينها.
 * المسار الحالي مضاء بالأخضر وفيه سهم أبيض؛ مسار الليل (عند التبديل التلقائي) يحمل رمز الهلال،
 * ومسار «+» ينشئ إعدادًا جديدًا.
 */
class LaneStrip(
    context: Context,
    private val lanes: List<Lane>,
    private val selected: Int,
    private val night: Int,
    private val onPick: (Lane) -> Unit,
) : View(context) {
    data class Lane(val index: Int, val name: String, val plus: Boolean = false)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Palette.laneDash; strokeWidth = context.dp(3f)
        pathEffect = DashPathEffect(floatArrayOf(context.dp(8f), context.dp(7f)), 0f)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val up = context.getDrawable(R.drawable.ic_up)!!.mutate()
    private val moon = context.getDrawable(R.drawable.ic_moon)!!.mutate()
    private val plus = context.getDrawable(R.drawable.ic_plus)!!.mutate()
    private val r = context.dp(10f)

    init { Type.init(context); contentDescription = "الإعدادات المسبقة" }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        fill.color = Palette.gantry
        c.drawRoundRect(RectF(0f, 0f, w, h), r, r, fill)
        val n = lanes.size
        val lw = w / n
        lanes.forEachIndexed { i, lane ->
            // من اليمين لليسار: المسار الأول في اليمين
            val x1 = w - lw * i
            val x0 = x1 - lw
            val on = !lane.plus && lane.index == selected
            if (on) {
                fill.color = Palette.green
                c.drawRoundRect(RectF(x0 + context.dp(3f), context.dp(3f), x1 - context.dp(3f), h - context.dp(3f)), r * 0.8f, r * 0.8f, fill)
            }
            if (i > 0) c.drawLine(x1, h * 0.18f, x1, h * 0.82f, dash)
            val ink = if (on) Palette.white else Palette.muted
            text.typeface = if (on) Type.kufiBold else Type.kufi
            text.textSize = h * 0.27f
            text.color = ink
            val icon = when {
                lane.plus -> plus
                lane.index == night -> moon
                else -> up
            }
            val isz = (h * (if (lane.plus) 0.3f else 0.36f)).toInt()
            val tw = text.measureText(lane.name).coerceAtMost(lw - isz - context.dp(26f)).coerceAtLeast(0f)
            val total = isz + context.dp(8f) + tw
            val start = x0 + (lw - total) / 2
            // الترتيب العربي: الرمز يمين النص
            val iconLeft = (start + tw + context.dp(8f)).toInt()
            icon.setTint(ink)
            icon.setBounds(iconLeft, (h / 2 - isz / 2).toInt(), iconLeft + isz, (h / 2 + isz / 2).toInt())
            icon.draw(c)
            c.save()
            c.clipRect(start, 0f, start + tw, h)
            text.textAlign = Paint.Align.RIGHT
            c.drawText(lane.name, start + tw, h / 2 + text.textSize * 0.36f, text)
            c.restore()
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP && lanes.isNotEmpty()) {
            val i = ((width - e.x) / (width.toFloat() / lanes.size)).toInt().coerceIn(0, lanes.size - 1)
            val lane = lanes[i]
            if (lane.plus || lane.index != selected) onPick(lane)
        }
        return true
    }
}
