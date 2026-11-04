package com.sahra.home

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * يبحث في إصدارات GitHub عن أحدث إصدار يبدأ وسمه بـ home- ، وينزّل ملف APK ويثبّته فوق النسخة الحالية.
 * (إصدارات منزلي تُنشر بـ make_latest=false، لذلك لا نستخدم releases/latest.)
 */
object Updater {
    private const val API = "https://api.github.com/repos/Abusamiha3393/gwm-themes/releases?per_page=40"
    private const val TAG_PREFIX = "home-"

    data class Release(val number: Int, val name: String, val apkUrl: String, val size: Long)

    sealed class Result {
        data class Available(val release: Release) : Result()
        object UpToDate : Result()
        data class Failed(val message: String) : Result()
    }

    @Suppress("DEPRECATION")
    fun currentNumber(context: Context): Int = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else info.versionCode
    }.getOrDefault(0)

    fun currentName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: ""

    /** يعمل في خيط خلفي فقط. */
    fun check(context: Context): Result {
        return try {
            val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "HavalHome")
            }
            val code = conn.responseCode
            if (code != 200) return Result.Failed("رد غير متوقع من GitHub ($code)")
            val list = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            var best: Release? = null
            for (i in 0 until list.length()) {
                val rel = list.getJSONObject(i)
                val tag = rel.optString("tag_name")
                if (!tag.startsWith(TAG_PREFIX) || rel.optBoolean("draft")) continue
                val number = tag.removePrefix(TAG_PREFIX).toIntOrNull() ?: continue
                val assets = rel.optJSONArray("assets") ?: continue
                var url = ""
                var size = 0L
                for (j in 0 until assets.length()) {
                    val a = assets.getJSONObject(j)
                    if (a.optString("name").endsWith(".apk")) {
                        url = a.optString("browser_download_url")
                        size = a.optLong("size")
                        break
                    }
                }
                if (url.isEmpty()) continue
                val current = best
                if (current == null || number > current.number) {
                    best = Release(number, rel.optString("name"), url, size)
                }
            }
            val found = best ?: return Result.Failed("لا توجد إصدارات منشورة")
            if (found.number > currentNumber(context)) Result.Available(found) else Result.UpToDate
        } catch (e: Exception) {
            Result.Failed("تعذر الاتصال بالإنترنت")
        }
    }

    /** ينزّل الملف ويعيد مساره. progress بين 0 و100. يعمل في خيط خلفي فقط. */
    fun download(context: Context, r: Release, progress: (Int) -> Unit): File {
        val out = File(context.cacheDir, "update.apk")
        var url = URL(r.apkUrl)
        var conn: HttpURLConnection
        var hops = 0
        while (true) {
            conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "HavalHome")
            }
            val code = conn.responseCode
            if (code in 300..399 && hops < 5) {
                url = URL(url, conn.getHeaderField("Location"))
                hops++
                continue
            }
            if (code != 200) throw IllegalStateException("HTTP $code")
            break
        }
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: r.size
        conn.inputStream.use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var last = -1
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    done += n
                    val p = if (total > 0) (done * 100 / total).toInt() else 0
                    if (p != last) {
                        last = p
                        progress(p)
                    }
                }
            }
        }
        return out
    }

    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    /** يسلّم الملف لمثبّت النظام، الذي يعرض نافذة تأكيد. */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(context, id, Intent(context, InstallResultReceiver::class.java), flags)
            session.commit(pi.intentSender)
        }
    }
}

/** يستقبل نتيجة التثبيت: يعرض نافذة التأكيد، أو يخبر بالخطأ. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "رمز $status"
                val text = if (status == PackageInstaller.STATUS_FAILURE_CONFLICT ||
                    status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE
                ) "فشل التحديث: التوقيع مختلف. احذف التطبيق وثبّت النسخة الجديدة يدويًا."
                else "فشل التحديث: $msg"
                Handler(Looper.getMainLooper()).post { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
            }
        }
    }
}
