package com.sahra.split

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout

/** قائمة كل التطبيقات: لمسة تفتح التطبيق، والضغط المطوّل يعرض خيارات التقسيم والنافذة العائمة. */
class AppsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        val apps = AppLauncher.apps(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.bg)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(label("التطبيقات", 24f, Palette.fg, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(pill("رجوع") { finish() })
        root.addView(head)

        val grid = GridView(this).apply {
            numColumns = GridView.AUTO_FIT
            columnWidth = dp(120)
            verticalSpacing = dp(12)
            horizontalSpacing = dp(12)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            adapter = object : BaseAdapter() {
                override fun getCount() = apps.size
                override fun getItem(i: Int) = apps[i]
                override fun getItemId(i: Int) = i.toLong()
                override fun getView(i: Int, convert: View?, parent: ViewGroup?): View {
                    val app = apps[i]
                    return LinearLayout(this@AppsActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        setPadding(dp(6), dp(10), dp(6), dp(10))
                        background = rounded(Palette.card, dp(14f))
                        addView(ImageView(context).apply { setImageDrawable(app.icon) }, LinearLayout.LayoutParams(dp(64), dp(64)))
                        addView(label(app.label, 14f).apply {
                            gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                            setPadding(0, dp(6), 0, 0)
                        })
                    }
                }
            }
            setOnItemClickListener { _, _, i, _ -> AppLauncher.launch(this@AppsActivity, apps[i].packageName, null, AppLauncher.MODE_FULLSCREEN) }
            setOnItemLongClickListener { _, _, i, _ -> options(apps[i]); true }
        }
        root.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(12) })
        setContentView(root)
    }

    private fun options(app: LaunchableApp) {
        val prefs = Prefs(this)
        val items = arrayOf("اجعله تطبيق الملاحة", "اجعله تطبيق الموسيقى", "افتحه في نافذة عائمة", "معلومات التطبيق")
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(app.label)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> prefs.updatePreset { it.copy(navApp = app.packageName) }
                    1 -> prefs.updatePreset { it.copy(musicApp = app.packageName) }
                    2 -> {
                        val s = AppLauncher.screenSize(this)
                        val w = s.x * 45 / 100; val h = s.y * 70 / 100
                        val r = Rect((s.x - w) / 2, (s.y - h) / 2, (s.x + w) / 2, (s.y + h) / 2)
                        AppLauncher.launch(this, app.packageName, r, AppLauncher.MODE_FREEFORM)
                    }
                    3 -> runCatching {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
                    }
                }
            }
            .show()
    }
}
