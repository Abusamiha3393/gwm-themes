package com.sahra.bridge.phone

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** ألوان صحراء داش (DESIGN.md) ليبدو الجسر جزءًا من نفس النظام. */
object Palette {
    val asphalt = Color.parseColor("#18191B")
    val gantry = Color.parseColor("#111214")
    val shoulder = Color.parseColor("#1F2023")
    val kerb = Color.parseColor("#26282B")
    val laneDash = Color.parseColor("#3B3D41")
    val green = Color.parseColor("#0A5236")
    val blue = Color.parseColor("#1A3C6B")
    val amber = Color.parseColor("#F3B300")
    val white = Color.parseColor("#F4F5F0")
    val grey = Color.parseColor("#9FA3A8")
    val ink = Color.parseColor("#16130A")
}

class Ui(private val ctx: Context) {
    fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeColor != null) setStroke(dp(1), strokeColor)
    }

    fun text(s: String, sp: Float, color: Int = Palette.white, bold: Boolean = false) = TextView(ctx).apply {
        text = s
        textSize = sp
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
    }

    /** زر كبير يناسب اللمس أثناء القيادة (76dp ارتفاعًا كحد أدنى في البطاقة). */
    fun button(label: String, bg: Int, fg: Int = Palette.white, heightDp: Int = 64, onClick: () -> Unit) =
        TextView(ctx).apply {
            text = label
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(fg)
            gravity = Gravity.CENTER
            minHeight = dp(heightDp)
            setPadding(dp(22), 0, dp(22), 0)
            background = rounded(bg, 12)
            isClickable = true
            setOnClickListener { onClick() }
        }

    fun column(padDp: Int = 0) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(padDp), dp(padDp), dp(padDp), dp(padDp))
    }

    fun row() = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun gap(dpSize: Int) = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(dp(dpSize), dp(dpSize))
    }

    fun wrap() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    fun matchWidth() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    fun weight(w: Float) = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w)
}
