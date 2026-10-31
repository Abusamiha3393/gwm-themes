package com.sahra.bridge.car

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var store: Store
    private lateinit var ui: Ui
    private lateinit var right: LinearLayout
    private lateinit var left: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 15_000)
        }
    }
    private val time = SimpleDateFormat("HH:mm", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        ui = Ui(this)
        BridgeService.start(this)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        right = ui.column(4)
        left = ui.column(4)
        val root = ui.row().apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(Palette.asphalt)
            setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16))
            gravity = android.view.Gravity.TOP
        }
        root.addView(scroll(right), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        root.addView(ui.gap(16))
        root.addView(scroll(left), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.3f))
        setContentView(root)
    }

    private fun scroll(content: View) = ScrollView(this).apply {
        isVerticalScrollBarEnabled = false
        addView(content)
    }

    override fun onResume() {
        super.onResume()
        BridgeService.onChanged = { render() }
        handler.post(tick)
        handleOpen(intent)
    }

    override fun onPause() {
        BridgeService.onChanged = null
        handler.removeCallbacks(tick)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    /** فتح عنصر من الإشعار. */
    private fun handleOpen(i: Intent?) {
        if (i == null) return
        val id = i.getStringExtra(EXTRA_OPEN) ?: return
        i.removeExtra(EXTRA_OPEN)
        val item = store.inbox().firstOrNull { it.id == id } ?: return
        Actions.open(this, item, store)
    }

    private fun render() {
        renderRight()
        renderLeft()
    }

    // ---- العمود الأيمن: الاقتران والإعدادات ----

    private fun renderRight() {
        right.removeAllViews()
        right.addView(ui.text("جسر الهافال", 30f, bold = true))
        val status = if (BridgeService.running) "● يعمل — ${store.carName}" else "○ الخدمة متوقفة"
        right.addView(ui.text(status, 16f, if (BridgeService.running) Palette.amber else Palette.grey))
        right.addView(ui.gap(14))

        Pairing.pending()?.let { r ->
            val ask = ui.column(22).apply { background = ui.rounded(Palette.shoulder, 14, Palette.amber) }
            ask.addView(ui.text("📱  طلب اقتران من «${r.phone}»", 20f, Palette.amber, bold = true))
            ask.addView(ui.gap(10))
            val buttons = ui.row()
            buttons.addView(ui.button("سماح", Palette.green, heightDp = 64) {
                BridgeService.answer(r.id, true)
                render()
            }, ui.weight(1f))
            buttons.addView(ui.gap(12))
            buttons.addView(ui.button("رفض", Palette.kerb, heightDp = 64) {
                BridgeService.answer(r.id, false)
                render()
            }, ui.weight(1f))
            ask.addView(buttons, ui.matchWidth())
            right.addView(ask, ui.matchWidth())
            right.addView(ui.gap(12))
        }

        val pairCard = card()
        pairCard.addView(ui.text("اقتران جوال جديد", 22f, bold = true))
        pairCard.addView(ui.text("في «رفيق الهافال» على الجوال اضغط «ابحث عن السيارة»، ثم اضغط «سماح» هنا. يتم ذلك مرة واحدة فقط.", 15f, Palette.grey))
        pairCard.addView(ui.gap(10))
        pairCard.addView(ui.text("أو أدخل هذا الرمز في الجوال:", 14f, Palette.grey))
        pairCard.addView(ui.text(store.currentCode().chunked(3).joinToString(" "), 40f, Palette.amber).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textDirection = View.TEXT_DIRECTION_LTR
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }, ui.matchWidth())
        pairCard.addView(ui.text("صالح حتى ${time.format(Date(store.codeExpiresAt()))} — يُستخدم مرة واحدة", 14f, Palette.grey).apply {
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }, ui.matchWidth())
        pairCard.addView(ui.gap(10))
        val ips = addresses()
        val where = if (ips.isEmpty()) "غير متصل بشبكة Wi-Fi — صِل الشاشة بنقطة اتصال الجوال"
        else "عنوان السيارة: " + ips.joinToString("  ·  ") { "$it:${BridgeService.PORT}" }
        pairCard.addView(ui.text(where, 15f, if (ips.isEmpty()) Palette.amber else Palette.white).apply {
            textDirection = View.TEXT_DIRECTION_ANY_RTL
        })
        pairCard.addView(ui.gap(12))
        pairCard.addView(ui.button("رمز جديد", Palette.kerb) {
            store.newCode()
            render()
        }, ui.wrap())
        right.addView(pairCard, ui.matchWidth())

        if (!Settings.canDrawOverlays(this)) {
            right.addView(ui.gap(12))
            val warn = card()
            warn.addView(ui.text("البطاقات العائمة غير مفعّلة", 18f, Palette.amber, bold = true))
            warn.addView(ui.text("بدونها تصل العناصر كإشعارات فقط.", 15f, Palette.grey))
            warn.addView(ui.gap(10))
            warn.addView(ui.button("السماح بالظهور فوق التطبيقات", Palette.blue) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } catch (e: Exception) {
                    Actions.toast(this, "امنحها عبر ADB: appops set $packageName SYSTEM_ALERT_WINDOW allow")
                }
            }, ui.wrap())
            right.addView(warn, ui.matchWidth())
        }

        right.addView(ui.gap(12))
        val settings = card()
        settings.addView(ui.text("الإعدادات", 20f, bold = true))
        settings.addView(ui.gap(8))
        val auto = if (store.autoOpenGeo) "مفعّل" else "معطّل"
        settings.addView(ui.button("فتح المواقع تلقائيًا: $auto", Palette.kerb) {
            store.autoOpenGeo = !store.autoOpenGeo
            render()
        }, ui.matchWidth())
        settings.addView(ui.gap(8))
        settings.addView(ui.button("تطبيق الملاحة: ${navLabel()}", Palette.kerb) { pickNav() }, ui.matchWidth())
        settings.addView(ui.gap(8))
        settings.addView(ui.button("اسم السيارة: ${store.carName}", Palette.kerb) { rename() }, ui.matchWidth())
        right.addView(settings, ui.matchWidth())
    }

    // ---- العمود الأيسر: الوارد والأجهزة ----

    private fun renderLeft() {
        left.removeAllViews()
        val head = ui.row()
        head.addView(ui.text("الوارد", 26f, bold = true), ui.weight(1f))
        val items = store.inbox()
        if (items.isNotEmpty()) {
            head.addView(ui.button("مسح", Palette.kerb, heightDp = 48) {
                store.clearInbox()
                render()
            })
        }
        left.addView(head, ui.matchWidth())
        left.addView(ui.gap(10))

        if (items.isEmpty()) {
            left.addView(ui.text("لا شيء بعد. شارك موقعًا أو رابطًا أو صورة من الجوال واختر «أرسل إلى الهافال».", 16f, Palette.grey))
        }
        items.forEach { item ->
            val row = ui.row().apply {
                background = ui.rounded(Palette.shoulder, 14)
                setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12))
                alpha = if (item.opened) 0.6f else 1f
            }
            val col = ui.column()
            col.addView(ui.text("${Actions.icon(item)}  ${item.title}", 18f, bold = true).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            if (item.detail != item.title) {
                col.addView(ui.text(item.detail, 14f, Palette.grey).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
            }
            col.addView(ui.text("من ${item.from} · ${time.format(Date(item.at))}", 13f, Palette.grey))
            row.addView(col, ui.weight(1f))
            row.addView(ui.gap(12))
            val label = if (item.type == "geo") "ملاحة" else Actions.openLabel(item)
            row.addView(ui.button(label, Palette.green, heightDp = 56) {
                Actions.open(this, item, store)
                render()
            })
            left.addView(row, ui.matchWidth())
            left.addView(ui.gap(8))
        }

        left.addView(ui.gap(16))
        left.addView(ui.text("الأجهزة الموثوقة", 22f, bold = true))
        left.addView(ui.gap(8))
        val devices = store.devices()
        if (devices.isEmpty()) left.addView(ui.text("لم يُقترن أي جوال بعد.", 16f, Palette.grey))
        devices.forEach { d ->
            val row = ui.row().apply {
                background = ui.rounded(Palette.shoulder, 14)
                setPadding(ui.dp(16), ui.dp(10), ui.dp(16), ui.dp(10))
            }
            row.addView(ui.text("📱  ${d.name}", 17f), ui.weight(1f))
            row.addView(ui.button("إزالة", Palette.kerb, heightDp = 48) {
                AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setMessage("إزالة ${d.name}؟ لن يستطيع الإرسال حتى يقترن من جديد.")
                    .setPositiveButton("إزالة") { _, _ ->
                        store.removeDevice(d.hash)
                        render()
                    }
                    .setNegativeButton("إلغاء", null)
                    .show()
            })
            left.addView(row, ui.matchWidth())
            left.addView(ui.gap(8))
        }
    }

    private fun card() = ui.column(22).apply { background = ui.rounded(Palette.shoulder, 14) }

    private fun navLabel(): String {
        val pkg = store.navPackage
        if (pkg.isEmpty()) return "يختار النظام"
        return Actions.navApps(this).firstOrNull { it.first == pkg }?.second ?: pkg
    }

    private fun pickNav() {
        val apps = Actions.navApps(this)
        val labels = (listOf("يختار النظام") + apps.map { it.second }).toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("تطبيق الملاحة")
            .setItems(labels) { _, which ->
                store.navPackage = if (which == 0) "" else apps[which - 1].first
                render()
            }
            .show()
    }

    private fun rename() {
        val field = EditText(this).apply {
            setText(store.carName)
            setSingleLine()
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("اسم السيارة")
            .setView(field)
            .setPositiveButton("حفظ") { _, _ ->
                val name = field.text.toString().trim()
                if (name.isNotEmpty()) {
                    store.carName = name.take(40)
                    // إعادة تشغيل الخدمة لتعلن الاسم الجديد عبر mDNS
                    stopService(Intent(this, BridgeService::class.java))
                    BridgeService.start(this)
                    handler.postDelayed({ render() }, 500)
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun addresses(): List<String> = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
    } catch (e: Exception) {
        emptyList()
    }

    companion object {
        const val EXTRA_OPEN = "open"
    }
}
