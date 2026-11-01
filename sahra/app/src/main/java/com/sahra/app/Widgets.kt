package com.sahra.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/** أرضية الإسفلت: لون داكن وخطوط طولية خافتة كأثر علامات الطريق. */
class AsphaltDrawable(context: Context) : Drawable() {
    private val step = context.dp(48f)
    private val line = Paint().apply { color = 0x09FFFFFF }
    override fun draw(c: Canvas) {
        c.drawColor(Palette.asphalt)
        var x = bounds.left + step
        while (x < bounds.right) { c.drawRect(x, bounds.top.toFloat(), x + step / 24, bounds.bottom.toFloat(), line); x += step }
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = android.graphics.PixelFormat.OPAQUE
}

/**
 * بانٍ لصفحات الإعدادات والمعلومات: أقسام بلسان أخضر، وصفوف بخلفية داكنة،
 * ومفاتيح وخيارات ومنزلقات بنفس الشكل في كل الشاشات.
 */
class Form(private val ctx: Context, val body: LinearLayout) {

    fun section(title: String) {
        val tab = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = ctx.smallSign(Palette.green)
            setPadding(ctx.dp(18), ctx.dp(10), ctx.dp(18), ctx.dp(10))
        }
        tab.addView(ctx.label(title, 17f, Palette.white, true))
        val row = LinearLayout(ctx).apply { setPadding(0, ctx.dp(24), 0, ctx.dp(10)) }
        row.addView(tab)
        body.addView(row)
    }

    fun note(text: String, color: Int = Palette.muted): TextView {
        val t = ctx.label(text, 14f, color).apply { setPadding(ctx.dp(4), ctx.dp(6), ctx.dp(4), ctx.dp(6)); setLineSpacing(ctx.dp(3f), 1f) }
        body.addView(t)
        return t
    }

    private fun card() = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(Palette.shoulder, ctx.dp(12f), ctx.dp(1), Palette.kerb)
        setPadding(ctx.dp(20), ctx.dp(14), ctx.dp(20), ctx.dp(14))
        minimumHeight = ctx.dp(68)
    }

    fun add(v: View) = body.addView(v, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = ctx.dp(8) })

    /** صف يُضغط: عنوان، وقيمة رمادية، وسهم. */
    fun row(title: String, value: String = "", onClick: () -> Unit): TextView {
        val c = card()
        c.addView(ctx.label(title, 17f), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val v = ctx.label(value, 16f, Palette.muted).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(ctx.dp(12), 0, ctx.dp(10), 0)
        }
        c.addView(v)
        c.addView(ctx.icon(R.drawable.ic_chev, 22, Palette.muted))
        c.setOnClickListener { onClick() }
        c.pressable(ctx.dp(12f))
        add(c)
        return v
    }

    /** قيمة للعرض فقط: اسم يمينًا ورقم يسارًا. */
    fun info(title: String, value: String): TextView {
        val c = card()
        c.addView(ctx.label(title, 16f, Palette.muted), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val v = ctx.label(value, 20f, Palette.white, true, digitsFace = true)
        c.addView(v)
        add(c)
        return v
    }

    fun permission(title: String, desc: String, ok: Boolean, onClick: () -> Unit) {
        val c = card()
        val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(ctx.label(title, 17f, Palette.white, true))
        texts.addView(ctx.label(desc, 14f, Palette.muted).apply { setPadding(0, ctx.dp(4), 0, 0) })
        c.addView(texts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        if (ok) {
            c.addView(ctx.icon(R.drawable.ic_check, 24), LinearLayout.LayoutParams(ctx.dp(24), ctx.dp(24)).apply { marginEnd = ctx.dp(8) })
            c.addView(ctx.label("ممنوحة", 15f))
        } else {
            val badge = LinearLayout(ctx).apply {
                background = rounded(Palette.amber, ctx.dp(8f))
                setPadding(ctx.dp(14), ctx.dp(8), ctx.dp(14), ctx.dp(8))
            }
            badge.addView(ctx.label("امنح", 15f, Palette.ink, true))
            c.addView(badge)
        }
        c.setOnClickListener { runCatching { onClick() } }
        c.pressable(ctx.dp(12f))
        add(c)
    }

    fun toggle(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
        val c = card()
        c.addView(ctx.label(title, 17f), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val sw = Switch(ctx).apply {
            isChecked = value
            thumbTintList = ColorStateList(states, intArrayOf(Palette.white, Palette.muted))
            trackTintList = ColorStateList(states, intArrayOf(Palette.green, Palette.laneDash))
            setOnCheckedChangeListener { _, b -> onChange(b) }
        }
        c.addView(sw)
        c.setOnClickListener { sw.toggle() }
        c.pressable(ctx.dp(12f))
        add(c)
    }

    /** اختيار من عدة: المختار لوحة خضراء. */
    fun choice(title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
        val c = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Palette.shoulder, ctx.dp(12f), ctx.dp(1), Palette.kerb)
            setPadding(ctx.dp(20), ctx.dp(14), ctx.dp(20), ctx.dp(16))
        }
        c.addView(ctx.label(title, 17f))
        val chips = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        options.forEachIndexed { i, o ->
            val on = i == selected
            chips.addView(ctx.label(o, 16f, if (on) Palette.white else Palette.muted, on).apply {
                gravity = Gravity.CENTER
                background = if (on) ctx.smallSign(Palette.green) else rounded(Palette.asphalt, ctx.dp(10f), ctx.dp(2), Palette.kerb)
                setPadding(ctx.dp(18), ctx.dp(12), ctx.dp(18), ctx.dp(12))
                minHeight = ctx.dp(64)
                setOnClickListener { if (!on) onPick(i) }
                pressable(ctx.dp(10f))
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = ctx.dp(8); topMargin = ctx.dp(12) })
        }
        c.addView(HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; addView(chips) })
        add(c)
    }

    fun seek(title: String, min: Int, max: Int, value: Int, fmt: (Int) -> String, onChange: (Int) -> Unit) {
        val c = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Palette.shoulder, ctx.dp(12f), ctx.dp(1), Palette.kerb)
            setPadding(ctx.dp(20), ctx.dp(14), ctx.dp(20), ctx.dp(14))
        }
        val top = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(ctx.label(title, 17f), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val v = ctx.label(fmt(value), 22f, Palette.white, true, digitsFace = true)
        top.addView(v)
        c.addView(top)
        c.addView(SeekBar(ctx).apply {
            this.max = max - min
            progress = (value - min).coerceIn(0, max - min)
            progressTintList = ColorStateList.valueOf(Palette.green)
            progressBackgroundTintList = ColorStateList.valueOf(Palette.laneDash)
            thumbTintList = ColorStateList.valueOf(Palette.white)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { v.text = fmt(p + min) }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) { onChange((sb?.progress ?: 0) + min) }
            })
        }, LinearLayout.LayoutParams(MATCH_PARENT, ctx.dp(64)))
        add(c)
    }
}
