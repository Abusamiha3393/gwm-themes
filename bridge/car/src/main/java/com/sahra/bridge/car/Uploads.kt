package com.sahra.bridge.car

import android.content.Context
import android.os.StatFs
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/**
 * استقبال الملفات على أجزاء قابلة للاستئناف.
 * معرّف الرفع مشتق من (الجهاز + بصمة الملف + الحجم)، فإذا انقطع الاتصال وأعاد الجوال
 * نفس الطلب لاحقًا يجد ما وصل سابقًا ويكمل منه بدل البدء من الصفر.
 */
class Uploads(private val context: Context) {
    private val dir = File(context.filesDir, "uploads").apply { mkdirs() }

    class Result(val code: Int, val json: JSONObject, val item: Item? = null)

    /** POST /v1/uploads */
    @Synchronized
    fun start(device: Device, body: JSONObject): Result {
        val name = safeName(body.optString("name"))
        val size = body.optLong("size", -1)
        val sha = body.optString("sha256").lowercase(Locale.US)
        val mime = body.optString("mime").ifEmpty { guessMime(name) }.take(100)
        // المتصفح على http لا يملك crypto.subtle، فنقبل الرفع بلا بصمة ونعتمد على الحجم
        if (size <= 0 || (sha.isNotEmpty() && !SHA_RE.matches(sha))) return Result(400, err("bad_meta"))
        if (size > MAX_FILE) return Result(413, err("too_large"))
        if (freeBytes() < size + RESERVE) return Result(507, err("no_space").put("free", freeBytes() - RESERVE))

        val key = if (sha.isNotEmpty()) sha else "name:$name:${body.optLong("modified")}"
        val id = Store.sha256("${device.hash}:$key:$size").take(32)
        val done = File(dir, "$id.done")
        if (done.exists()) {
            return Result(200, JSONObject().put("id", id).put("received", size).put("done", true).put("chunk", CHUNK))
        }
        val meta = File(dir, "$id.json")
        if (!meta.exists()) {
            meta.writeText(
                JSONObject().put("name", name).put("size", size).put("sha256", sha).put("mime", mime)
                    .put("from", device.name).toString(),
            )
        }
        val received = File(dir, "$id.part").length()
        return Result(200, JSONObject().put("id", id).put("received", received).put("done", false).put("chunk", CHUNK))
    }

    /** GET /v1/uploads/{id} */
    @Synchronized
    fun status(id: String): Result {
        if (!ID_RE.matches(id)) return Result(404, err("not_found"))
        if (File(dir, "$id.done").exists()) return Result(200, JSONObject().put("id", id).put("done", true))
        if (!File(dir, "$id.json").exists()) return Result(404, err("not_found"))
        return Result(200, JSONObject().put("id", id).put("received", File(dir, "$id.part").length()).put("done", false))
    }

    /** PUT /v1/uploads/{id} مع ترويسة X-Offset. يكتب الجزء ويُنهي الملف عند اكتماله. */
    @Synchronized
    fun chunk(id: String, offset: Long, data: ByteArray): Result {
        if (!ID_RE.matches(id)) return Result(404, err("not_found"))
        val metaFile = File(dir, "$id.json")
        if (!metaFile.exists()) return Result(404, err("not_found"))
        val meta = JSONObject(metaFile.readText())
        val size = meta.optLong("size")
        val part = File(dir, "$id.part")
        val have = part.length()
        if (offset != have) return Result(409, err("offset").put("received", have))
        if (have + data.size > size) return Result(400, err("overflow"))
        FileOutputStream(part, true).use { it.write(data) }
        val now = part.length()
        if (now < size) return Result(200, JSONObject().put("id", id).put("received", now).put("done", false))
        return finish(id, meta, part)
    }

    private fun finish(id: String, meta: JSONObject, part: File): Result {
        val digest = MessageDigest.getInstance("SHA-256")
        part.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        val expected = meta.optString("sha256")
        if (expected.isNotEmpty() && Store.hex(digest.digest()) != expected) {
            part.delete()
            return Result(422, err("checksum").put("received", 0))
        }
        val target = unique(received(context), meta.optString("name"))
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
        File(dir, "$id.json").delete()
        File(dir, "$id.done").writeText(target.name)
        val mime = meta.optString("mime")
        val item = Item(
            UUID.randomUUID().toString(), "file", target.name,
            "${humanSize(target.length())} · ${kindLabel(mime, target.name)}",
            BridgeFiles.uriFor(context, target).toString(),
            meta.optString("from"), System.currentTimeMillis(), false, mime,
        )
        return Result(200, JSONObject().put("id", id).put("received", target.length()).put("done", true).put("item", item.id), item)
    }

    /** يحذف الأجزاء المتروكة وعلامات الاكتمال الأقدم من أسبوع. */
    fun cleanup() {
        val cutoff = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
        dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    private fun freeBytes(): Long = try {
        StatFs(received(context).path).availableBytes
    } catch (e: Exception) {
        Long.MAX_VALUE
    }

    private fun unique(folder: File, name: String): File {
        var f = File(folder, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var i = 2
        while (f.exists()) f = File(folder, "$base ($i)$ext").also { i++ }
        return f
    }

    private fun err(code: String) = JSONObject().put("error", code)

    companion object {
        const val CHUNK = 4 * 1024 * 1024
        const val MAX_FILE = 8L * 1024 * 1024 * 1024
        const val RESERVE = 500L * 1024 * 1024
        private val SHA_RE = Regex("[0-9a-f]{64}")
        private val ID_RE = Regex("[0-9a-f]{32}")

        /** مجلد الملفات المستلمة. خاص بالتطبيق، ويُفتح لغيره عبر BridgeFiles. */
        fun received(context: Context): File =
            File(context.getExternalFilesDir(null) ?: context.filesDir, "received").apply { mkdirs() }

        fun safeName(raw: String): String {
            val n = raw.substringAfterLast('/').substringAfterLast('\\')
                .replace(Regex("""[\u0000-\u001f<>:"|?*]"""), "_").trim().trimStart('.')
            return n.take(120).ifEmpty { "file" }
        }

        fun guessMime(name: String): String {
            val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
            if (ext == "apk") return APK_MIME
            return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }

        const val APK_MIME = "application/vnd.android.package-archive"

        fun isApk(mime: String, name: String) = mime == APK_MIME || name.lowercase(Locale.US).endsWith(".apk")

        fun kindLabel(mime: String, name: String) = when {
            isApk(mime, name) -> "تطبيق"
            mime.startsWith("image/") -> "صورة"
            mime.startsWith("video/") -> "فيديو"
            mime.startsWith("audio/") -> "صوت"
            mime == "application/pdf" -> "PDF"
            else -> "ملف"
        }

        fun humanSize(b: Long): String = when {
            b >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", b / (1L shl 30).toDouble())
            b >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", b / (1L shl 20).toDouble())
            b >= 1L shl 10 -> String.format(Locale.US, "%d KB", b shr 10)
            else -> "$b B"
        }
    }
}
