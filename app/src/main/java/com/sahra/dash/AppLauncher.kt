package com.sahra.dash

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.Log

data class LaunchableApp(val packageName: String, val label: String, val icon: Drawable?)

/**
 * يفتح التطبيقات في جزء محدد من الشاشة.
 * أنظمة الشاشات تختلف: بعضها يحترم الحدود ووضع النوافذ، وبعضها يتجاهلها ويفتح التطبيق كاملًا.
 * في الحالة الثانية يبقى العرض فوق التطبيق فيغطي جزءًا منه فقط، فتبقى النتيجة مقبولة.
 */
object AppLauncher {
    private const val TAG = "AppLauncher"

    // قيم WindowConfiguration المخفية في أندرويد
    const val MODE_FULLSCREEN = 1
    const val MODE_SPLIT_PRIMARY = 3
    const val MODE_SPLIT_SECONDARY = 4
    const val MODE_FREEFORM = 5
    const val MODE_MULTI_WINDOW = 6

    private val main = Handler(Looper.getMainLooper())

    fun apps(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { LaunchableApp(it.packageName, it.loadLabel(pm).toString(), runCatching { it.loadIcon(pm) }.getOrNull()) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun label(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /** يفتح تطبيقًا داخل مستطيل محدد. يرجع false إذا لم يكن التطبيق موجودًا. */
    fun launch(context: Context, pkg: String, bounds: Rect?, mode: Int): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (mode == MODE_FREEFORM || mode == MODE_MULTI_WINDOW) {
            intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        val options = ActivityOptions.makeBasic()
        if (bounds != null) options.setLaunchBounds(Rect(bounds))
        setWindowingMode(options, mode)
        return try {
            context.startActivity(intent, options.toBundle())
            true
        } catch (e: Exception) {
            Log.w(TAG, "launch with options failed, plain launch: ${e.message}")
            runCatching { context.startActivity(intent) }.isSuccess
        }
    }

    /** تقسيم الشاشة بين تطبيقين بنسبة محددة (0.3 إلى 0.7). */
    fun splitTwo(context: Context, left: String, right: String, ratio: Float, width: Int, height: Int): Boolean {
        val leftW = (width * ratio.coerceIn(0.3f, 0.7f)).toInt()
        val a = Rect(0, 0, leftW - 4, height)
        val b = Rect(leftW + 4, 0, width, height)
        if (!launch(context, left, a, MODE_SPLIT_PRIMARY)) return false
        // التطبيق الثاني بعد أن يستقر الأول
        main.postDelayed({ launch(context, right, b, MODE_SPLIT_SECONDARY) }, 700)
        return true
    }

    fun goHome(context: Context) {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(home) }
    }

    private fun setWindowingMode(options: ActivityOptions, mode: Int) {
        try {
            ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType).invoke(options, mode)
        } catch (e: Throwable) {
            Log.w(TAG, "setLaunchWindowingMode($mode) unavailable: ${e.message}")
        }
    }
}
