package com.sahra.bridge.car

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** يشغّل خدمة الجسر مع تشغيل الشاشة ليكون جاهزًا قبل أن يفتح أحد التطبيق. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            BridgeService.start(context)
        } catch (_: Exception) {
        }
    }
}
