package com.sahra.split

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.TextView
import java.util.Locale

/** ألوان صحراء المشتركة وأدوات صغيرة لبناء الواجهة بالكود. */
object Palette {
    val bg = Color.parseColor("#14100C")
    val card = Color.parseColor("#211A14")
    val cardHi = Color.parseColor("#2E241B")
    val line = Color.parseColor("#3A2E23")
    val accent = Color.parseColor("#E8A24A")
    val fg = Color.parseColor("#F4E3C3")
    val muted = Color.parseColor("#A8957A")
    val good = Color.parseColor("#9CC46B")
    val warn = Color.parseColor("#E5583F")
    val blue = Color.parseColor("#4FA3E8")
}

fun Context.dp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
fun Context.dp(v: Int): Int = dp(v.toFloat()).toInt()

fun rounded(color: Int, radius: Float, stroke: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radius
    if (stroke > 0) setStroke(stroke, strokeColor)
}

fun Context.label(text: String, size: Float = 16f, color: Int = Palette.fg, bold: Boolean = false) = TextView(this).apply {
    this.text = text
    textSize = size
    setTextColor(color)
    if (bold) typeface = Typeface.DEFAULT_BOLD
}

fun Context.pill(text: String, onClick: () -> Unit) = Button(this).apply {
    this.text = text
    isAllCaps = false
    textSize = 15f
    setTextColor(Palette.fg)
    background = rounded(Palette.cardHi, dp(12f))
    setPadding(dp(16), dp(10), dp(16), dp(10))
    minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
    gravity = Gravity.CENTER
    setOnClickListener { onClick() }
}

fun View.pad(all: Int) { setPadding(all, all, all, all) }

/** رقم بعدد خانات عشرية، أو «—» إن لم يكن معروفًا. */
fun num(v: Float, decimals: Int = 0): String =
    if (v.isNaN() || v.isInfinite()) "—" else String.format(Locale.US, "%.${decimals}f", v)
