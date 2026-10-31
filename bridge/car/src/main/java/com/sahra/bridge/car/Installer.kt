package com.sahra.bridge.car

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.net.Uri
import java.io.File
import kotlin.concurrent.thread

/** تثبيت APK وصل من الجوال، بعد تأكيد المستخدم في نافذة النظام (نفس طريقة محدّث صحراء داش). */
object Installer {
    fun install(context: Context, apk: File) {
        val app = context.applicationContext
        if (!app.packageManager.canRequestPackageInstalls()) {
            Actions.toast(app, "اسمح لجسر الهافال بتثبيت التطبيقات أولًا")
            try {
                app.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (_: Exception) {
                Actions.toast(app, "أو عبر ADB: appops set ${app.packageName} REQUEST_INSTALL_PACKAGES allow")
            }
            return
        }
        Actions.toast(app, "جارٍ تجهيز ${apk.name}…")
        thread(name = "bridge-install") {
            try {
                val installer = app.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    session.openWrite("base.apk", 0, apk.length()).use { out ->
                        apk.inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }
                    var flags = PendingIntent.FLAG_UPDATE_CURRENT
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
                    val pi = PendingIntent.getBroadcast(app, id, Intent(app, InstallResultReceiver::class.java), flags)
                    session.commit(pi.intentSender)
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post { Actions.toast(app, "تعذّر التثبيت: ${e.message}") }
            }
        }
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
            }
            PackageInstaller.STATUS_SUCCESS -> Actions.toast(context, "تم التثبيت ✓")
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "رمز $status"
                val text = if (status == PackageInstaller.STATUS_FAILURE_CONFLICT) {
                    "فشل التثبيت: التوقيع مختلف عن النسخة المثبتة"
                } else {
                    "فشل التثبيت: $msg"
                }
                Actions.toast(context, text)
            }
        }
    }
}
