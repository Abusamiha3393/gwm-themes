package com.sahra.split

import android.graphics.Color

/** Generated from replica/design/tokens.json. Roles, not colours: use these instead of raw hex. */
object Tokens {
    val bg = Color.parseColor("#18191B")
    val bgDeep = Color.parseColor("#111214")
    val surface = Color.parseColor("#1F2023")
    val surfaceRaised = Color.parseColor("#26282B")
    val border = Color.parseColor("#3B3D41")
    val borderInput = Color.parseColor("#74777D")
    val borderSign = Color.parseColor("#F4F5F0")
    val text = Color.parseColor("#F4F5F0")
    val textDim = Color.parseColor("#D2D3CF")
    val textMuted = Color.parseColor("#9FA3A8")
    val signRoad = Color.parseColor("#0A5236")
    val signService = Color.parseColor("#1A3C6B")
    val signLeisure = Color.parseColor("#5A321C")
    val accent = Color.parseColor("#1A3C6B")
    val onAccent = Color.parseColor("#F4F5F0")
    val warning = Color.parseColor("#F3B300")
    val onWarning = Color.parseColor("#16130A")
    val danger = Color.parseColor("#C8102E")
    val dangerText = Color.parseColor("#FF5A6E")
    val onDanger = Color.parseColor("#F4F5F0")
    val success = Color.parseColor("#3DBE7A")
    val focus = Color.parseColor("#F3B300")

    /** Spacing scale in design dp (pass through Context.dp()). */
    val space = intArrayOf(0, 4, 8, 12, 16, 24, 32, 48, 64)
    const val TARGET_MIN = 64; const val TARGET_RAIL = 72; const val TARGET_BALL = 76
    const val TYPE_CAPTION = 13f
    const val TYPE_BODY = 16f
    const val TYPE_LABEL = 17f
    const val TYPE_TITLE = 24f
    const val TYPE_DISPLAY = 34f
    const val TYPE_METRIC = 56f
    const val TYPE_METRIC_XL = 96f
}
