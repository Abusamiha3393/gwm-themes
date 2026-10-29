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
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout

/** كل التطبيقات على أرضية الإسفلت: لمسة تفتح التطبيق، والضغط المطوّل يعرض خيارات التقسيم والنافذة العائمة. */
class AppsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Type.init(this)
        window.statusBarColor = Palette.gantry
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        val apps = AppLauncher.apps(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = AsphaltDrawable(this@AppsActivity)
            setPadding(dp(20), dp(16), dp(20), dp(8))
        }
        root.addView(header(R.drawable.ic_apps, "التطبيقات", Palette.blue) { finish() })

        val grid = GridView(this).apply {
            numColumns = GridView.AUTO_FIT
            columnWidth = dp(132)
            verticalSpacing = dp(14)
            horizontalSpacing = dp(14)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            selector = rounded(0x22FFFFFF, dp(14f))
            clipToPadding = false
            setPadding(0, dp(16), 0, dp(16))
            adapter = object : BaseAdapter() {
                override fun getCount() = apps.size
                override fun getItem(i: Int) = apps[i]
                override fun getItemId(i: Int) = i.toLong()
                override fun getView(i: Int, convert: View?, parent: ViewGroup?): View {
                    val app = apps[i]
                    return LinearLayout(this@AppsActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        setPadding(dp(8), dp(14), dp(8), dp(12))
                        background = rounded(Palette.shoulder, dp(14f), dp(1), Palette.kerb)
                        val plate = FrameLayout(context).apply { background = rounded(Palette.white, dp(18f)) }
                        plate.addView(ImageView(context).apply { setImageDrawable(app.icon) }, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))
                        addView(plate, LinearLayout.LayoutParams(dp(78), dp(78)))
                        addView(label(app.label, 14f, Palette.white, true).apply {
                            gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                            setPadding(0, dp(10), 0, 0)
                        })
                    }
                }
            }
            setOnItemClickListener { _, _, i, _ -> AppLauncher.launch(this@AppsActivity, apps[i].packageName, null, AppLauncher.MODE_FULLSCREEN) }
            setOnItemLongClickListener { _, _, i, _ -> options(apps[i]); true }
        }
        root.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
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
