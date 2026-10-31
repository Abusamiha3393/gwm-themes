package com.sahra.bridge.car

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
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * يعمل طوال تشغيل الشاشة: خادم HTTP على المنفذ 47800 + إعلان mDNS،
 * ويعرض بطاقة عائمة لكل عنصر يصل من الجوال.
 */
class BridgeService : Service() {
    private lateinit var store: Store
    private var server: HttpServer? = null
    private var nsd: NsdManager? = null
    private var registration: NsdManager.RegistrationListener? = null
    private val main = Handler(Looper.getMainLooper())
    private var card: View? = null
    private val hideCard = Runnable { removeCard() }
    private val failures = AtomicInteger()
    @Volatile private var lockedUntil = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        startInForeground()
        startServer()
        advertise()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        running = false
        main.removeCallbacksAndMessages(null)
        removeCard()
        server?.stop()
        server = null
        try {
            registration?.let { nsd?.unregisterService(it) }
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    // ---- الخادم ----

    private fun startServer() {
        val s = HttpServer(PORT, ::handle)
        try {
            s.start()
            server = s
        } catch (e: IOException) {
            // المنفذ مشغول مؤقتًا بعد إعادة التشغيل: نحاول مجددًا
            Log.w(TAG, "bind failed, retrying", e)
            main.postDelayed({ if (server == null) startServer() }, 3000)
        }
    }

    private fun handle(req: Request): Response = when {
        req.method == "GET" && req.path == "/v1/info" -> ok(info())
        req.method == "POST" && req.path == "/v1/pair" -> pair(req)
        req.method == "GET" && req.path == "/v1/ping" ->
            if (auth(req) != null) ok(JSONObject().put("ok", true)) else unauthorized()
        req.method == "POST" && req.path == "/v1/items" -> receive(req)
        else -> Response(404, """{"error":"not_found"}""")
    }

    private fun info() = JSONObject()
        .put("app", "havalbridge")
        .put("protocol", PROTOCOL)
        .put("id", store.deviceId)
        .put("name", store.carName)

    private fun pair(req: Request): Response {
        if (System.currentTimeMillis() < lockedUntil) return Response(429, """{"error":"locked"}""")
        val body = try {
            JSONObject(req.body)
        } catch (e: Exception) {
            return Response(400, """{"error":"bad_json"}""")
        }
        val code = body.optString("code")
        val phone = body.optString("phone").trim().ifEmpty { "جوال" }
        if (!store.consumeCode(code)) {
            if (failures.incrementAndGet() >= MAX_FAILURES) {
                lockedUntil = System.currentTimeMillis() + LOCK_MS
                failures.set(0)
            }
            return Response(403, """{"error":"wrong_code"}""")
        }
        failures.set(0)
        val token = store.addDevice(phone)
        main.post {
            Actions.toast(this, "تم اقتران $phone")
            changed()
        }
        return ok(info().put("token", token))
    }

    private fun receive(req: Request): Response {
        val device = auth(req) ?: return unauthorized()
        val body = try {
            JSONObject(req.body)
        } catch (e: Exception) {
            return Response(400, """{"error":"bad_json"}""")
        }
        val item = Actions.parse(body, device.name) ?: return Response(400, """{"error":"bad_item"}""")
        store.addItem(item)
        main.post { arrived(item) }
        return ok(JSONObject().put("ok", true).put("id", item.id))
    }

    private fun auth(req: Request): Device? {
        val h = req.headers["authorization"] ?: return null
        if (!h.startsWith("Bearer ")) return null
        return store.findDevice(h.removePrefix("Bearer ").trim())
    }

    private fun ok(o: JSONObject) = Response(200, o.toString())
    private fun unauthorized() = Response(401, """{"error":"unauthorized"}""")

    // ---- mDNS ----

    private fun advertise() {
        val manager = getSystemService(Context.NSD_SERVICE) as NsdManager
        nsd = manager
        val info = NsdServiceInfo()
        info.serviceName = "HavalBridge-" + store.deviceId.take(6)
        info.serviceType = SERVICE_TYPE
        info.port = PORT
        info.setAttribute("id", store.deviceId)
        info.setAttribute("name", store.carName)
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(s: NsdServiceInfo) {
                Log.i(TAG, "mDNS registered ${s.serviceName}")
            }

            override fun onRegistrationFailed(s: NsdServiceInfo, code: Int) {
                Log.w(TAG, "mDNS failed $code")
            }

            override fun onServiceUnregistered(s: NsdServiceInfo) {}
            override fun onUnregistrationFailed(s: NsdServiceInfo, code: Int) {}
        }
        registration = listener
        try {
            manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.w(TAG, "mDNS unavailable", e)
        }
    }

    // ---- وصول عنصر ----

    private fun arrived(item: Item) {
        changed()
        if (item.type == "geo" && store.autoOpenGeo) {
            Actions.open(this, item, store)
            changed()
            return
        }
        if (Settings.canDrawOverlays(this)) showCard(item) else notifyItem(item)
    }

    @SuppressLint("RtlHardcoded")
    private fun showCard(item: Item) {
        removeCard()
        val ui = Ui(this)
        val box = ui.column(22).apply {
            background = ui.rounded(Palette.asphalt, 16, Palette.laneDash)
            elevation = ui.dp(8).toFloat()
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        box.addView(ui.text("${Actions.icon(item.type)}  من ${item.from}", 15f, Palette.grey))
        box.addView(ui.text(item.title, 24f, bold = true).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        if (item.detail != item.title) {
            box.addView(ui.text(item.detail, 16f, Palette.grey).apply {
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            })
        }
        box.addView(ui.gap(14))
        val buttons = ui.row()
        val openLabel = when (item.type) {
            "geo" -> "ابدأ الملاحة"
            "text" -> "انسخ"
            else -> "افتح"
        }
        buttons.addView(ui.button(openLabel, Palette.green, heightDp = 76) {
            removeCard()
            Actions.open(this, item, store)
            changed()
        }, ui.weight(1f))
        buttons.addView(ui.gap(12))
        buttons.addView(ui.button("لاحقًا", Palette.kerb, heightDp = 76) { removeCard() }, ui.weight(1f))
        box.addView(buttons, ui.matchWidth())

        val lp = WindowManager.LayoutParams(
            ui.dp(560),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.y = ui.dp(24)
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try {
            wm.addView(box, lp)
            card = box
            main.postDelayed(hideCard, CARD_MS)
        } catch (e: Exception) {
            Log.w(TAG, "overlay failed", e)
            notifyItem(item)
        }
    }

    private fun removeCard() {
        main.removeCallbacks(hideCard)
        val v = card ?: return
        card = null
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (_: Exception) {
        }
    }

    private fun notifyItem(item: Item) {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH_ITEMS) == null) {
            nm.createNotificationChannel(NotificationChannel(CH_ITEMS, "العناصر الواردة", NotificationManager.IMPORTANCE_HIGH))
        }
        val open = PendingIntent.getActivity(
            this, item.id.hashCode(),
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, item.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(this, CH_ITEMS)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("${Actions.icon(item.type)} ${item.title}")
            .setContentText("من ${item.from} — اضغط للفتح")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(item.id.hashCode(), n)
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH_SERVICE) == null) {
            nm.createNotificationChannel(NotificationChannel(CH_SERVICE, "خدمة الجسر", NotificationManager.IMPORTANCE_MIN))
        }
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(this, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("جاهز لاستقبال العناصر من الجوال")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    companion object {
        private const val TAG = "HavalBridge"
        const val PORT = 47800
        const val PROTOCOL = 1
        const val SERVICE_TYPE = "_havalbridge._tcp"
        private const val CH_SERVICE = "service"
        private const val CH_ITEMS = "items"
        private const val NOTIF_ID = 7
        private const val MAX_FAILURES = 5
        private const val LOCK_MS = 10 * 60 * 1000L
        private const val CARD_MS = 20_000L

        @Volatile var running = false
            private set

        /** تستدعيه الواجهة لتحدّث نفسها عند وصول عنصر أو اقتران جهاز. يُستدعى على الخيط الرئيسي. */
        var onChanged: (() -> Unit)? = null

        private fun changed() = onChanged?.invoke()

        fun start(context: Context) {
            context.startForegroundService(Intent(context, BridgeService::class.java))
        }
    }
}
