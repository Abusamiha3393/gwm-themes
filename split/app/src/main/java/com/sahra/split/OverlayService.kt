package com.sahra.split

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * خدمة أمامية للنوافذ العائمة: الكرة (عداد سرعة وقائمة سريعة)، ولوحة المعلومات العائمة فوق التطبيقات.
 */
class OverlayService : Service(), VehicleRepository.Listener {

    companion object {
        private const val ACTION_SYNC = "com.sahra.split.SYNC"
        private const val CHANNEL = "overlay"
        private const val NOTIF_ID = 11

        /** يشغّل الخدمة أو يوقفها حسب الإعدادات، ويعيد بناء النوافذ. */
        fun sync(context: Context) {
            val p = Prefs(context)
            val want = (p.ball || p.floatingPanel) && Settings.canDrawOverlays(context)
            val i = Intent(context, OverlayService::class.java).setAction(ACTION_SYNC)
            if (want) runCatching { context.startForegroundService(i) }
            else context.stopService(Intent(context, OverlayService::class.java))
        }
    }

    private lateinit var prefs: Prefs
    private lateinit var wm: WindowManager
    private var ball: SpeedRoundel? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var menu: View? = null
    private var panel: View? = null
    private var panelHidden = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        goForeground()
        VehicleRepository.start(this)
        VehicleRepository.addListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        rebuild()
        return START_STICKY
    }

    override fun onDestroy() {
        VehicleRepository.removeListener(this)
        removeAll()
        super.onDestroy()
    }

    override fun onState(state: VehicleState) {
        ball?.speed = state.speed
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "النوافذ العائمة", NotificationManager.IMPORTANCE_MIN))
        }
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, SettingsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("صحراء سبليت")
            .setContentText("الكرة العائمة ولوحة المعلومات تعمل")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun params(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun removeAll() {
        listOf(ball, menu, panel).forEach { v -> if (v != null) runCatching { wm.removeView(v) } }
        ball = null; menu = null; panel = null
    }

    private fun rebuild() {
        removeAll()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        if (prefs.floatingPanel && !panelHidden) addPanel()
        if (prefs.ball) addBall()
        if (!prefs.ball && !prefs.floatingPanel) stopSelf()
    }

    // ---------------- لوحة المعلومات العائمة ----------------

    private fun addPanel() {
        val p = prefs.preset
        if (p.side == PanelSide.NONE) return
        val size = AppLauncher.screenSize(this)
        val r = AppLauncher.layoutFor(p, size.x, size.y, 0).panel ?: return
        val v = SplitInfoView(this, p.page) { page -> prefs.updatePreset { it.copy(page = page) } }.apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val lp = params(r.width(), r.height()).apply { x = r.left; y = r.top }
        runCatching { wm.addView(v, lp); panel = v }
    }

    // ---------------- الكرة العائمة ----------------

    @SuppressLint("ClickableViewAccessibility")
    private fun addBall() {
        val d = dp(76)
        val v = SpeedRoundel(this)
        v.speed = VehicleRepository.state.speed
        val lp = params(d, d).apply { x = prefs.ballX; y = prefs.ballY }
        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = lp.x; sy = lp.y; tx = e.rawX; ty = e.rawY; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - tx; val dy = e.rawY - ty
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    if (moved) {
                        lp.x = sx + dx.toInt(); lp.y = sy + dy.toInt()
                        runCatching { wm.updateViewLayout(v, lp) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) { prefs.ballX = lp.x; prefs.ballY = lp.y; closeMenu() } else toggleMenu()
                }
            }
            true
        }
        runCatching { wm.addView(v, lp); ball = v; ballParams = lp }
    }

    private fun closeMenu() {
        menu?.let { runCatching { wm.removeView(it) } }
        menu = null
    }

    /** القائمة: عمود لوحات خدمات صغيرة، لكل أمر رمزه. */
    private fun toggleMenu() {
        if (menu != null) { closeMenu(); return }
        Type.init(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Palette.gantry, dp(16f), dp(2), Palette.kerb)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        fun item(res: Int, text: String, color: Int = Palette.blue, action: () -> Unit) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6), dp(6), dp(10), dp(6))
                setOnClickListener { closeMenu(); action() }
            }.also { it.pressable(dp(12f)) }
            val sq = FrameLayout(this).apply { background = smallSign(color) }
            sq.addView(icon(res, 26), FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
            row.addView(sq, LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(14) })
            row.addView(label(text, 17f, Palette.white, true))
            col.addView(row, LinearLayout.LayoutParams(dp(250), LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        item(R.drawable.ic_home, "الرئيسية", Palette.green) { AppLauncher.goHome(this) }
        item(R.drawable.ic_split, "تقسيم الشاشة", Palette.green) { goHomeAndSplit() }
        item(R.drawable.ic_back, "رجوع") {
            if (!BackService.back()) {
                android.widget.Toast.makeText(this, "فعّل «صحراء سبليت» في إمكانية الوصول ليعمل زر الرجوع", android.widget.Toast.LENGTH_LONG).show()
            }
        }
        item(R.drawable.ic_swap, "تبديل التطبيقين") {
            prefs.updatePreset { it.copy(navApp = it.musicApp, musicApp = it.navApp) }
            goHomeAndSplit()
        }
        item(R.drawable.ic_up, "الإعداد التالي") {
            prefs.presetIndex = (prefs.presetIndex + 1) % Prefs.PRESETS
            prefs.autoDayNight = false
            rebuild()
            goHomeAndSplit()
        }
        if (prefs.floatingPanel) item(R.drawable.ic_clock, if (panelHidden) "إظهار اللوحة" else "إخفاء اللوحة", Palette.brown) {
            panelHidden = !panelHidden
            rebuild()
        }
        item(R.drawable.ic_log, "السجل") {
            startActivity(Intent(this, LogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        item(R.drawable.ic_apps, "التطبيقات") {
            startActivity(Intent(this, AppsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        val bp = ballParams ?: return
        val size = AppLauncher.screenSize(this)
        val lp = params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            x = if (bp.x > size.x / 2) (bp.x - dp(282)).coerceAtLeast(0) else bp.x + dp(86)
            y = bp.y.coerceAtMost(size.y - dp(560)).coerceAtLeast(0)
        }
        runCatching { wm.addView(col, lp); menu = col }
    }

    private fun goHomeAndSplit() {
        val i = Intent(this, HomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            .putExtra("split", true)
        runCatching { startActivity(i) }
    }
}

/**
 * خدمة إمكانية الوصول: تُستخدم فقط لتنفيذ «رجوع» من الكرة العائمة.
 * لا تقرأ محتوى الشاشة.
 */
class BackService : AccessibilityService() {
    companion object {
        @Volatile private var instance: BackService? = null
        fun back(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_BACK) ?: false
        fun enabled() = instance != null
    }

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}

/**
 * الكرة العائمة: لوحة خضراء صغيرة بإطار أبيض فيها السرعة الحالية بأرقام اللوحات.
 * لا تستعمل الحلقة الحمراء، لأنها في لوحات الطرق تعني الحد الأقصى لا سرعتك.
 */
class SpeedRoundel(context: Context) : View(context) {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val sign = context.smallSign(Palette.green)
    var speed = 0f
        set(v) { if (num(v) != num(field)) { field = v; invalidate() } else field = v }

    init { Type.init(context); contentDescription = "القائمة السريعة" }

    override fun onDraw(c: android.graphics.Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        paint.color = 0x66000000
        c.drawRoundRect(android.graphics.RectF(context.dp(2f), context.dp(4f), w - context.dp(2f), h), context.dp(12f), context.dp(12f), paint)
        sign.setBounds(0, 0, width, height - context.dp(3))
        sign.draw(c)
        paint.color = Palette.white
        paint.typeface = Type.digitsBlack
        paint.textAlign = android.graphics.Paint.Align.CENTER
        val t = num(speed)
        paint.textSize = h * (if (t.length >= 3) 0.36f else 0.44f)
        c.drawText(t, w / 2, h * 0.56f, paint)
        paint.typeface = Type.kufi
        paint.textSize = h * 0.13f
        paint.color = Palette.legendDim
        c.drawText("كم/س", w / 2, h * 0.8f, paint)
    }
}
