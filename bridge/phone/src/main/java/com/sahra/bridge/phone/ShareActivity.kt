package com.sahra.bridge.phone

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import org.json.JSONObject
import kotlin.concurrent.thread

/**
 * يظهر في قائمة المشاركة باسم «أرسل إلى الهافال». لا واجهة له.
 * النصوص والروابط: يغلق فورًا ويكمل في الخلفية.
 * الصور والملفات: ينسخها أولًا (لأن صلاحية القراءة مرتبطة بهذه الشاشة)، ثم يغلق ويرسل.
 */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = applicationContext
        if (!PhoneStore(app).paired) {
            toast(app, "اقترن بالسيارة أولًا")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        val streams = streams(intent)
        if (streams.isNotEmpty()) {
            toast(app, if (streams.size == 1) "جارٍ تجهيز الملف…" else "جارٍ تجهيز ${streams.size} ملفات…")
            thread(name = "bridge-copy") {
                val items = streams.mapNotNull { Outbox.copyIn(app, it) }
                runOnUiThread { finish() }
                if (items.isEmpty()) post(app, "تعذّرت قراءة الملفات") else sendFiles(app, items)
            }
            return
        }
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val subject = intent?.getStringExtra(Intent.EXTRA_SUBJECT)
        if (text.isEmpty()) {
            toast(app, "لا يوجد ما يُرسل")
        } else {
            toast(app, "جارٍ الإرسال إلى الهافال…")
            send(app, text, subject)
        }
        finish()
    }

    @Suppress("DEPRECATION")
    private fun streams(i: Intent?): List<Uri> {
        if (i == null) return emptyList()
        return when (i.action) {
            Intent.ACTION_SEND -> {
                val u = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                listOfNotNull(u)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val l = if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                l?.toList().orEmpty()
            }
            else -> emptyList()
        }
    }

    companion object {
        fun send(app: Context, text: String, subject: String?, done: (() -> Unit)? = null) {
            thread(name = "bridge-share") {
                val item = try {
                    MapLinks.build(text, subject)
                } catch (e: Exception) {
                    null
                }
                val msg = if (item == null) {
                    "تعذّر قراءة المحتوى"
                } else {
                    val what = when (item.optString("type")) {
                        "geo" -> "الموقع"
                        "link" -> "الرابط"
                        else -> "النص"
                    }
                    message(app, Sender.deliver(app, item), what)
                }
                post(app, msg, done)
            }
        }

        /** يرسل ملفات منسوخة مسبقًا في Outbox، واحدًا تلو الآخر. يُستدعى من خيط خلفي. */
        fun sendFiles(app: Context, items: List<JSONObject>, done: (() -> Unit)? = null) {
            var sent = 0
            var last = Sender.Result.SENT
            for (item in items) {
                last = Sender.deliver(app, item)
                if (last == Sender.Result.SENT) sent++
                // إن لم تكن السيارة متاحة نضع الباقي في الطابور دون إعادة البحث لكل ملف
                if (last == Sender.Result.QUEUED) {
                    items.drop(items.indexOf(item) + 1).forEach { PhoneStore(app).enqueue(it.put("localId", System.nanoTime().toString())) }
                    break
                }
                if (last == Sender.Result.REVOKED || last == Sender.Result.NOT_PAIRED) break
            }
            val what = when {
                items.size > 1 -> "${items.size} ملفات"
                items[0].optJSONObject("payload")?.optString("mime").orEmpty().startsWith("image/") -> "الصورة"
                else -> "الملف"
            }
            val msg = if (sent == items.size) "✓ وصل $what إلى ${PhoneStore(app).carName}"
            else if (sent > 0 && last == Sender.Result.QUEUED) "وصل $sent من ${items.size} — الباقي سيُرسل تلقائيًا عند الاتصال"
            else if (last == Sender.Result.REJECTED) "رفضت الشاشة الملف (قد تكون المساحة غير كافية)"
            else message(app, last, what)
            post(app, msg, done)
        }

        private fun message(app: Context, r: Sender.Result, what: String) = when (r) {
            Sender.Result.SENT -> "✓ وصل $what إلى ${PhoneStore(app).carName}"
            Sender.Result.QUEUED -> "السيارة غير متاحة الآن — سيُرسل $what تلقائيًا عند الاتصال"
            Sender.Result.NOT_PAIRED -> "اقترن بالسيارة أولًا"
            Sender.Result.REVOKED -> "أُلغي اقتران هذا الجوال من الشاشة — اقترن من جديد"
            Sender.Result.REJECTED -> "رفضت الشاشة هذا العنصر"
        }

        private fun post(app: Context, msg: String, done: (() -> Unit)? = null) {
            Handler(Looper.getMainLooper()).post {
                toast(app, msg)
                done?.invoke()
            }
        }

        private fun toast(c: Context, msg: String) = Toast.makeText(c, msg, Toast.LENGTH_LONG).show()
    }
}
