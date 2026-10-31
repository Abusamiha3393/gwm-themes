package com.sahra.bridge.car

import android.content.Context
import java.util.UUID

/** طلب اقتران ينتظر موافقة السائق على الشاشة. */
class PairRequest(val id: String, val phone: String, val created: Long) {
    @Volatile var status = PENDING
    @Volatile var token: String? = null

    val expired: Boolean get() = status == PENDING && System.currentTimeMillis() - created > TTL

    companion object {
        const val PENDING = "pending"
        const val APPROVED = "approved"
        const val DENIED = "denied"
        const val EXPIRED = "expired"
        const val TTL = 60_000L
    }
}

/**
 * الاقتران بالموافقة: الجوال يطلب، الشاشة تعرض «سماح / رفض»، والجوال يسأل كل ثانية حتى يأخذ الرمز.
 * طلب واحد فقط في كل مرة، وكل طلب جديد يلغي السابق.
 */
object Pairing {
    @Volatile var current: PairRequest? = null
        private set
    @Volatile private var lastRequest = 0L

    /** null إن جاء الطلب بسرعة كبيرة بعد سابقه (حماية من الإغراق). */
    @Synchronized
    fun open(phone: String): PairRequest? {
        val now = System.currentTimeMillis()
        if (now - lastRequest < MIN_GAP) return null
        lastRequest = now
        current?.let { if (it.status == PairRequest.PENDING) it.status = PairRequest.EXPIRED }
        val r = PairRequest(UUID.randomUUID().toString(), phone.take(60), now)
        current = r
        return r
    }

    /** الطلب إن كان ما زال قائمًا، مع تحديث حالته إن انتهت مهلته. */
    fun find(id: String): PairRequest? {
        val r = current ?: return null
        if (r.id != id) return null
        if (r.expired) r.status = PairRequest.EXPIRED
        return r
    }

    fun pending(): PairRequest? = current?.takeIf { it.status == PairRequest.PENDING && !it.expired }

    @Synchronized
    fun approve(context: Context, id: String): PairRequest? {
        val r = find(id) ?: return null
        if (r.status != PairRequest.PENDING) return null
        r.token = Store(context).addDevice(r.phone)
        r.status = PairRequest.APPROVED
        return r
    }

    @Synchronized
    fun deny(id: String) {
        val r = find(id) ?: return
        if (r.status == PairRequest.PENDING) r.status = PairRequest.DENIED
    }

    /** يُستدعى بعد أن يأخذ الجوال الرمز، حتى لا يُسلَّم مرتين. */
    @Synchronized
    fun consumed(id: String) {
        if (current?.id == id) current = null
    }

    private const val MIN_GAP = 3000L
}
