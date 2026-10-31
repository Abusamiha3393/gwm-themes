package com.sahra.bridge.car

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

object Actions {

    /** يحوّل طلب الجوال إلى عنصر، أو null إن كان غير صالح. */
    fun parse(json: JSONObject, from: String): Item? {
        val type = json.optString("type")
        val p = json.optJSONObject("payload") ?: return null
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        return when (type) {
            "geo" -> {
                val lat = p.optDouble("lat")
                val lng = p.optDouble("lng")
                val label = p.optString("label").trim().take(200)
                val query = p.optString("query").trim().take(300)
                val hasCoords = !lat.isNaN() && !lng.isNaN() && lat in -90.0..90.0 && lng in -180.0..180.0
                if (!hasCoords && query.isEmpty()) return null
                val uri = if (hasCoords) {
                    val ll = String.format(Locale.US, "%.6f,%.6f", lat, lng)
                    val name = if (label.isNotEmpty()) "($label)" else ""
                    "geo:$ll?q=" + Uri.encode("$ll$name")
                } else {
                    "geo:0,0?q=" + Uri.encode(query)
                }
                val detail = if (hasCoords) String.format(Locale.US, "%.5f, %.5f", lat, lng) else query
                Item(id, "geo", label.ifEmpty { query.ifEmpty { "موقع" } }, detail, uri, from, now, false)
            }
            "link" -> {
                val url = p.optString("url").trim()
                val scheme = Uri.parse(url).scheme?.lowercase()
                if (scheme != "http" && scheme != "https") return null
                if (url.length > 4000) return null
                val title = p.optString("title").trim().take(200)
                val host = Uri.parse(url).host ?: url
                Item(id, "link", title.ifEmpty { host }, url, url, from, now, false)
            }
            "text" -> {
                val text = p.optString("text")
                if (text.isBlank() || text.length > 10_000) return null
                val first = text.trim().lineSequence().first().take(80)
                Item(id, "text", first, text, "", from, now, false)
            }
            else -> null
        }
    }

    fun open(context: Context, item: Item, store: Store) {
        when (item.type) {
            "geo" -> openGeo(context, item, store.navPackage)
            "link" -> view(context, Uri.parse(item.uri), null)
            "file" -> openFile(context, item)
            "text" -> {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("HAVAL Bridge", item.detail))
                toast(context, "تم نسخ النص إلى الحافظة")
            }
        }
        store.markOpened(item.id)
    }

    private fun openFile(context: Context, item: Item) {
        val uri = Uri.parse(item.uri)
        val file = BridgeFiles.fileOf(context, uri)
        if (file == null) {
            toast(context, "لم يعد الملف موجودًا على الشاشة")
            return
        }
        when {
            Uploads.isApk(item.mime, file.name) -> Installer.install(context, file)
            item.mime.startsWith("image/") -> context.startActivity(
                Intent(context, ImageActivity::class.java).putExtra(ImageActivity.EXTRA_NAME, file.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            else -> {
                val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, item.mime.ifEmpty { Uploads.guessMime(file.name) })
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try {
                    context.startActivity(i)
                } catch (e: Exception) {
                    toast(context, "لا يوجد تطبيق على الشاشة يفتح هذا النوع (${file.name})")
                }
            }
        }
    }

    /** نص زر الفتح حسب نوع العنصر. */
    fun openLabel(item: Item): String = when (item.type) {
        "geo" -> "ابدأ الملاحة"
        "text" -> "انسخ"
        "file" -> when {
            Uploads.isApk(item.mime, item.title) -> "تثبيت"
            item.mime.startsWith("image/") -> "عرض"
            item.mime.startsWith("video/") || item.mime.startsWith("audio/") -> "تشغيل"
            else -> "افتح"
        }
        else -> "افتح"
    }

    private fun openGeo(context: Context, item: Item, pkg: String) {
        val geo = Uri.parse(item.uri)
        if (view(context, geo, pkg.ifEmpty { null }, quiet = true)) return
        if (pkg.isNotEmpty() && view(context, geo, null, quiet = true)) return
        // لا يوجد تطبيق يفهم geo: — نجرب رابط خرائط Google العادي
        // geo: رابط غير هرمي، فلا نستخدم getQueryParameter
        val q = Uri.decode(item.uri.substringAfter("?q=", item.detail)).substringBefore('(')
        val web = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(q))
        view(context, web, null)
    }

    private fun view(context: Context, uri: Uri, pkg: String?, quiet: Boolean = false): Boolean {
        val i = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (pkg != null) i.setPackage(pkg)
        return try {
            context.startActivity(i)
            true
        } catch (e: Exception) {
            if (!quiet) toast(context, "لا يوجد تطبيق على الشاشة يفتح هذا العنصر")
            false
        }
    }

    /** تطبيقات الملاحة المثبّتة التي تقبل روابط geo: */
    fun navApps(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("geo:24.7136,46.6753"))
        @Suppress("DEPRECATION")
        val list = pm.queryIntentActivities(i, PackageManager.MATCH_ALL)
        return list.map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .sortedBy { it.second }
    }

    fun icon(type: String) = when (type) {
        "geo" -> "📍"
        "link" -> "🔗"
        "file" -> "📎"
        else -> "📝"
    }

    fun icon(item: Item): String = if (item.type != "file") icon(item.type) else when {
        item.mime.startsWith("image/") -> "🖼"
        item.mime.startsWith("video/") -> "🎬"
        item.mime.startsWith("audio/") -> "🎵"
        Uploads.isApk(item.mime, item.title) -> "📦"
        else -> "📄"
    }

    fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
