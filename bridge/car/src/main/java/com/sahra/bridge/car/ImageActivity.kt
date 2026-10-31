package com.sahra.bridge.car

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import java.io.File
import kotlin.concurrent.thread

/** عارض صور بملء الشاشة، مع التنقل بين الصور المستلمة. لا يعتمد على معرض الشاشة. */
class ImageActivity : Activity() {
    private lateinit var image: ImageView
    private var files: List<File> = emptyList()
    private var index = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ui = Ui(this)
        val store = Store(this)
        files = store.inbox()
            .filter { it.type == "file" && it.mime.startsWith("image/") }
            .mapNotNull { BridgeFiles.fileOf(this, android.net.Uri.parse(it.uri)) }
            .distinct()
        val wanted = intent.getStringExtra(EXTRA_NAME)
        index = files.indexOfFirst { it.name == wanted }.coerceAtLeast(0)

        image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
            setOnClickListener { finish() }
        }
        val root = FrameLayout(this)
        root.addView(image, FrameLayout.LayoutParams(-1, -1))
        val bar = ui.row().apply { layoutDirection = View.LAYOUT_DIRECTION_RTL }
        bar.addView(ui.button("السابقة", Palette.kerb, heightDp = 64) { step(-1) })
        bar.addView(ui.gap(12))
        bar.addView(ui.button("إغلاق", Palette.green, heightDp = 64) { finish() })
        bar.addView(ui.gap(12))
        bar.addView(ui.button("التالية", Palette.kerb, heightDp = 64) { step(1) })
        root.addView(bar, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = ui.dp(20)
        })
        setContentView(root)
        show()
    }

    private fun step(d: Int) {
        if (files.isEmpty()) return
        index = (index + d + files.size) % files.size
        show()
    }

    private fun show() {
        val f = files.getOrNull(index) ?: return finish()
        val w = resources.displayMetrics.widthPixels
        val h = resources.displayMetrics.heightPixels
        thread(name = "bridge-image") {
            val bmp = decode(f, w, h)
            runOnUiThread { if (!isDestroyed) image.setImageBitmap(bmp) }
        }
    }

    /** تصغير الصورة عند فك ترميزها حتى لا تستهلك ذاكرة الشاشة. */
    private fun decode(f: File, w: Int, h: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    companion object {
        const val EXTRA_NAME = "name"
    }
}
