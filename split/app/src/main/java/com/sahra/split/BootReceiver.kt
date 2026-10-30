package com.sahra.split

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** عند تشغيل السيارة: يشغّل الكرة واللوحة العائمة إذا كان التشغيل التلقائي مفعّلًا. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs(context).autostart) return
        runCatching { OverlayService.sync(context) }
        runCatching { ClusterService.sync(context) }
    }
}
