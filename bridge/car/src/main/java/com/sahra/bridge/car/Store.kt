package com.sahra.bridge.car

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID

/** جهاز جوال تم اقترانه. نحفظ بصمة الرمز فقط، لا الرمز نفسه. */
data class Device(val hash: String, val name: String, val added: Long)

/** عنصر وصل من الجوال: موقع أو رابط أو نص أو ملف (uri لملف = content:// من BridgeFiles). */
data class Item(
    val id: String,
    val type: String,
    val title: String,
    val detail: String,
    val uri: String,
    val from: String,
    val at: Long,
    val opened: Boolean,
    val mime: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("type", type).put("title", title).put("detail", detail)
        .put("uri", uri).put("from", from).put("at", at).put("opened", opened).put("mime", mime)

    companion object {
        fun fromJson(o: JSONObject) = Item(
            o.optString("id"), o.optString("type"), o.optString("title"), o.optString("detail"),
            o.optString("uri"), o.optString("from"), o.optLong("at"), o.optBoolean("opened"),
            o.optString("mime"),
        )
    }
}

/** كل ما يحفظه التطبيق، في SharedPreferences واحد. الدوال متزامنة لأن الخادم يعمل على عدة خيوط. */
class Store(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("bridge", Context.MODE_PRIVATE)
    private val random = SecureRandom()

    @get:Synchronized
    val deviceId: String
        get() {
            val existing = p.getString("id", null)
            if (existing != null) return existing
            val id = UUID.randomUUID().toString()
            p.edit().putString("id", id).apply()
            return id
        }

    var carName: String
        get() = p.getString("name", null) ?: "HAVAL"
        set(v) = p.edit().putString("name", v).apply()

    /** فتح المواقع مباشرة في تطبيق الملاحة بدل إظهار بطاقة. */
    var autoOpenGeo: Boolean
        get() = p.getBoolean("autoGeo", false)
        set(v) = p.edit().putBoolean("autoGeo", v).apply()

    /** تطبيق الملاحة المفضّل، أو فارغ لترك الاختيار للنظام. */
    var navPackage: String
        get() = p.getString("nav", "") ?: ""
        set(v) = p.edit().putString("nav", v).apply()

    // ---- رمز الاقتران ----

    @Synchronized
    fun currentCode(): String {
        val code = p.getString("code", null)
        if (code == null || System.currentTimeMillis() > codeExpiresAt()) return newCode()
        return code
    }

    fun codeExpiresAt(): Long = p.getLong("codeExp", 0L)

    @Synchronized
    fun newCode(): String {
        val code = String.format(Locale.US, "%06d", random.nextInt(1_000_000))
        p.edit().putString("code", code).putLong("codeExp", System.currentTimeMillis() + CODE_TTL).apply()
        return code
    }

    /** يقبل الرمز مرة واحدة فقط، ثم يولّد رمزًا جديدًا. */
    @Synchronized
    fun consumeCode(code: String): Boolean {
        val current = p.getString("code", null) ?: return false
        if (System.currentTimeMillis() > codeExpiresAt()) return false
        if (!MessageDigest.isEqual(current.toByteArray(), code.trim().toByteArray())) return false
        newCode()
        return true
    }

    // ---- الأجهزة الموثوقة ----

    @Synchronized
    fun devices(): List<Device> {
        val arr = JSONArray(p.getString("devices", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Device(o.optString("hash"), o.optString("name"), o.optLong("added"))
        }
    }

    /** يضيف جهازًا ويعيد رمز الجلسة الذي يحفظه الجوال. */
    @Synchronized
    fun addDevice(name: String): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        val token = hex(bytes)
        val arr = JSONArray(p.getString("devices", "[]"))
        arr.put(JSONObject().put("hash", sha256(token)).put("name", name.take(60)).put("added", System.currentTimeMillis()))
        p.edit().putString("devices", arr.toString()).apply()
        return token
    }

    @Synchronized
    fun findDevice(token: String): Device? {
        val h = sha256(token)
        return devices().firstOrNull { MessageDigest.isEqual(it.hash.toByteArray(), h.toByteArray()) }
    }

    @Synchronized
    fun removeDevice(hash: String) {
        val arr = JSONArray()
        devices().filter { it.hash != hash }.forEach {
            arr.put(JSONObject().put("hash", it.hash).put("name", it.name).put("added", it.added))
        }
        p.edit().putString("devices", arr.toString()).apply()
    }

    // ---- الوارد ----

    @Synchronized
    fun inbox(): List<Item> {
        val arr = JSONArray(p.getString("inbox", "[]"))
        return (0 until arr.length()).map { Item.fromJson(arr.getJSONObject(it)) }
    }

    @Synchronized
    fun addItem(item: Item) = saveInbox(listOf(item) + inbox())

    @Synchronized
    fun markOpened(id: String) = saveInbox(inbox().map { if (it.id == id) it.copy(opened = true) else it })

    @Synchronized
    fun clearInbox() = saveInbox(emptyList())

    private fun saveInbox(items: List<Item>) {
        val arr = JSONArray()
        items.take(MAX_INBOX).forEach { arr.put(it.toJson()) }
        p.edit().putString("inbox", arr.toString()).apply()
    }

    companion object {
        const val CODE_TTL = 5 * 60 * 1000L
        const val MAX_INBOX = 50

        fun sha256(s: String): String = hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray()))
        fun hex(b: ByteArray): String = b.joinToString("") { String.format(Locale.US, "%02x", it) }
    }
}
