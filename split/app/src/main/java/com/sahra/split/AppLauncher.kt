package com.sahra.split

import android.app.ActivityOptions
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

data class LaunchableApp(val packageName: String, val label: String, val icon: Drawable?)

/**
 * يفتح التطبيقات في أجزاء محددة من الشاشة.
 * أنظمة الشاشات تختلف: بعضها يحترم الحدود ووضع النوافذ، وبعضها يتجاهلها ويفتح التطبيق كاملًا،
 * لذلك يمكن اختيار وضع النوافذ من الإعدادات.
 */
object AppLauncher {
    private const val TAG = "AppLauncher"

    // قيم WindowConfiguration المخفية في أندرويد
    const val MODE_FULLSCREEN = 1
    const val MODE_SPLIT_PRIMARY = 3
    const val MODE_SPLIT_SECONDARY = 4
    const val MODE_FREEFORM = 5

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

    fun label(context: Context, pkg: String): String = if (pkg.isEmpty()) "—" else runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    fun icon(context: Context, pkg: String): Drawable? = if (pkg.isEmpty()) null else
        runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull()

    @Suppress("DEPRECATION")
    fun screenSize(context: Context): Point {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds
            Point(b.width(), b.height())
        } else {
            Point().also { wm.defaultDisplay.getRealSize(it) }
        }
    }

    /** يفتح تطبيقًا داخل مستطيل محدد (أو كاملًا إن كان bounds = null). */
    fun launch(context: Context, pkg: String, bounds: Rect?, mode: Int): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (bounds == null) {
            return runCatching { context.startActivity(intent) }.isSuccess
        }
        if (mode == MODE_SPLIT_PRIMARY || mode == MODE_SPLIT_SECONDARY) {
            intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        }
        val options = ActivityOptions.makeBasic()
        options.setLaunchBounds(Rect(bounds))
        setWindowingMode(options, mode)
        return try {
            context.startActivity(intent, options.toBundle())
            true
        } catch (e: Exception) {
            Log.w(TAG, "launch with options failed, plain launch: ${e.message}")
            runCatching { context.startActivity(intent) }.isSuccess
        }
    }

    /** المستطيلات المحسوبة لإعداد مسبق: اللوحة، تطبيق الملاحة، تطبيق الموسيقى. */
    data class Layout(val panel: Rect?, val nav: Rect?, val music: Rect?)

    fun layoutFor(p: Preset, width: Int, height: Int, gap: Int): Layout {
        val panelW = if (p.side == PanelSide.NONE) 0 else (width * p.panelWidth).toInt()
        val panel = when (p.side) {
            PanelSide.LEFT -> Rect(0, 0, panelW, height)
            PanelSide.RIGHT -> Rect(width - panelW, 0, width, height)
            PanelSide.NONE -> null
        }
        val x0 = if (p.side == PanelSide.LEFT) panelW + gap else 0
        val x1 = if (p.side == PanelSide.RIGHT) width - panelW - gap else width
        val hasNav = p.navApp.isNotEmpty()
        val hasMusic = p.musicApp.isNotEmpty()
        return when {
            hasNav && hasMusic -> {
                val mid = x0 + ((x1 - x0) * p.ratio).toInt()
                Layout(panel, Rect(x0, 0, mid - gap / 2, height), Rect(mid + gap / 2, 0, x1, height))
            }
            hasNav -> Layout(panel, Rect(x0, 0, x1, height), null)
            hasMusic -> Layout(panel, null, Rect(x0, 0, x1, height))
            else -> Layout(panel, null, null)
        }
    }

    /** يطبّق الإعداد المسبق: يفتح تطبيق الموسيقى ثم الملاحة (لتبقى الملاحة في الأمام). */
    fun applyPreset(context: Context, p: Preset, mode: Int) {
        val size = screenSize(context)
        val l = layoutFor(p, size.x, size.y, context.dp(4))
        val navMode = if (mode == MODE_SPLIT_PRIMARY) MODE_SPLIT_PRIMARY else mode
        val musicMode = if (mode == MODE_SPLIT_PRIMARY) MODE_SPLIT_SECONDARY else mode
        var delay = 0L
        l.music?.let { r -> main.postDelayed({ launch(context, p.musicApp, r, musicMode) }, delay); delay += 700 }
        l.nav?.let { r -> main.postDelayed({ launch(context, p.navApp, r, navMode) }, delay) }
    }

    fun goHome(context: Context) {
        val home = Intent(context, HomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
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

/** نافذة اختيار تطبيق من القائمة، مع خيار «بدون». */
object AppPicker {
    fun show(context: Context, title: String, allowNone: Boolean, onPick: (String) -> Unit) {
        val apps = AppLauncher.apps(context)
        val items = (if (allowNone) listOf(LaunchableApp("", "— بدون —", null)) else emptyList()) + apps
        val adapter = object : BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(i: Int) = items[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, convert: View?, parent: ViewGroup?): View {
                val app = items[i]
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(context.dp(16), context.dp(10), context.dp(16), context.dp(10))
                }
                val icon = ImageView(context).apply { setImageDrawable(app.icon) }
                row.addView(icon, LinearLayout.LayoutParams(context.dp(36), context.dp(36)))
                row.addView(TextView(context).apply {
                    text = app.label; textSize = 17f; setTextColor(Palette.fg)
                    setPadding(context.dp(14), 0, context.dp(14), 0)
                })
                return row
            }
        }
        AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title)
            .setAdapter(adapter) { _, i -> onPick(items[i].packageName) }
            .setNegativeButton("إلغاء", null)
            .show()
    }
}
