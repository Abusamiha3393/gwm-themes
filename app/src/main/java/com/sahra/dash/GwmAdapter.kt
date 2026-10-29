package com.sahra.dash

import android.os.IBinder
import android.os.Parcel
import android.util.Log

/**
 * عميل لخدمة النظام "gwm_adapter" الموجودة في شاشات GWM (هافال وتانك).
 * ترجع null إذا لم تكن الخدمة موجودة (مثلًا على هاتف عادي أو محاكي).
 */
object GwmAdapter {
    private const val TAG = "GwmAdapter"
    private const val SERVICE = "gwm_adapter"
    private const val DESCRIPTOR = "com.gwm.android.adapter.IGwmAdapterService"
    private const val TX_GET_VALUES = 1

    @Volatile private var cached: IBinder? = null

    private fun binder(): IBinder? {
        cached?.let { if (it.isBinderAlive) return it }
        return try {
            val sm = Class.forName("android.os.ServiceManager")
            val get = sm.getMethod("getService", String::class.java)
            (get.invoke(null, SERVICE) as? IBinder).also { cached = it }
        } catch (e: Throwable) {
            Log.w(TAG, "service lookup failed: ${e.message}")
            null
        }
    }

    fun isAvailable(): Boolean = binder() != null

    fun readKeys(callerPackage: String, keys: Array<String>): Array<String?>? {
        val b = binder() ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(1)
            data.writeString(callerPackage)
            data.writeStringArray(keys)
            data.writeStringArray(null)
            data.writeInt(1)
            b.transact(TX_GET_VALUES, data, reply, 0)
            reply.readException()
            reply.createStringArray()
        } catch (e: Throwable) {
            Log.w(TAG, "readKeys failed: ${e.message}")
            cached = null
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}
