package com.sahra.bridge.phone

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var store: PhoneStore
    private lateinit var ui: Ui
    private lateinit var body: LinearLayout
    private var status = ""
    private var connected = false
    private var cars: List<Car>? = null
    private var searching = false
    private var draft = ""
    private val time = SimpleDateFormat("d/M HH:mm", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = PhoneStore(this)
        ui = Ui(this)
        body = ui.column(16).apply { layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Palette.asphalt)
            addView(body)
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        render()
        if (store.paired) refreshStatus()
    }

    private fun alive() = !isFinishing && !isDestroyed

    private fun bg(work: () -> Unit) = thread(name = "bridge-ui") { work() }
    private fun onUi(work: () -> Unit) = runOnUiThread { if (alive()) work() }

    private fun refreshStatus() {
        status = "جارٍ البحث عن ${store.carName}…"
        connected = false
        render()
        bg {
            val car = Sender.locate(this)
            if (car != null) Sender.flush(this, car)
            onUi {
                connected = car != null
                status = if (car != null) "متصلة ✓  ${car.host}" else "غير متاحة الآن — تأكد أن الشاشة والجوال على نفس الشبكة"
                render()
            }
        }
    }

    private fun render() {
        body.removeAllViews()
        body.addView(ui.text("رفيق الهافال", 30f, bold = true))
        body.addView(ui.text("أرسل المواقع والروابط إلى شاشة السيارة", 15f, Palette.grey))
        body.addView(ui.gap(16))
        if (store.paired) renderPaired() else renderPairing()
    }

    // ---- بعد الاقتران ----

    private fun renderPaired() {
        val st = card()
        st.addView(ui.text("🚗  ${store.carName}", 22f, bold = true))
        st.addView(ui.text(status, 15f, if (connected) Palette.amber else Palette.grey))
        val pending = store.pending().size
        if (pending > 0) {
            st.addView(ui.gap(8))
            st.addView(ui.text("في الطابور: $pending — تُرسل تلقائيًا عند ظهور السيارة", 15f, Palette.white))
        }
        st.addView(ui.gap(10))
        st.addView(ui.button("تحديث الاتصال", Palette.kerb, heightDp = 48) { refreshStatus() }, ui.wrap())
        body.addView(st, ui.matchWidth())
        body.addView(ui.gap(12))

        val send = card()
        send.addView(ui.text("إرسال سريع", 20f, bold = true))
        send.addView(ui.gap(8))
        val field = EditText(this).apply {
            hint = "الصق رابطًا أو موقعًا أو نصًا"
            setText(draft)
            setTextColor(Palette.white)
            setHintTextColor(Palette.grey)
            textSize = 17f
            minLines = 2
            background = ui.rounded(Palette.kerb, 12)
            setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_VARIATION_URI
        }
        send.addView(field, ui.matchWidth())
        send.addView(ui.gap(10))
        val row = ui.row()
        row.addView(ui.button("أرسل", Palette.green) {
            val text = field.text.toString().trim()
            draft = ""
            if (text.isEmpty()) toast("اكتب أو الصق شيئًا أولًا") else sendText(text)
        }, ui.weight(1f))
        row.addView(ui.gap(10))
        row.addView(ui.button("أرسل الحافظة", Palette.blue) {
            draft = field.text.toString()
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
            if (clip.isNullOrEmpty()) toast("الحافظة فارغة") else sendText(clip)
        }, ui.weight(1f))
        send.addView(row, ui.matchWidth())
        send.addView(ui.gap(10))
        send.addView(ui.text("أو من أي تطبيق: مشاركة ← «أرسل إلى الهافال»", 14f, Palette.grey))
        body.addView(send, ui.matchWidth())
        body.addView(ui.gap(12))

        val history = store.history()
        if (history.isNotEmpty()) {
            body.addView(ui.text("آخر ما أُرسل", 20f, bold = true))
            body.addView(ui.gap(8))
            history.forEach { h ->
                val p = h.optJSONObject("payload")
                val type = h.optString("type")
                val icon = when (type) {
                    "geo" -> "📍"
                    "link" -> "🔗"
                    else -> "📝"
                }
                val title = when (type) {
                    "geo" -> p?.optString("label").orEmpty().ifEmpty {
                        p?.optString("query").orEmpty().ifEmpty {
                            String.format(Locale.US, "%.5f, %.5f", p?.optDouble("lat") ?: 0.0, p?.optDouble("lng") ?: 0.0)
                        }
                    }
                    "link" -> p?.optString("title").orEmpty().ifEmpty { p?.optString("url").orEmpty() }
                    else -> p?.optString("text").orEmpty()
                }
                val r = ui.column(14).apply { background = ui.rounded(Palette.shoulder, 12) }
                r.addView(ui.text("$icon  $title", 16f).apply {
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                })
                r.addView(ui.text(time.format(Date(h.optLong("at"))), 13f, Palette.grey))
                body.addView(r, ui.matchWidth())
                body.addView(ui.gap(6))
            }
            body.addView(ui.gap(10))
        }

        body.addView(ui.button("إلغاء الاقتران", Palette.kerb, heightDp = 48) {
            AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setMessage("إلغاء الاقتران بـ ${store.carName}؟ أزله أيضًا من «الأجهزة الموثوقة» على الشاشة.")
                .setPositiveButton("إلغاء الاقتران") { _, _ ->
                    store.unpair()
                    cars = null
                    render()
                }
                .setNegativeButton("رجوع", null)
                .show()
        }, ui.wrap())
    }

    private fun sendText(text: String) {
        toast("جارٍ الإرسال…")
        ShareActivity.send(applicationContext, text, null) { if (alive()) refreshStatus() }
    }

    // ---- الاقتران ----

    private fun renderPairing() {
        val c = card()
        c.addView(ui.text("اقتران السيارة", 22f, bold = true))
        c.addView(ui.gap(6))
        c.addView(ui.text("1. افتح «جسر الهافال» على شاشة السيارة.\n2. صِل الشاشة والجوال بنفس الشبكة (نقطة اتصال الجوال مثلًا).\n3. ابحث هنا ثم أدخل الرمز الظاهر على الشاشة.", 15f, Palette.grey))
        c.addView(ui.gap(12))
        c.addView(ui.button(if (searching) "جارٍ البحث…" else "ابحث عن السيارة", Palette.green) {
            if (!searching) search()
        }, ui.matchWidth())
        c.addView(ui.gap(8))
        c.addView(ui.button("أدخل عنوان الشاشة يدويًا", Palette.kerb, heightDp = 52) { manual() }, ui.matchWidth())
        body.addView(c, ui.matchWidth())

        val list = cars ?: return
        body.addView(ui.gap(14))
        if (list.isEmpty()) {
            body.addView(ui.text("لم تُعثر على سيارة. تأكد أن تطبيق الشاشة مفتوح وأن الجهازين على نفس الشبكة، أو أدخل العنوان الظاهر على الشاشة يدويًا.", 15f, Palette.amber))
            return
        }
        body.addView(ui.text("اختر سيارتك", 20f, bold = true))
        body.addView(ui.gap(8))
        list.forEach { car ->
            body.addView(ui.button("🚗  ${car.name}   ·   ${car.host}", Palette.shoulder) { askCode(car) }, ui.matchWidth())
            body.addView(ui.gap(8))
        }
    }

    private fun search() {
        searching = true
        cars = null
        render()
        bg {
            val found = Discovery.find(this, 4000)
            onUi {
                searching = false
                cars = found
                render()
                if (found.size == 1) askCode(found[0])
            }
        }
    }

    private fun manual() {
        val field = EditText(this).apply {
            hint = "مثال: 192.168.43.120"
            inputType = InputType.TYPE_CLASS_PHONE
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("عنوان الشاشة")
            .setMessage("يظهر في تطبيق الشاشة تحت «عنوان السيارة».")
            .setView(padded(field))
            .setPositiveButton("اتصال") { _, _ ->
                val raw = field.text.toString().trim()
                val host = raw.substringBefore(':')
                val port = raw.substringAfter(':', "").toIntOrNull() ?: Client.PORT
                if (host.isEmpty()) return@setPositiveButton
                bg {
                    val car = try {
                        Client.info(host, port, 3000)
                    } catch (e: IOException) {
                        null
                    }
                    onUi {
                        if (car == null) toast("لا يوجد جسر الهافال على $host") else askCode(car)
                    }
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun askCode(car: Car) {
        val field = EditText(this).apply {
            hint = "الرمز من 6 أرقام"
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 26f
            gravity = Gravity.CENTER
            textDirection = View.TEXT_DIRECTION_LTR
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("اقتران ${car.name}")
            .setMessage("أدخل الرمز الظاهر على شاشة السيارة.")
            .setView(padded(field))
            .setPositiveButton("اقتران") { _, _ ->
                val code = field.text.toString().filter { it.isDigit() }
                bg {
                    val result = try {
                        store.savePairing(car, Client.pair(car, code, Sender.phoneName(this)))
                        "تم الاقتران بـ ${car.name} ✓"
                    } catch (e: HttpError) {
                        when (e.code) {
                            403 -> "الرمز غير صحيح أو انتهت صلاحيته"
                            429 -> "محاولات كثيرة — انتظر 10 دقائق"
                            else -> "فشل الاقتران (${e.code})"
                        }
                    } catch (e: IOException) {
                        "تعذّر الاتصال بالشاشة"
                    }
                    onUi {
                        toast(result)
                        cars = null
                        render()
                        if (store.paired) refreshStatus()
                    }
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun padded(v: View) = LinearLayout(this).apply {
        setPadding(ui.dp(24), ui.dp(8), ui.dp(24), 0)
        addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun card() = ui.column(18).apply { background = ui.rounded(Palette.shoulder, 14) }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
