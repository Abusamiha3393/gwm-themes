package com.sahra.bridge.car

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** يرسم رمز QR أسود على أبيض بالحجم المطلوب. */
object Qr {
    private var cacheText = ""
    private var cacheBitmap: Bitmap? = null

    fun bitmap(text: String, size: Int): Bitmap {
        val cached = cacheBitmap
        if (cached != null && text == cacheText && cached.width == size) return cached
        val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val px = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) px[y * size + x] = if (m.get(x, y)) Color.BLACK else Color.WHITE
        }
        val b = Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
        cacheText = text
        cacheBitmap = b
        return b
    }
}
