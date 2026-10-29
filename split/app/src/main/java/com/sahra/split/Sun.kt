package com.sahra.split

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/** حساب الشروق والغروب (خوارزمية NOAA المبسطة) لاختيار إعداد النهار أو الليل. */
object Sun {
    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)

    /** يرجع وقت الشروق أو الغروب بالدقائق من منتصف الليل بالتوقيت المحلي، أو null في المناطق القطبية. */
    fun minutes(cal: Calendar, lat: Double, lon: Double, sunrise: Boolean): Int? {
        val n = cal.get(Calendar.DAY_OF_YEAR)
        val lngHour = lon / 15.0
        val t = n + ((if (sunrise) 6.0 else 18.0) - lngHour) / 24.0
        val m = 0.9856 * t - 3.289
        var l = m + 1.916 * sin(rad(m)) + 0.020 * sin(rad(2 * m)) + 282.634
        l = (l % 360 + 360) % 360
        var ra = deg(atan(0.91764 * tan(rad(l))))
        ra = (ra % 360 + 360) % 360
        val lQuad = floor(l / 90) * 90
        val raQuad = floor(ra / 90) * 90
        ra = (ra + (lQuad - raQuad)) / 15.0
        val sinDec = 0.39782 * sin(rad(l))
        val cosDec = cos(asin(sinDec))
        val cosH = (cos(rad(90.833)) - sinDec * sin(rad(lat))) / (cosDec * cos(rad(lat)))
        if (cosH > 1 || cosH < -1) return null
        val h = (if (sunrise) 360 - deg(acos(cosH)) else deg(acos(cosH))) / 15.0
        val localT = h + ra - 0.06571 * t - 6.622
        val ut = ((localT - lngHour) % 24 + 24) % 24
        val offsetH = TimeZone.getDefault().getOffset(cal.timeInMillis) / 3_600_000.0
        val local = ((ut + offsetH) % 24 + 24) % 24
        return (local * 60).toInt()
    }

    fun isNight(context: Context, now: Calendar = Calendar.getInstance()): Boolean {
        val p = Prefs(context)
        val rise = minutes(now, p.lat.toDouble(), p.lon.toDouble(), true) ?: 6 * 60
        val set = minutes(now, p.lat.toDouble(), p.lon.toDouble(), false) ?: 18 * 60
        val m = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return m < rise || m >= set
    }

    fun format(min: Int?): String = if (min == null) "—" else "%d:%02d".format(min / 60, min % 60)

    /** يحدّث الموقع المحفوظ من آخر قراءة GPS أو شبكة إن وُجدت صلاحية. */
    @SuppressLint("MissingPermission")
    fun refreshLocation(context: Context) {
        if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) return
        val lm = context.getSystemService(LocationManager::class.java) ?: return
        val best = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time } ?: return
        val p = Prefs(context)
        p.lat = best.latitude.toFloat()
        p.lon = best.longitude.toFloat()
    }

    /** إن كان التبديل التلقائي مفعّلًا، يختار الإعداد المناسب للوقت. يرجع true إذا تغيّر الإعداد. */
    fun applyDayNight(context: Context): Boolean {
        val p = Prefs(context)
        if (!p.autoDayNight) return false
        val want = if (isNight(context)) p.nightPreset else p.dayPreset
        if (want == p.presetIndex) return false
        p.presetIndex = want
        return true
    }
}
