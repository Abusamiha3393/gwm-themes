package com.sahra.bridge.phone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/**
 * نسخة محلية من كل ملف يُشارك: صلاحية قراءة الرابط القادم من التطبيق الآخر قد تنتهي،
 * والملف قد يبقى في الطابور حتى تظهر السيارة. نحسب SHA-256 أثناء النسخ.
 */
object Outbox {
    fun dir(context: Context) = File(context.filesDir, "outbox").apply { mkdirs() }

    /** يعيد عنصر {type:file, payload:{name, mime, size, sha256, path}} أو null. */
    fun copyIn(context: Context, uri: Uri): JSONObject? {
        val cr = context.contentResolver
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        try {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) name = c.getString(0)
            }
        } catch (_: Exception) {
        }
        name = name.replace(Regex("""[/\\\u0000-\u001f]"""), "_").take(120).ifEmpty { "file" }
        val mime = cr.getType(uri) ?: guessMime(name)
        if (!name.contains('.')) extFor(mime)?.let { name = "$name.$it" }
        val target = File(dir(context), UUID.randomUUID().toString().take(8) + "_" + name)
        val digest = MessageDigest.getInstance("SHA-256")
        return try {
            val input = cr.openInputStream(uri) ?: return null
            input.use { inp ->
                target.outputStream().use { out ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = inp.read(buf)
                        if (n < 0) break
                        digest.update(buf, 0, n)
                        out.write(buf, 0, n)
                    }
                }
            }
            if (target.length() == 0L) {
                target.delete()
                return null
            }
            val sha = digest.digest().joinToString("") { String.format(Locale.US, "%02x", it) }
            JSONObject().put("type", "file").put(
                "payload",
                JSONObject().put("name", name).put("mime", mime).put("size", target.length())
                    .put("sha256", sha).put("path", target.path),
            )
        } catch (e: Exception) {
            target.delete()
            null
        }
    }

    /** يحذف نسخًا لم تعد في الطابور (أُرسلت أو أُسقطت). */
    fun cleanup(context: Context, keep: Set<String>) {
        val cutoff = System.currentTimeMillis() - 10 * 60 * 1000
        dir(context).listFiles()?.filter { it.path !in keep && it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
        if (ext == "apk") return "application/vnd.android.package-archive"
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun extFor(mime: String): String? =
        if (mime == "application/vnd.android.package-archive") "apk"
        else android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)

    fun icon(mime: String) = when {
        mime.startsWith("image/") -> "🖼"
        mime.startsWith("video/") -> "🎬"
        mime.startsWith("audio/") -> "🎵"
        mime == "application/vnd.android.package-archive" -> "📦"
        else -> "📄"
    }

    fun humanSize(b: Long): String = when {
        b >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", b / (1L shl 30).toDouble())
        b >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", b / (1L shl 20).toDouble())
        b >= 1L shl 10 -> String.format(Locale.US, "%d KB", b shr 10)
        else -> "$b B"
    }
}

/** إشعار تقدّم الإرسال للملفات الكبيرة. */
class Progress(private val context: Context) {
    private val nm = context.getSystemService(NotificationManager::class.java)
    private var last = -1

    init {
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "إرسال الملفات", NotificationManager.IMPORTANCE_LOW))
        }
    }

    fun update(name: String, done: Long, total: Long) {
        val pct = if (total > 0) (done * 100 / total).toInt() else 0
        if (pct == last) return
        last = pct
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("إرسال إلى الهافال")
            .setContentText("$name — $pct%")
            .setProgress(100, pct, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        try {
            nm.notify(ID, n)
        } catch (_: SecurityException) {
        }
    }

    fun clear() = nm.cancel(ID)

    companion object {
        private const val CHANNEL = "upload"
        private const val ID = 21
    }
}
