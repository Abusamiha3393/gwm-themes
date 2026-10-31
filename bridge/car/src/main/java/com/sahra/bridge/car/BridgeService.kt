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
    private lateinit var uploads: Uploads

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        uploads = Uploads(this)
        Thread { uploads.cleanup() }.start()
        startInForeground()
        startServer()
        advertise()
        running = true
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // أزرار إشعار طلب الاقتران (حين لا تتوفر البطاقة العائمة)
        val id = intent?.getStringExtra(EXTRA_REQUEST)
        when (intent?.action) {
            ACTION_APPROVE -> if (id != null) approve(id)
            ACTION_DENY -> if (id != null) deny(id)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        instance = null
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
        req.method == "GET" && (req.path == "/" || req.path == "/index.html" || req.path.startsWith("/p/")) -> page()
        req.method == "GET" && req.path == "/app.apk" -> phoneApk()
        req.method == "POST" && req.path == "/v1/web/claim" -> claim(req)
        req.method == "POST" && req.path == "/v1/pair" -> pair(req)
        req.method == "POST" && req.path == "/v1/pair/request" -> pairRequest(req)
        req.method == "GET" && req.path.startsWith("/v1/pair/request/") -> pairStatus(req.path.substringAfterLast('/'))
        req.method == "POST" && req.path == "/v1/uploads" -> uploadStart(req)
        req.method == "GET" && req.path.startsWith("/v1/uploads/") -> uploadStatus(req)
        req.method == "PUT" && req.path.startsWith("/v1/uploads/") -> uploadChunk(req)
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
        .put("features", org.json.JSONArray().put("approve").put("files"))

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

    // ---- صفحة الويب وتطبيق الجوال المضمّن ----

    private fun page(): Response = try {
        Response.raw(200, assets.open("web/index.html").use { it.readBytes() }, "text/html; charset=utf-8")
    } catch (e: IOException) {
        Response(500, """{"error":"no_page"}""")
    }

    /** تطبيق الجوال مضمَّن في هذا التطبيق عند البناء (assets/phone.apk). */
    private fun phoneApk(): Response = try {
        val bytes = assets.open("phone.apk").use { it.readBytes() }
        Response.raw(
            200, bytes, Uploads.APK_MIME,
            mapOf("Content-Disposition" to "attachment; filename=\"HavalBridge-Phone.apk\""),
        )
    } catch (e: IOException) {
        Response.raw(404, "تطبيق الجوال غير مضمّن في هذه النسخة".toByteArray(), "text/plain; charset=utf-8")
    }

    /** المتصفح يقدّم مفتاح QR (لمرة واحدة) ويأخذ رمز جلسة دائمًا. */
    private fun claim(req: Request): Response {
        if (System.currentTimeMillis() < lockedUntil) return Response(429, """{"error":"locked"}""")
        val body = try {
            JSONObject(req.body)
        } catch (e: Exception) {
            return Response(400, """{"error":"bad_json"}""")
        }
        if (!store.consumeWebKey(body.optString("key"))) {
            if (failures.incrementAndGet() >= MAX_FAILURES) {
                lockedUntil = System.currentTimeMillis() + LOCK_MS
                failures.set(0)
            }
            return Response(403, """{"error":"expired"}""")
        }
        failures.set(0)
        val name = body.optString("name").trim().ifEmpty { "جوال" }
        val token = store.addDevice(name)
        main.post {
            Actions.toast(this, "تم ربط $name ✓")
            changed()
        }
        return ok(info().put("token", token).put("host", req.headers["host"]?.substringBefore(':') ?: ""))
    }

    // ---- الاقتران بالموافقة على الشاشة ----

    private fun pairRequest(req: Request): Response {
        val body = try {
            JSONObject(req.body)
        } catch (e: Exception) {
            return Response(400, """{"error":"bad_json"}""")
        }
        val phone = body.optString("phone").trim().ifEmpty { "جوال" }
        val r = Pairing.open(phone) ?: return Response(429, """{"error":"slow_down"}""")
        main.post { askApproval(r) }
        return ok(JSONObject().put("request", r.id).put("ttl", PairRequest.TTL))
    }

    private fun pairStatus(id: String): Response {
        val r = Pairing.find(id) ?: return ok(JSONObject().put("status", PairRequest.EXPIRED))
        val o = JSONObject().put("status", r.status)
        val token = r.token
        if (r.status == PairRequest.APPROVED && token != null) {
            info().keys().forEach { k -> o.put(k, info().get(k)) }
            o.put("token", token)
            Pairing.consumed(id)
        }
        return ok(o)
    }

    private fun approve(id: String) {
        removeCard()
        cancelPairNotification()
        val r = Pairing.approve(this, id) ?: return
        Actions.toast(this, "تم اقتران ${r.phone} ✓")
        changed()
    }

    private fun deny(id: String) {
        removeCard()
        cancelPairNotification()
        Pairing.deny(id)
        changed()
    }

    private fun askApproval(r: PairRequest) {
        changed()
        if (!Settings.canDrawOverlays(this)) {
            notifyPair(r)
            return
        }
        val ui = Ui(this)
        val box = ui.column(22).apply {
            background = ui.rounded(Palette.asphalt, 16, Palette.amber)
            elevation = ui.dp(8).toFloat()
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        box.addView(ui.text("📱  طلب اقتران جديد", 15f, Palette.amber))
        box.addView(ui.text("السماح لـ «${r.phone}» بالإرسال إلى الشاشة؟", 22f, bold = true))
        box.addView(ui.text("بعد الموافقة يتصل تلقائيًا في كل مرة دون رمز.", 15f, Palette.grey))
        box.addView(ui.gap(14))
        val buttons = ui.row()
        buttons.addView(ui.button("سماح", Palette.green, heightDp = 76) { approve(r.id) }, ui.weight(1f))
        buttons.addView(ui.gap(12))
        buttons.addView(ui.button("رفض", Palette.kerb, heightDp = 76) { deny(r.id) }, ui.weight(1f))
        box.addView(buttons, ui.matchWidth())
        addCard(box, PairRequest.TTL)
    }

    private fun notifyPair(r: PairRequest) {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH_ITEMS) == null) {
            nm.createNotificationChannel(NotificationChannel(CH_ITEMS, "العناصر الواردة", NotificationManager.IMPORTANCE_HIGH))
        }
        fun action(a: String, code: Int) = PendingIntent.getService(
            this, code, Intent(this, BridgeService::class.java).setAction(a).putExtra(EXTRA_REQUEST, r.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(this, CH_ITEMS)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("طلب اقتران من ${r.phone}")
            .setContentText("اسمح له بالإرسال إلى الشاشة؟")
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_launcher), "سماح", action(ACTION_APPROVE, 11)).build())
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_launcher), "رفض", action(ACTION_DENY, 12)).build())
            .setTimeoutAfter(PairRequest.TTL)
            .build()
        nm.notify(PAIR_NOTIF, n)
    }

    private fun cancelPairNotification() = getSystemService(NotificationManager::class.java).cancel(PAIR_NOTIF)

    // ---- الملفات ----

    private fun uploadStart(req: Request): Response {
        val device = auth(req) ?: return unauthorized()
        val body = try {
            JSONObject(req.body)
        } catch (e: Exception) {
            return Response(400, """{"error":"bad_json"}""")
        }
        val r = uploads.start(device, body)
        return Response(r.code, r.json.toString())
    }

    private fun uploadStatus(req: Request): Response {
        auth(req) ?: return unauthorized()
        val r = uploads.status(req.path.substringAfterLast('/'))
        return Response(r.code, r.json.toString())
    }

    private fun uploadChunk(req: Request): Response {
        auth(req) ?: return unauthorized()
        val offset = req.headers["x-offset"]?.toLongOrNull() ?: return Response(400, """{"error":"no_offset"}""")
        val r = uploads.chunk(req.path.substringAfterLast('/'), offset, req.bytes)
        val item = r.item
        if (item != null) {
            store.addItem(item)
            main.post { arrived(item) }
        }
        return Response(r.code, r.json.toString())
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
        box.addView(ui.text("${Actions.icon(item)}  من ${item.from}", 15f, Palette.grey))
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
        buttons.addView(ui.button(Actions.openLabel(item), Palette.green, heightDp = 76) {
            removeCard()
            Actions.open(this, item, store)
            changed()
        }, ui.weight(1f))
        buttons.addView(ui.gap(12))
        buttons.addView(ui.button("لاحقًا", Palette.kerb, heightDp = 76) { removeCard() }, ui.weight(1f))
        box.addView(buttons, ui.matchWidth())
        if (!addCard(box, CARD_MS)) notifyItem(item)
    }

    /** يعرض بطاقة عائمة أعلى الشاشة، وتختفي بعد المدة المحددة. */
    private fun addCard(box: View, durationMs: Long): Boolean {
        removeCard()
        val ui = Ui(this)
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
        return try {
            wm.addView(box, lp)
            card = box
            main.postDelayed(hideCard, durationMs)
            true
        } catch (e: Exception) {
            Log.w(TAG, "overlay failed", e)
            false
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
            .setContentTitle("${Actions.icon(item)} ${item.title}")
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
        private const val PAIR_NOTIF = 8
        const val ACTION_APPROVE = "com.sahra.bridge.car.APPROVE"
        const val ACTION_DENY = "com.sahra.bridge.car.DENY"
        const val EXTRA_REQUEST = "request"
        private const val MAX_FAILURES = 5
        private const val LOCK_MS = 10 * 60 * 1000L
        private const val CARD_MS = 20_000L

        @Volatile var running = false
            private set

        @Volatile private var instance: BridgeService? = null

        /** موافقة أو رفض من شاشة التطبيق نفسها. */
        fun answer(id: String, allow: Boolean) {
            val s = instance ?: return
            if (allow) s.approve(id) else s.deny(id)
        }

        /** تستدعيه الواجهة لتحدّث نفسها عند وصول عنصر أو اقتران جهاز. يُستدعى على الخيط الرئيسي. */
        var onChanged: (() -> Unit)? = null

        private fun changed() = onChanged?.invoke()

        fun start(context: Context) {
            context.startForegroundService(Intent(context, BridgeService::class.java))
        }
    }
}
