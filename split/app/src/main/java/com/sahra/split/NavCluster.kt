package com.sahra.split

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.util.Log
import android.view.Display

/**
 * ينقل تطبيق الملاحة إلى شاشة العدادات ويعيده.
 *
 * لا نعيد ضبط مهمة التطبيق (بلا RESET_TASK) حتى يبقى المسار: النظام يجلب المهمة الموجودة
 * إلى الشاشة المطلوبة إن سمح بذلك. ما دامت الملاحة على العدادات يُخفى الثيم من هناك.
 */
object NavCluster {
    private const val TAG = "NavCluster"

    fun isOn(context: Context) = Prefs(context).navOnCluster

    /** يبدّل بين الإرسال والإرجاع ويعيد رسالة قصيرة للسائق. */
    fun toggle(context: Context): String = if (isOn(context)) bringBack(context) else send(context)

    fun send(context: Context): String {
        val p = Prefs(context)
        val pkg = p.preset.navApp
        if (pkg.isEmpty()) return "اختر تطبيق الملاحة في الإعداد المسبق أولًا"
        val display = ClusterService.findDisplay(context) ?: return "لم يُعثر على شاشة العدادات"
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return "تطبيق الملاحة غير مثبت"
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        p.navOnCluster = true
        if (p.navClusterFull) {
            // بملء الشاشة: أخفِ الثيم أولًا حتى لا يغطي الملاحة
            ClusterService.instance?.hideForNav()
            ClusterService.reload(context)
            ClusterActivity.finishAll()
        } else {
            // داخل الثيم: يبقى الثيم ويفتح ثقبًا شفافًا في مكان النافذة
            ClusterService.instance?.pushNow()
            ClusterService.reload(context)
        }

        val opts = ActivityOptions.makeBasic().setLaunchDisplayId(display.displayId)
        opts.setLaunchBounds(bounds(context, display, p.navClusterFull))
        AppLauncher.setWindowingMode(opts, if (p.navClusterFull) AppLauncher.MODE_FULLSCREEN else AppLauncher.MODE_FREEFORM)
        return try {
            context.startActivity(intent, opts.toBundle())
            "الملاحة على شاشة العدادات"
        } catch (e: Exception) {
            Log.w(TAG, "launch on display ${display.displayId} refused: ${e.message}")
            p.navOnCluster = false
            ClusterService.reload(context)
            "رفض النظام فتح الملاحة على العدادات (${e.javaClass.simpleName})"
        }
    }

    fun bringBack(context: Context): String {
        val p = Prefs(context)
        p.navOnCluster = false
        val pkg = p.preset.navApp
        if (pkg.isNotEmpty()) {
            val size = AppLauncher.screenSize(context)
            val rect = AppLauncher.layoutFor(p.preset, size.x, size.y, context.dp(4)).nav
                ?: Rect(0, 0, size.x, size.y)
            AppLauncher.launch(context, pkg, rect, p.windowMode, Display.DEFAULT_DISPLAY)
        }
        // أعد الثيم إلى العدادات
        if (ClusterService.running) ClusterService.reload(context) else ClusterService.sync(context)
        return "أُعيدت الملاحة إلى الشاشة الرئيسية"
    }

    /** مستطيل الملاحة على شاشة العدادات: كامل، أو نافذة الملاحة داخل إطار الثيم. */
    fun bounds(context: Context, display: Display, full: Boolean): Rect {
        val m = android.util.DisplayMetrics().also { @Suppress("DEPRECATION") display.getRealMetrics(it) }
        if (full) return Rect(0, 0, m.widthPixels, m.heightPixels)
        val p = Prefs(context)
        // إطار الثيم (بعد الهوامش)، ثم نافذة الملاحة كنسبة منه
        val fx = m.widthPixels * p.clusterSide / 100
        val fy = m.heightPixels * p.clusterTop / 100
        val fw = m.widthPixels - 2 * fx
        val fh = m.heightPixels - fy - m.heightPixels * p.clusterBottom / 100
        val left = fx + (fw * p.navX).toInt()
        val top = fy + (fh * p.navY).toInt()
        val right = (left + fw * p.navW).toInt().coerceAtMost(fx + fw)
        val bottom = (top + fh * p.navH).toInt().coerceAtMost(fy + fh)
        return Rect(left, top, right, bottom)
    }

    /** يعيد فتح الملاحة على العدادات بالمقاس الجديد إن كانت هناك (بعد تعديلها في المحرر). */
    fun relayout(context: Context) {
        val p = Prefs(context)
        if (!p.navOnCluster) return
        val display = ClusterService.findDisplay(context) ?: return
        val pkg = p.preset.navApp
        if (pkg.isEmpty()) return
        AppLauncher.launch(context, pkg, bounds(context, display, p.navClusterFull),
            if (p.navClusterFull) AppLauncher.MODE_FULLSCREEN else AppLauncher.MODE_FREEFORM, display.displayId)
    }
}
