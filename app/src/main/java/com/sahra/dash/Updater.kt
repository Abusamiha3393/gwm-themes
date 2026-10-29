package com.sahra.dash

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * يتحقق من آخر إصدار في مستودع GitHub العام، ينزّل ملف APK، ويثبّته فوق النسخة الحالية.
 * التثبيت يطلب موافقة السائق مرة واحدة على «تثبيت التطبيقات غير المعروفة» ثم تأكيدًا لكل تحديث.
 */
object Updater {
    const val REPO = "Abusamiha3393/gwm-themes"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    private const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L

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
    }.getOrNull() ?: "—"

    /** يعمل في خيط خلفي فقط. */
    fun check(context: Context): Result {
        return try {
            val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "SahraDash")
            }
            val code = conn.responseCode
            if (code == 404) return Result.Failed("لا توجد إصدارات منشورة، أو المستودع ما زال خاصًا")
            if (code != 200) return Result.Failed("رد غير متوقع من GitHub ($code)")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val number = Regex("""(\d+)$""").find(json.optString("tag_name"))?.groupValues?.get(1)?.toIntOrNull()
                ?: return Result.Failed("لم أتعرف على رقم الإصدار")
            val assets = json.optJSONArray("assets")
            var url = ""; var size = 0L
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) { url = a.optString("browser_download_url"); size = a.optLong("size"); break }
            }
            if (url.isEmpty()) return Result.Failed("الإصدار لا يحتوي ملف APK")
            Prefs(context).lastUpdateCheck = System.currentTimeMillis()
            if (number > currentNumber(context)) Result.Available(Release(number, json.optString("name"), url, size))
            else Result.UpToDate
        } catch (e: Exception) {
            Result.Failed("تعذر الاتصال بالإنترنت")
        }
    }

    fun due(context: Context) = System.currentTimeMillis() - Prefs(context).lastUpdateCheck > CHECK_EVERY_MS

    /** ينزّل الملف ويعيد مساره. progress بين 0 و100. يعمل في خيط خلفي فقط. */
    fun download(context: Context, r: Release, progress: (Int) -> Unit): File {
        val out = File(context.cacheDir, "update.apk")
        var url = URL(r.apkUrl)
        var conn: HttpURLConnection
        var hops = 0
        // روابط GitHub تحوّل إلى خادم آخر
        while (true) {
            conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000; readTimeout = 30_000
                setRequestProperty("User-Agent", "SahraDash")
            }
            val code = conn.responseCode
            if (code in 300..399 && hops < 5) { url = URL(url, conn.getHeaderField("Location")); hops++; continue }
            if (code != 200) throw IllegalStateException("HTTP $code")
            break
        }
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: r.size
        conn.inputStream.use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var done = 0L; var last = -1
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    output.write(buf, 0, n); done += n
                    val p = if (total > 0) (done * 100 / total).toInt() else 0
                    if (p != last) { last = p; progress(p) }
                }
            }
        }
        return out
    }

    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    /** يسلّم الملف لمثبّت النظام، الذي يعرض للسائق نافذة تأكيد. */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(context, id, Intent(context, InstallResultReceiver::class.java), flags)
            session.commit(pi.intentSender)
        }
    }
}

/** يستقبل نتيجة التثبيت: يعرض نافذة التأكيد، أو يخبر السائق بالخطأ. */
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
                val text = if (status == PackageInstaller.STATUS_FAILURE_CONFLICT || status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE)
                    "فشل التحديث: التوقيع مختلف عن النسخة المثبتة. احذف التطبيق وثبّت النسخة الجديدة يدويًا."
                else "فشل التحديث: $msg"
                Handler(Looper.getMainLooper()).post { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
            }
        }
    }
}
