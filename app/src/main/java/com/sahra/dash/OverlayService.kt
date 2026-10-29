package com.sahra.dash

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * خدمة أمامية تعرض الثيم فوق شاشة الوسائط في WebView وترسل له البيانات باستمرار.
 * تدير أيضًا: التطبيق المجاور للعدادات، العداد العائم الصغير، والتطبيق في نافذة عائمة.
 */
class OverlayService : Service(), VehicleRepository.Listener {

    companion object {
        const val ACTION_SHOW = "com.sahra.dash.SHOW"
        const val ACTION_HIDE = "com.sahra.dash.HIDE"
        const val ACTION_TOGGLE = "com.sahra.dash.TOGGLE"
        const val ACTION_RELOAD = "com.sahra.dash.RELOAD"
        const val ACTION_FLOAT_APP = "com.sahra.dash.FLOAT_APP"
        const val ACTION_STOP = "com.sahra.dash.STOP"
        private const val CHANNEL = "overlay"
        private const val NOTIF_ID = 7

        @Volatile var isRunning = false
            private set

        fun send(context: Context, action: String) {
            val i = Intent(context, OverlayService::class.java).setAction(action)
            if (action == ACTION_STOP) context.startService(i) else context.startForegroundService(i)
        }

        @Suppress("DEPRECATION")
        fun screenSize(context: Context): Point {
            val wm = context.getSystemService(WINDOW_SERVICE) as WindowManager
            return if (Build.VERSION.SDK_INT >= 30) {
                val b = wm.currentWindowMetrics.bounds
                Point(b.width(), b.height())
            } else {
                Point().also { wm.defaultDisplay.getRealSize(it) }
            }
        }
    }

    private lateinit var prefs: Prefs
    private lateinit var wm: WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var web: WebView? = null
    private var visible = false
    private var pageReady = false
    private var design = Design()
    private var mini: TextView? = null
    private var miniParams: WindowManager.LayoutParams? = null
    private var floatBar: View? = null
    private val mediaListener: (NowPlaying) -> Unit = { push() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        prefs = Prefs(this)
        design = DesignStore(this).current
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        goForeground()
        VehicleRepository.forceDemo = prefs.demo
        VehicleRepository.start(packageName)
        VehicleRepository.addListener(this)
        MediaTracker.addListener(mediaListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        when (intent?.action) {
            ACTION_HIDE -> hide()
            ACTION_TOGGLE -> if (visible) hide() else show()
            ACTION_RELOAD -> {
                VehicleRepository.forceDemo = prefs.demo
                design = DesignStore(this).current
                if (visible) { hide(); show() } else updateMini()
            }
            ACTION_FLOAT_APP -> floatApp()
            ACTION_STOP -> {
                hide(); removeMini(); removeFloatBar()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> show()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        hide(); removeMini(); removeFloatBar()
        VehicleRepository.removeListener(this)
        VehicleRepository.stop()
        MediaTracker.removeListener(mediaListener)
        isRunning = false
        super.onDestroy()
    }

    // ---------------- الإشعار الدائم ----------------

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.app_name), NotificationManager.IMPORTANCE_MIN))
        }
        val toggle = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("اضغط لإظهار أو إخفاء العدادات")
            .setContentIntent(toggle)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_launcher), "الإعدادات", open).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    // ---------------- نافذة العدادات ----------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun show() {
        if (visible) return
        if (!Settings.canDrawOverlays(this)) return
        removeMini()
        val w = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = true
            settings.mediaPlaybackRequiresUserGesture = false
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addJavascriptInterface(Bridge(), "SahraHost")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    pageReady = true
                    push()
                }
            }
        }
        pageReady = false
        w.loadUrl("file:///android_asset/" + Themes.byId(prefs.themeId).asset)

        val screen = screenSize(this)
        val layout = prefs.layout
        val width = if (layout == OverlayLayout.FULL) WindowManager.LayoutParams.MATCH_PARENT
        else (screen.x * layout.fraction).roundToInt()
        val params = overlayParams(width, WindowManager.LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.TOP or (if (layout.rightSide) Gravity.RIGHT else Gravity.LEFT)
        }
        try {
            wm.addView(w, params)
            web = w
            visible = true
        } catch (e: Exception) {
            w.destroy()
            return
        }
        // التطبيق المجاور يملأ ما تبقى من الشاشة
        if (layout != OverlayLayout.FULL && prefs.panelApp.isNotEmpty()) {
            val used = (screen.x * layout.fraction).roundToInt()
            val rest = if (layout.rightSide) Rect(0, 0, screen.x - used, screen.y) else Rect(used, 0, screen.x, screen.y)
            main.postDelayed({ AppLauncher.launch(this, prefs.panelApp, rest, AppLauncher.MODE_MULTI_WINDOW) }, 250)
        }
    }

    private fun hide() {
        val w = web
        if (w != null) {
            try { wm.removeView(w) } catch (_: Exception) {}
            w.destroy()
        }
        web = null
        visible = false
        pageReady = false
        updateMini()
    }

    private fun overlayParams(w: Int, h: Int, focusable: Boolean = false) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    )

    // ---------------- العداد العائم الصغير ----------------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    private fun updateMini() {
        if (!prefs.miniGauge || visible || !Settings.canDrawOverlays(this)) { removeMini(); return }
        if (mini != null) return
        val size = dp(96)
        val tv = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#F4E3C3"))
            typeface = Typeface.DEFAULT_BOLD
            textSize = 26f
            setLineSpacing(0f, 0.85f)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E6141210"))
                setStroke(dp(3), runCatching { Color.parseColor(prefs.accent) }.getOrDefault(Color.parseColor("#E8A24A")))
            }
        }
        val p = overlayParams(size, size).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = prefs.miniX; y = prefs.miniY
        }
        // سحب لتغيير المكان، ونقرة لإظهار العدادات كاملة
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        tv.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = p.x; startY = p.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (abs(dx) + abs(dy) > dp(6)) moved = true
                    if (moved) {
                        p.x = startX + dx.roundToInt(); p.y = startY + dy.roundToInt()
                        runCatching { wm.updateViewLayout(tv, p) }
                    }
                }
                MotionEvent.ACTION_UP -> if (moved) { prefs.miniX = p.x; prefs.miniY = p.y } else show()
            }
            true
        }
        try {
            wm.addView(tv, p)
            mini = tv; miniParams = p
            renderMini()
        } catch (_: Exception) {}
    }

    private fun renderMini() {
        val tv = mini ?: return
        val s = VehicleRepository.state
        val v = if (prefs.mph) s.speed / 1.609f else s.speed
        tv.text = "${v.roundToInt()}\n${if (prefs.mph) "mph" else "كم/س"}"
        tv.textSize = 26f
    }

    private fun removeMini() {
        val tv = mini ?: return
        runCatching { wm.removeView(tv) }
        mini = null; miniParams = null
    }

    // ---------------- تطبيق في نافذة عائمة ----------------

    private fun floatApp() {
        val pkg = prefs.floatingApp
        if (pkg.isEmpty()) return
        val screen = screenSize(this)
        val w = (screen.x * 0.42f).roundToInt()
        val h = (screen.y * 0.62f).roundToInt()
        val margin = dp(16)
        val bounds = Rect(screen.x - w - margin, screen.y - h - margin, screen.x - margin, screen.y - margin)
        hide()
        AppLauncher.launch(this, pkg, bounds, AppLauncher.MODE_FREEFORM)
        showFloatBar(bounds)
    }

    /** شريط صغير فوق النافذة العائمة: تكبير التطبيق أو إغلاقه. */
    private fun showFloatBar(bounds: Rect) {
        removeFloatBar()
        if (!Settings.canDrawOverlays(this)) return
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply { setColor(Color.parseColor("#E6141210")); cornerRadius = dp(18).toFloat() }
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }
        fun btn(label: String, onClick: () -> Unit) = TextView(this).apply {
            text = label; textSize = 20f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(6), dp(14), dp(6))
            setOnClickListener { onClick() }
        }
        val pkg = prefs.floatingApp
        bar.addView(btn("⤢") { removeFloatBar(); AppLauncher.launch(this, pkg, null, AppLauncher.MODE_FULLSCREEN) })
        bar.addView(btn("✕") { removeFloatBar(); AppLauncher.goHome(this) })
        val p = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = bounds.right - dp(130); y = (bounds.top - dp(48)).coerceAtLeast(0)
        }
        try { wm.addView(bar, p); floatBar = bar } catch (_: Exception) {}
    }

    private fun removeFloatBar() {
        val b = floatBar ?: return
        runCatching { wm.removeView(b) }
        floatBar = null
    }

    // ---------------- إرسال البيانات للثيم ----------------

    override fun onState(state: VehicleState) {
        push()
        renderMini()
    }

    override fun onSteeringKey(action: SteeringAction) {
        when (action.code) {
            prefs.toggleKey -> if (visible) hide() else show()
            prefs.nextThemeKey -> nextTheme()
            else -> web?.evaluateJavascript("window.onSahraKey && window.onSahraKey(${action.code});", null)
        }
    }

    private fun push() {
        val w = web ?: return
        if (!pageReady) return
        w.evaluateJavascript(Payload.script(Payload.build(prefs, design)), null)
    }

    private fun nextTheme() {
        prefs.themeId = Themes.next(prefs.themeId).id
        if (visible) { hide(); show() }
    }

    /** ما يستطيع الثيم طلبه من التطبيق. */
    inner class Bridge {
        @JavascriptInterface fun close() { main.post { hide() } }
        @JavascriptInterface fun nextTheme() { main.post { this@OverlayService.nextTheme() } }
        @JavascriptInterface fun media(cmd: String) { main.post { MediaTracker.command(cmd) } }
        @JavascriptInterface fun openApp(pkg: String) { main.post { hide(); AppLauncher.launch(this@OverlayService, pkg, null, AppLauncher.MODE_FULLSCREEN) } }
        @JavascriptInterface fun floatApp() { main.post { this@OverlayService.floatApp() } }
        @JavascriptInterface fun openSettings() {
            main.post {
                hide()
                startActivity(Intent(this@OverlayService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        @JavascriptInterface fun themes(): String =
            JSONArray(Themes.all.map { JSONObject().put("id", it.id).put("name", it.name) }).toString()
    }
}
