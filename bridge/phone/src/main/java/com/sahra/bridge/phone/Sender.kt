package com.sahra.bridge.phone

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.concurrent.thread

/** ما يحفظه الجوال: السيارة المقترنة، الطابور، والسجل. */
class PhoneStore(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("bridge", Context.MODE_PRIVATE)

    val paired: Boolean get() = token.isNotEmpty()
    var token: String
        get() = p.getString("token", "") ?: ""
        set(v) = p.edit().putString("token", v).apply()
    var carId: String
        get() = p.getString("carId", "") ?: ""
        set(v) = p.edit().putString("carId", v).apply()
    var carName: String
        get() = p.getString("carName", "") ?: ""
        set(v) = p.edit().putString("carName", v).apply()
    var host: String
        get() = p.getString("host", "") ?: ""
        set(v) = p.edit().putString("host", v).apply()
    var port: Int
        get() = p.getInt("port", Client.PORT)
        set(v) = p.edit().putInt("port", v).apply()

    fun savePairing(car: Car, newToken: String) {
        p.edit().putString("token", newToken).putString("carId", car.id).putString("carName", car.name)
            .putString("host", car.host).putInt("port", car.port).apply()
    }

    fun unpair() {
        p.edit().remove("token").remove("carId").remove("carName").remove("host").remove("pending").apply()
    }

    @Synchronized
    fun pending(): List<JSONObject> = list("pending")

    @Synchronized
    fun enqueue(item: JSONObject) = save("pending", (pending() + item).takeLast(MAX_PENDING))

    @Synchronized
    fun dropPending(sent: List<JSONObject>) {
        val ids = sent.map { it.optString("localId") }.toSet()
        save("pending", pending().filter { it.optString("localId") !in ids })
    }

    @Synchronized
    fun history(): List<JSONObject> = list("history")

    @Synchronized
    fun record(item: JSONObject) = save("history", (listOf(item) + history()).take(MAX_HISTORY))

    private fun list(key: String): List<JSONObject> {
        val arr = JSONArray(p.getString(key, "[]"))
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    private fun save(key: String, items: List<JSONObject>) {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        p.edit().putString(key, arr.toString()).apply()
    }

    companion object {
        const val MAX_PENDING = 30
        const val MAX_HISTORY = 20
    }
}

object Sender {
    enum class Result { SENT, QUEUED, NOT_PAIRED, REVOKED, REJECTED }

    /** يجد السيارة المقترنة: آخر عنوان معروف أولًا، ثم البحث في الشبكة. */
    fun locate(context: Context, store: PhoneStore = PhoneStore(context)): Car? {
        if (!store.paired) return null
        if (store.host.isNotEmpty()) {
            try {
                val car = Client.info(store.host, store.port, 2000)
                if (car.id == store.carId) return car
            } catch (_: IOException) {
            }
        }
        val car = Discovery.find(context, 4000, stopAtId = store.carId).firstOrNull { it.id == store.carId }
            ?: return null
        store.host = car.host
        store.port = car.port
        return car
    }

    /** يرسل عنصرًا، أو يضعه في الطابور إن لم تكن السيارة متاحة. متزامن: من خيط خلفي فقط. */
    fun deliver(context: Context, item: JSONObject): Result {
        val store = PhoneStore(context)
        if (!store.paired) return Result.NOT_PAIRED
        item.put("localId", item.optString("localId").ifEmpty { System.nanoTime().toString() })
        item.put("sentAt", item.optLong("sentAt", System.currentTimeMillis()))
        val car = locate(context, store)
        if (car == null) {
            store.enqueue(item)
            schedule(context)
            return Result.QUEUED
        }
        val r = post(store, car, item)
        if (r == Result.QUEUED) {
            store.enqueue(item)
            schedule(context)
        }
        if (r == Result.SENT) flush(context, car)
        return r
    }

    /** يرسل ما في الطابور. يعيد true إن فرغ الطابور. */
    fun flush(context: Context, known: Car? = null): Boolean {
        val store = PhoneStore(context)
        val items = store.pending()
        if (items.isEmpty()) return true
        if (!store.paired) return true
        val car = known ?: locate(context, store) ?: return false
        val done = ArrayList<JSONObject>()
        for (item in items) {
            when (post(store, car, item)) {
                Result.SENT, Result.REJECTED -> done.add(item)
                Result.REVOKED -> return true.also { store.dropPending(items) }
                else -> break
            }
        }
        store.dropPending(done)
        return store.pending().isEmpty()
    }

    private fun post(store: PhoneStore, car: Car, item: JSONObject): Result {
        val wire = JSONObject().put("type", item.optString("type"))
            .put("payload", item.optJSONObject("payload") ?: JSONObject())
            .put("sentAt", item.optLong("sentAt"))
        return try {
            Client.send(car.host, car.port, store.token, wire)
            store.record(JSONObject(item.toString()).put("status", "sent").put("at", System.currentTimeMillis()))
            Result.SENT
        } catch (e: HttpError) {
            when (e.code) {
                401 -> {
                    // أُزيل الجوال من قائمة الأجهزة الموثوقة على الشاشة
                    store.unpair()
                    Result.REVOKED
                }
                400, 413 -> Result.REJECTED
                else -> Result.QUEUED
            }
        } catch (e: IOException) {
            Result.QUEUED
        }
    }

    fun schedule(context: Context) {
        val js = context.getSystemService(JobScheduler::class.java) ?: return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, QueueJob::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_LINEAR)
            .build()
        js.schedule(job)
    }

    fun phoneName(context: Context): String {
        val name = try {
            Settings.Global.getString(context.contentResolver, "device_name")
        } catch (e: Exception) {
            null
        }
        return name?.takeIf { it.isNotBlank() } ?: "${Build.MANUFACTURER} ${Build.MODEL}".trim()
    }

    private const val JOB_ID = 4780
}

/** يعيد محاولة إرسال الطابور في الخلفية حتى تظهر السيارة على الشبكة. */
class QueueJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        thread(name = "bridge-flush") {
            val empty = try {
                Sender.flush(applicationContext)
            } catch (e: Exception) {
                false
            }
            jobFinished(params, !empty)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}
