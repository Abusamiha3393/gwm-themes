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
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * خدمة أمامية تعرض الثيم فوق شاشة الوسائط في WebView، وترسل له البيانات باستمرار.
 * عقد الثيمات: يستقبل الثيم window.onSahraData(data) و window.onSahraKey(code).
 */
class OverlayService : Service(), VehicleRepository.Listener {

    companion object {
        const val ACTION_SHOW = "com.sahra.dash.SHOW"
        const val ACTION_HIDE = "com.sahra.dash.HIDE"
        const val ACTION_TOGGLE = "com.sahra.dash.TOGGLE"
        const val ACTION_RELOAD = "com.sahra.dash.RELOAD"
        const val ACTION_STOP = "com.sahra.dash.STOP"
        private const val CHANNEL = "overlay"
        private const val NOTIF_ID = 7

        @Volatile var isRunning = false
            private set

        fun send(context: Context, action: String) {
            val i = Intent(context, OverlayService::class.java).setAction(action)
            if (action == ACTION_STOP) {
                context.startService(i)
            } else {
                context.startForegroundService(i)
            }
        }
    }

    private lateinit var prefs: Prefs
    private lateinit var wm: WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var web: WebView? = null
    private var visible = false
    private var pageReady = false
    private val mediaListener: (NowPlaying) -> Unit = { push() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        prefs = Prefs(this)
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
            ACTION_RELOAD -> { VehicleRepository.forceDemo = prefs.demo; if (visible) { hide(); show() } }
            ACTION_STOP -> { hide(); stopSelf(); return START_NOT_STICKY }
            else -> show()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        hide()
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

    // ---------------- نافذة العرض ----------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun show() {
        if (visible) return
        if (!Settings.canDrawOverlays(this)) return
        val w = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
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

        val metrics = resources.displayMetrics
        val width = when (prefs.layout) {
            OverlayLayout.FULL -> WindowManager.LayoutParams.MATCH_PARENT
            OverlayLayout.LEFT, OverlayLayout.RIGHT -> metrics.widthPixels / 2
            OverlayLayout.LEFT_THIRD, OverlayLayout.RIGHT_THIRD -> metrics.widthPixels / 3
        }
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or when (prefs.layout) {
                OverlayLayout.RIGHT, OverlayLayout.RIGHT_THIRD -> Gravity.RIGHT
                else -> Gravity.LEFT
            }
        }
        try {
            wm.addView(w, params)
            web = w
            visible = true
        } catch (e: Exception) {
            w.destroy()
        }
    }

    private fun hide() {
        val w = web ?: return
        try { wm.removeView(w) } catch (_: Exception) {}
        w.destroy()
        web = null
        visible = false
        pageReady = false
    }

    // ---------------- إرسال البيانات للثيم ----------------

    override fun onState(state: VehicleState) = push()

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
        val data = VehicleRepository.state.toJson()
        val m = MediaTracker.current
        data.put("media", JSONObject().apply {
            put("title", m.title); put("artist", m.artist); put("playing", m.playing)
            put("app", m.app); put("art", m.artDataUrl)
        })
        data.put("theme", prefs.themeId)
        data.put("time", System.currentTimeMillis())
        w.evaluateJavascript(
            "(function(d){window.SahraData=d;if(window.onSahraData)window.onSahraData(d);})($data);",
            null,
        )
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
