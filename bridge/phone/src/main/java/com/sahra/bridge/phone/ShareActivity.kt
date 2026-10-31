package com.sahra.bridge.phone

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlin.concurrent.thread

/**
 * يظهر في قائمة المشاركة باسم «أرسل إلى الهافال». لا واجهة له:
 * يغلق فورًا ليعود المستخدم إلى التطبيق الذي شارك منه، ويكمل الإرسال في الخلفية.
 */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val subject = intent?.getStringExtra(Intent.EXTRA_SUBJECT)
        val app = applicationContext
        if (text.isEmpty()) {
            toast(app, "لا يوجد نص أو رابط لإرساله")
        } else if (!PhoneStore(app).paired) {
            toast(app, "اقترن بالسيارة أولًا")
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            toast(app, "جارٍ الإرسال إلى الهافال…")
            send(app, text, subject)
        }
        finish()
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
                    when (Sender.deliver(app, item)) {
                        Sender.Result.SENT -> "✓ وصل $what إلى ${PhoneStore(app).carName}"
                        Sender.Result.QUEUED -> "السيارة غير متاحة الآن — سيُرسل $what تلقائيًا عند الاتصال"
                        Sender.Result.NOT_PAIRED -> "اقترن بالسيارة أولًا"
                        Sender.Result.REVOKED -> "أُلغي اقتران هذا الجوال من الشاشة — اقترن من جديد"
                        Sender.Result.REJECTED -> "رفضت الشاشة هذا العنصر"
                    }
                }
                Handler(Looper.getMainLooper()).post {
                    toast(app, msg)
                    done?.invoke()
                }
            }
        }

        private fun toast(c: Context, msg: String) = Toast.makeText(c, msg, Toast.LENGTH_LONG).show()
    }
}
