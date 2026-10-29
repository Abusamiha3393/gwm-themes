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
    private var ball: TextView? = null
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
        ball?.text = num(state.speed)
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
        val d = dp(64)
        val v = TextView(this).apply {
            text = num(VehicleRepository.state.speed)
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Palette.bg)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Palette.accent)
                setStroke(dp(3), Palette.bg)
            }
            alpha = 0.92f
        }
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

    private fun toggleMenu() {
        if (menu != null) { closeMenu(); return }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Palette.card, dp(16f), dp(1), Palette.line)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        fun item(text: String, action: () -> Unit) {
            col.addView(TextView(this).apply {
                this.text = text
                textSize = 17f
                setTextColor(Palette.fg)
                setPadding(dp(16), dp(12), dp(16), dp(12))
                setOnClickListener { closeMenu(); action() }
            }, LinearLayout.LayoutParams(dp(220), LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        item("⌂  الرئيسية") { AppLauncher.goHome(this) }
        item("⧉  تقسيم الشاشة") { goHomeAndSplit() }
        item("↩  رجوع") {
            if (!BackService.back()) {
                android.widget.Toast.makeText(this, "فعّل «صحراء سبليت» في إمكانية الوصول ليعمل زر الرجوع", android.widget.Toast.LENGTH_LONG).show()
            }
        }
        item("⇄  تبديل التطبيقين") {
            prefs.updatePreset { it.copy(navApp = it.musicApp, musicApp = it.navApp) }
            goHomeAndSplit()
        }
        item("➜  الإعداد التالي") {
            prefs.presetIndex = (prefs.presetIndex + 1) % Prefs.PRESETS
            prefs.autoDayNight = false
            rebuild()
            goHomeAndSplit()
        }
        if (prefs.floatingPanel) item(if (panelHidden) "◧  إظهار اللوحة" else "◨  إخفاء اللوحة") {
            panelHidden = !panelHidden
            rebuild()
        }
        item("☰  السجل") {
            startActivity(Intent(this, LogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        item("▦  التطبيقات") {
            startActivity(Intent(this, AppsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        val bp = ballParams ?: return
        val size = AppLauncher.screenSize(this)
        val lp = params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            x = if (bp.x > size.x / 2) (bp.x - dp(230)).coerceAtLeast(0) else bp.x + dp(72)
            y = bp.y.coerceAtMost(size.y - dp(470)).coerceAtLeast(0)
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
