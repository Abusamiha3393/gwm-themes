package com.sahra.split

import android.annotation.SuppressLint
import android.app.ActivityOptions
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
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject

/** ثيم عدادات (ملف HTML في assets/themes) يستقبل البيانات عبر window.onSahraData. */
data class ClusterTheme(val id: String, val name: String, val note: String, val file: String = "$id.html") {
    val url get() = "file:///android_asset/themes/$file"
}

object ClusterThemes {
    val all = listOf(
        // ثيم صور من تصميم صاحب التطبيق، بخلفيتين
        ClusterTheme("haval", "هافال", "معدني رمادي", "image.html?t=haval&v=gray"),
        ClusterTheme("haval_blue", "هافال أزرق", "معدني بإضاءة", "image.html?t=haval&v=blue"),
        ClusterTheme("dune", "كثبان", "عدادات كلاسيكية"),
        ClusterTheme("horizon", "أفق", "قوس عريض"),
        ClusterTheme("pulse", "نبض", "رقمي"),
        ClusterTheme("track", "مضمار", "أضواء التبديل"),
        ClusterTheme("trail", "مسار", "الطرق الوعرة"),
        ClusterTheme("journey", "رحلة", "الوقت والموسيقى"),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.first()

    /** ألوان الإبرة والعلامات المتاحة للثيمات. */
    val ACCENTS = listOf("#E8A24A", "#E5583F", "#9CC46B", "#4FA3E8", "#EDEDED", "#A98BF0")
}

/** البيانات التي تُرسل للثيم بنفس صيغة صحراء داش. */
object ClusterPayload {
    /** نص القياسات الذي يظهر وسط الثيم عند تفعيل «قياسات الشاشة». */
    @Volatile var debugInfo = ""

    /** العنصر المحدد في محرر الثيم، فارغ خارج المحرر. */
    @Volatile var editSel = ""

    /** هل يحتاج الثيم ثقبًا شفافًا للملاحة الآن. */
    fun navHole(p: Prefs) = p.navOnCluster && !p.navClusterFull

    private fun JSONObject.num(name: String, v: Float) = put(name, if (v.isNaN()) JSONObject.NULL else v.toDouble())

    private fun nums(v: List<Float>) = JSONArray().apply { v.forEach { put(if (it.isNaN()) JSONObject.NULL else it.toDouble()) } }

    private fun alerts(s: VehicleState, tireWarnPsi: Int): JSONArray {
        val out = JSONArray()
        fun add(id: String, text: String, level: String) = out.put(JSONObject().put("id", id).put("text", text).put("level", level))
        if (!s.fuelPercent.isNaN()) when {
            s.fuelPercent <= 7f -> add("fuel", "الوقود منخفض جدًا", "danger")
            s.fuelPercent <= 12f -> add("fuel", "الوقود منخفض", "warn")
        }
        if (!s.coolantTemp.isNaN()) when {
            s.coolantTemp >= 115f -> add("coolant", "حرارة المحرك مرتفعة جدًا — توقف بأمان", "danger")
            s.coolantTemp >= 106f -> add("coolant", "حرارة المحرك مرتفعة", "warn")
        }
        if (!s.batteryVoltage.isNaN() && s.batteryVoltage > 1f) {
            if (s.rpm > 600f && s.batteryVoltage < 12.6f) add("battery", "البطارية لا تشحن", "warn")
            else if (s.batteryVoltage < 11.8f) add("battery", "جهد البطارية منخفض", "warn")
        }
        val low = s.tirePsi.filter { !it.isNaN() && it < tireWarnPsi }
        if (low.isNotEmpty()) add("tire", "ضغط إطار منخفض", if (low.any { it < tireWarnPsi * 0.85f }) "danger" else "warn")
        if (!s.pitch.isNaN() && kotlin.math.abs(s.pitch) >= 30f) add("pitch", "ميلان أمامي خطر", "danger")
        if (!s.roll.isNaN() && kotlin.math.abs(s.roll) >= 30f) add("roll", "ميلان جانبي خطر", "danger")
        return out
    }

    fun build(context: Context, s: VehicleState = VehicleRepository.state): JSONObject {
        val p = Prefs(context)
        val d = JSONObject()
        d.put("speed", s.speed.toDouble()); d.put("rpm", s.rpm.toDouble())
        d.put("gear", Gears.letter(context, s)); d.put("gearLabel", Gears.label(context, s))
        d.put("gearNo", if (s.gearNo >= 0) s.gearNo else JSONObject.NULL)
        // الإطارات: أمامي يسار، أمامي يمين، خلفي يسار، خلفي يمين
        d.put("tires", nums(s.tirePsi)); d.put("tireTemps", nums(s.tireTemp)); d.put("tireWarnPsi", p.tireWarnPsi)
        d.num("journey", s.journeyKm); d.num("trip", s.tripKm); d.num("avgConsumption", s.avgConsumption)
        d.num("baro", s.baroHpa)
        d.num("fuel", s.fuelPercent); d.num("range", s.rangeKm); d.num("odometer", s.odometerKm)
        d.num("coolant", s.coolantTemp); d.num("outsideTemp", s.outsideTemp); d.num("insideTemp", s.insideTemp)
        d.num("batteryVoltage", s.batteryVoltage); d.num("batteryLevel", s.batteryLevel)
        d.num("steering", s.steeringAngle); d.num("pitch", s.pitch); d.num("roll", s.roll)
        d.num("compass", s.compass); d.num("altitude", s.altitude)
        d.put("vin", ""); d.put("live", s.live); d.put("demo", s.demo)
        val m = MediaTracker.current
        d.put("media", JSONObject().put("title", m.title).put("artist", m.artist).put("playing", m.playing).put("app", m.app).put("art", ""))
        d.put("style", JSONObject().apply {
            put("accent", p.clusterAccent); put("speedMax", p.speedMax); put("rpmMax", p.rpmMax)
            put("units", "kmh"); put("wallpaper", JSONObject.NULL); put("wallpaperDim", 0.55)
            put("design", JSONObject().put("needleStyle", "classic").put("scale", 100).put("shift", 0).put("swap", false))
        })
        d.put("alerts", alerts(s, p.tireWarnPsi))
        // نافذة الملاحة: الثيم يفتح ثقبًا شفافًا فيها حين تكون الملاحة على العدادات داخل الثيم
        d.put("nav", JSONObject().put("on", p.navOnCluster).put("full", p.navClusterFull)
            .put("x", p.navX.toDouble()).put("y", p.navY.toDouble()).put("w", p.navW.toDouble()).put("h", p.navH.toDouble()))
        // تعديلات محرر الثيم، والعنصر المحدد في المحرر (يُحاط بإطار)
        val edit = runCatching { JSONObject(p.themeEdit(p.clusterTheme)) }.getOrDefault(JSONObject())
        if (editSel.isNotEmpty()) edit.put("sel", editSel)
        d.put("edit", edit)
        d.put("layout", "cluster")
        if (p.clusterDebug) d.put("debug", debugInfo)
        d.put("theme", p.clusterTheme)
        d.put("time", System.currentTimeMillis())
        return d
    }

    /**
     * viewW/viewH: مقاس نافذة الثيم الحقيقي بالبكسل. على شاشة العدادات يخطئ المتصفح في الكثافة
     * فيرسم الصفحة أكبر من النافذة، فيصغّرها الثيم بهذين الرقمين حتى تطابق الشاشة.
     */
    fun script(context: Context, viewW: Int = 0, viewH: Int = 0) =
        "(function(d){d.view={w:$viewW,h:$viewH};window.SahraData=d;if(window.__sahra)window.__sahra(d);else if(window.onSahraData)window.onSahraData(d);})(${build(context)});"
}

/** WebView يعرض ثيمًا ويغذّيه بالبيانات، يُستخدم على شاشة العدادات وفي المعاينة. */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
class ThemeView(context: Context, private val interactive: Boolean) : WebView(context), VehicleRepository.Listener {
    private var ready = false
    private var lastPush = 0L
    private var themeId = ""
    private var transparent = false

    /** على شاشة العدادات فقط: يُسمح بالخلفية الشفافة لتظهر الملاحة من الثقب. */
    var allowHole = false

    /** يستقبل قائمة عناصر الثيم القابلة للتحرير (JSON) حين يبلّغ بها الثيم. */
    var onElements: ((String) -> Unit)? = null

    init {
        setBackgroundColor(Color.BLACK)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        if (!interactive) { isFocusable = false; isClickable = false }
        // الثيمات تتحقق من وجود SahraHost لتعرف أنها داخل التطبيق لا في متصفح
        addJavascriptInterface(Host(), "SahraHost")
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) { ready = true; push(force = true) }
        }
    }

    /** إعادة تحميل الثيم الحالي حتى إن لم يتغير (بعد تعديل يمس بناءه). */
    fun reloadTheme() { val id = themeId; themeId = ""; load(id) }

    fun load(id: String) {
        if (id == themeId) return
        themeId = id
        ready = false
        loadUrl(ClusterThemes.byId(id).url)
    }

    fun push(force: Boolean = false) {
        if (!ready) return
        val now = System.currentTimeMillis()
        if (!force && now - lastPush < 80) return
        lastPush = now
        // خلفية شفافة فقط حين تظهر الملاحة من خلال الثيم
        val hole = allowHole && ClusterPayload.navHole(Prefs(context))
        if (hole != transparent) { transparent = hole; setBackgroundColor(if (hole) Color.TRANSPARENT else Color.BLACK) }
        evaluateJavascript(ClusterPayload.script(context, width, height), null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        VehicleRepository.addListener(this)
    }

    override fun onDetachedFromWindow() {
        VehicleRepository.removeListener(this)
        super.onDetachedFromWindow()
    }

    override fun onState(state: VehicleState) = push()

    override fun onTouchEvent(event: android.view.MotionEvent?): Boolean = if (interactive) super.onTouchEvent(event) else false

    private inner class Host {
        @JavascriptInterface fun close() {}
        @JavascriptInterface fun nextTheme() {}
        @JavascriptInterface fun media(cmd: String) { Handler(Looper.getMainLooper()).post { MediaTracker.command(cmd) } }
        @JavascriptInterface fun openApp(pkg: String) {}
        @JavascriptInterface fun floatApp() {}
        @JavascriptInterface fun openSettings() {}
        @JavascriptInterface fun elements(json: String) {
            Handler(Looper.getMainLooper()).post { onElements?.invoke(json) }
        }
        @JavascriptInterface fun themes(): String =
            JSONArray(ClusterThemes.all.map { JSONObject().put("id", it.id).put("name", it.name) }).toString()
    }
}

/**
 * يعرض الثيم على شاشة العدادات خلف المقود. في شاشات GWM هي شاشة ثانية (رقمها غالبًا 2، 1920×800).
 * الطريقة: نافذة عائمة على تلك الشاشة عبر createDisplayContext، وإن رفضها النظام نفتح نشاطًا عليها.
 */
class ClusterService : Service() {

    companion object {
        private const val TAG = "Cluster"
        private const val CHANNEL = "cluster"
        private const val NOTIF_ID = 12
        @Volatile var status = ""
            private set
        @Volatile var running = false
            private set

        /** الخدمة الحالية، لإخفاء الثيم فورًا قبل نقل الملاحة (على الخيط الرئيسي). */
        @Volatile var instance: ClusterService? = null
            private set

        /** شاشة العدادات: الشاشة رقم 2 إن وُجدت، وإلا أول شاشة غير الرئيسية. */
        fun findDisplay(context: Context): Display? {
            val dm = context.getSystemService(DisplayManager::class.java) ?: return null
            return dm.getDisplay(2) ?: dm.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        }

        /** نسبة عرض نافذة الثيم إلى ارتفاعها، للمعاينة في الشاشة الرئيسية. */
        fun aspect(context: Context): Float {
            val d = findDisplay(context) ?: return 2.4f
            val m = android.util.DisplayMetrics().also { @Suppress("DEPRECATION") d.getRealMetrics(it) }
            val p = Prefs(context)
            val w = m.widthPixels * (100 - 2 * p.clusterSide) / 100f
            val h = m.heightPixels * (100 - p.clusterTop - p.clusterBottom) / 100f
            return if (h > 0) w / h else 2.4f
        }

        fun displaysInfo(context: Context): String {
            val dm = context.getSystemService(DisplayManager::class.java) ?: return "—"
            return dm.displays.joinToString("، ") { d ->
                val m = android.util.DisplayMetrics().also { @Suppress("DEPRECATION") d.getRealMetrics(it) }
                "${d.displayId}: ${m.widthPixels}×${m.heightPixels}"
            }
        }

        /** يشغّل العرض على العدادات أو يوقفه حسب الإعداد. */
        fun sync(context: Context) {
            val p = Prefs(context)
            val i = Intent(context, ClusterService::class.java)
            if (p.clusterOn && Settings.canDrawOverlays(context)) runCatching { context.startForegroundService(i) }
            else context.stopService(i)
        }

        fun reload(context: Context) {
            if (running) runCatching { context.startForegroundService(Intent(context, ClusterService::class.java).setAction("reload")) }
        }
    }

    private var view: ThemeView? = null
    private var wm: WindowManager? = null
    private var shown: Display? = null

    /** موضع الثيم وحجمه على شاشة العدادات حسب الهوامش التي يضبطها السائق (نسبة من الشاشة). */
    private fun frame(display: Display, lp: WindowManager.LayoutParams) {
        val m = android.util.DisplayMetrics().also { @Suppress("DEPRECATION") display.getRealMetrics(it) }
        val p = Prefs(this)
        val x = m.widthPixels * p.clusterSide / 100
        val top = m.heightPixels * p.clusterTop / 100
        val bottom = m.heightPixels * p.clusterBottom / 100
        lp.gravity = android.view.Gravity.TOP or android.view.Gravity.START
        lp.x = x; lp.y = top
        lp.width = m.widthPixels - 2 * x
        lp.height = m.heightPixels - top - bottom
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** يدفع البيانات للثيم الآن (بعد تعديل في المحرر) بدل انتظار القراءة التالية. */
    fun pushNow() {
        view?.push(force = true)
    }

    /** يعيد تحميل صفحة الثيم (تغيّر بناؤه، مثل الخلفية). */
    fun reloadPage() {
        view?.reloadTheme()
    }

    /** يزيل الثيم من العدادات الآن، قبل أن يُفتح تطبيق الملاحة هناك. */
    fun hideForNav() {
        remove()
        status = "الملاحة معروضة على شاشة العدادات"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        running = true
        goForeground()
        VehicleRepository.start(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        // الملاحة على العدادات بملء الشاشة: نُخفي الثيم حتى تعود. داخل الثيم: يبقى الثيم ويُفتح فيه ثقب
        val pr = Prefs(this)
        if (pr.navOnCluster && pr.navClusterFull) {
            remove()
            status = "الملاحة معروضة على شاشة العدادات"
            return START_STICKY
        }
        val theme = Prefs(this).clusterTheme
        val v = view
        if (v != null) {
            v.load(theme)
            v.push(force = true)
            // تحديث الهوامش إن تغيّرت
            val d = shown
            if (d != null) runCatching {
                val lp = v.layoutParams as WindowManager.LayoutParams
                frame(d, lp)
                wm?.updateViewLayout(v, lp)
            }
        } else show(theme)
        return START_STICKY
    }

    override fun onDestroy() {
        remove()
        instance = null
        running = false
        status = ""
        super.onDestroy()
    }

    private fun show(theme: String) {
        val display = findDisplay(this)
        if (display == null) {
            status = "لم يُعثر على شاشة العدادات. الشاشات: ${displaysInfo(this)}"
            Log.w(TAG, status)
            return
        }
        try {
            val dctx = createDisplayContext(display)
            val w = dctx.getSystemService(WindowManager::class.java)
            val v = ThemeView(dctx, interactive = false).apply { allowHole = true }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT,
            )
            frame(display, lp)
            val m = android.util.DisplayMetrics().also { @Suppress("DEPRECATION") display.getRealMetrics(it) }
            ClusterPayload.debugInfo = "display ${display.displayId} ${m.widthPixels}x${m.heightPixels} d=${m.density}\n" +
                "window ${lp.width}x${lp.height} at ${lp.x},${lp.y}\nall: ${displaysInfo(this)}"
            w.addView(v, lp)
            v.load(theme)
            view = v; wm = w; shown = display
            status = "يعرض على الشاشة ${display.displayId}"
        } catch (e: Exception) {
            Log.w(TAG, "overlay on display ${display.displayId} failed: ${e.message}")
            // البديل: نشاط على شاشة العدادات
            try {
                val opts = ActivityOptions.makeBasic().setLaunchDisplayId(display.displayId)
                val m = android.util.DisplayMetrics().also { @Suppress("DEPRECATION") display.getRealMetrics(it) }
                opts.setLaunchBounds(Rect(0, 0, m.widthPixels, m.heightPixels))
                startActivity(Intent(this, ClusterActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK), opts.toBundle())
                status = "يعرض على الشاشة ${display.displayId} (نشاط)"
            } catch (e2: Exception) {
                status = "تعذر العرض على شاشة العدادات: ${e2.message}"
                Log.w(TAG, status)
            }
        }
    }

    private fun remove() {
        view?.let { v -> runCatching { wm?.removeView(v) }; v.destroy() }
        view = null; wm = null; shown = null
        ClusterActivity.finishAll()
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "شاشة العدادات", NotificationManager.IMPORTANCE_MIN))
        }
        val open = PendingIntent.getActivity(this, 2, Intent(this, HomeActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("صحراء سبليت")
            .setContentText("الثيم يعمل على شاشة العدادات")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIF_ID, n)
    }
}

/** بديل النافذة العائمة: نشاط كامل على شاشة العدادات يعرض الثيم. */
class ClusterActivity : android.app.Activity() {
    companion object {
        private val open = mutableListOf<ClusterActivity>()
        fun finishAll() { open.toList().forEach { it.finish() } }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        open.add(this)
        VehicleRepository.start(this)
        val v = ThemeView(this, interactive = false)
        setContentView(v)
        v.load(Prefs(this).clusterTheme)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    override fun onDestroy() {
        open.remove(this)
        super.onDestroy()
    }
}
