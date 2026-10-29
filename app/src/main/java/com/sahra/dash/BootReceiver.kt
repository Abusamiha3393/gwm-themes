package com.sahra.dash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** يشغّل العدادات تلقائيًا عند تشغيل السيارة إذا كان الخيار مفعّلًا. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs(context).autostart) return
        if (!Settings.canDrawOverlays(context)) return
        try {
            OverlayService.send(context, OverlayService.ACTION_SHOW)
        } catch (_: Exception) {
        }
    }
}
