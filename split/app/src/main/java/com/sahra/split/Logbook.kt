package com.sahra.split

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.max
import kotlin.math.min

data class Trip(
    val start: Long,
    val end: Long,
    val startOdo: Float,
    val endOdo: Float,
    val maxSpeed: Float,
    val driveSec: Long,
    val movingSec: Long,
    val startFuel: Float,
    val endFuel: Float,
    val done: Boolean,
) {
    val km: Float get() = max(0f, endOdo - startOdo)
    /** متوسط السرعة أثناء الحركة فقط. */
    val avgSpeed: Float get() = if (movingSec < 30) Float.NaN else km / (movingSec / 3600f)

    fun liters(tank: Float): Float {
        if (startFuel.isNaN() || endFuel.isNaN()) return Float.NaN
        val d = startFuel - endFuel
        return if (d <= 0f) Float.NaN else d / 100f * tank
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("start", start); put("end", end)
        put("so", startOdo.toDouble()); put("eo", endOdo.toDouble())
        put("max", maxSpeed.toDouble()); put("drive", driveSec); put("moving", movingSec)
        put("sf", jnum(startFuel)); put("ef", jnum(endFuel)); put("done", done)
    }

    companion object {
        fun from(o: JSONObject) = Trip(
            o.optLong("start"), o.optLong("end"),
            o.optDouble("so").toFloat(), o.optDouble("eo").toFloat(),
            o.optDouble("max").toFloat(), o.optLong("drive"), o.optLong("moving"),
            o.optDouble("sf", Double.NaN).toFloat(), o.optDouble("ef", Double.NaN).toFloat(),
            o.optBoolean("done", true),
        )
    }
}

data class Refuel(
    val at: Long,
    val odo: Float,
    val fromPct: Float,
    val toPct: Float,
    val liters: Float,
    /** استهلاك الخزان السابق (لتر/100 كم)، أو NaN إن كانت المسافة قصيرة. */
    val prevConsumption: Float,
    val prevKm: Float,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("at", at); put("odo", odo.toDouble()); put("from", fromPct.toDouble()); put("to", toPct.toDouble())
        put("l", jnum(liters)); put("pc", jnum(prevConsumption)); put("pk", jnum(prevKm))
    }

    companion object {
        fun from(o: JSONObject) = Refuel(
            o.optLong("at"), o.optDouble("odo").toFloat(), o.optDouble("from").toFloat(), o.optDouble("to").toFloat(),
            o.optDouble("l", Double.NaN).toFloat(), o.optDouble("pc", Double.NaN).toFloat(), o.optDouble("pk", Double.NaN).toFloat(),
        )
    }
}

/** تذكير بتاريخ أو بقراءة عداد (مثل تغيير الزيت عند 85000 كم). */
data class Reminder(
    val id: Long,
    val text: String,
    val byOdo: Boolean,
    /** تاريخ بالملي ثانية، أو كيلومترات العداد. */
    val target: Double,
    val notified: Boolean = false,
) {
    fun due(odo: Float, now: Long = System.currentTimeMillis()): Boolean =
        if (byOdo) !odo.isNaN() && odo >= target else now >= target.toLong()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("text", text); put("odo", byOdo); put("target", target); put("n", notified)
    }

    companion object {
        fun from(o: JSONObject) = Reminder(
            o.optLong("id"), o.optString("text"), o.optBoolean("odo"), o.optDouble("target"), o.optBoolean("n"),
        )
    }
}

data class Stats(
    val since: Long = System.currentTimeMillis(),
    val maxSpeed: Float = 0f,
    val maxRpm: Float = 0f,
    val maxCoolant: Float = Float.NaN,
    val minOutside: Float = Float.NaN,
    val maxOutside: Float = Float.NaN,
    val engineSec: Long = 0,
    val km: Float = 0f,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("since", since); put("ms", maxSpeed.toDouble()); put("mr", maxRpm.toDouble())
        put("mc", jnum(maxCoolant)); put("mino", jnum(minOutside)); put("maxo", jnum(maxOutside))
        put("eng", engineSec); put("km", km.toDouble())
    }

    companion object {
        fun from(o: JSONObject) = Stats(
            o.optLong("since"), o.optDouble("ms").toFloat(), o.optDouble("mr").toFloat(),
            o.optDouble("mc", Double.NaN).toFloat(), o.optDouble("mino", Double.NaN).toFloat(),
            o.optDouble("maxo", Double.NaN).toFloat(), o.optLong("eng"), o.optDouble("km").toFloat(),
        )
    }
}

private fun jnum(v: Float): Any = if (v.isNaN() || v.isInfinite()) JSONObject.NULL else v.toDouble()

private fun fmin(a: Float, b: Float) = if (a.isNaN()) b else if (b.isNaN()) a else min(a, b)
private fun fmax(a: Float, b: Float) = if (a.isNaN()) b else if (b.isNaN()) a else max(a, b)

/**
 * السجل: الرحلات (آخر 30)، التعبئات (آخر 30)، التذكيرات (حتى 20)، والإحصاءات.
 * يُحفظ في ملف JSON داخل التطبيق. التسجيل من بيانات السيارة الحية فقط، لا من الوضع التجريبي.
 */
object Logbook {
    const val MAX_TRIPS = 30
    const val MAX_REFUELS = 30
    const val MAX_REMINDERS = 20

    /** تبدأ الرحلة عند تجاوز هذه السرعة، وتنتهي بعد إطفاء المحرك دقيقتين أو التوقف 10 دقائق. */
    private const val START_SPEED = 3f
    private const val ENGINE_OFF_MS = 2 * 60_000L
    private const val IDLE_MS = 10 * 60_000L
    private const val STALE_MS = 15 * 60_000L

    private var loaded = false
    private lateinit var file: File
    private val trips = mutableListOf<Trip>()
    private val refuels = mutableListOf<Refuel>()
    private val reminders = mutableListOf<Reminder>()
    private var stats = Stats()
    private var current: Trip? = null

    private var lastSample = 0L
    private var idleSince = 0L
    private var engineOffSince = 0L
    private var lastSave = 0L
    private var lastReminderCheck = 0L
    private var lastOdo = Float.NaN

    @Synchronized
    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        file = File(context.applicationContext.filesDir, "logbook.json")
        val o = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return
        o.optJSONArray("trips")?.let { a -> for (i in 0 until a.length()) trips.add(Trip.from(a.getJSONObject(i))) }
        o.optJSONArray("refuels")?.let { a -> for (i in 0 until a.length()) refuels.add(Refuel.from(a.getJSONObject(i))) }
        o.optJSONArray("reminders")?.let { a -> for (i in 0 until a.length()) reminders.add(Reminder.from(a.getJSONObject(i))) }
        o.optJSONObject("stats")?.let { stats = Stats.from(it) }
        o.optJSONObject("current")?.let { c ->
            val t = Trip.from(c)
            // رحلة لم تُغلق لأن التطبيق توقف: نغلقها إن مضى عليها وقت طويل
            if (System.currentTimeMillis() - t.end > STALE_MS) addTrip(t.copy(done = true)) else current = t
        }
    }

    @Synchronized
    private fun save() {
        val o = JSONObject()
        o.put("trips", JSONArray().apply { trips.forEach { put(it.toJson()) } })
        o.put("refuels", JSONArray().apply { refuels.forEach { put(it.toJson()) } })
        o.put("reminders", JSONArray().apply { reminders.forEach { put(it.toJson()) } })
        o.put("stats", stats.toJson())
        current?.let { o.put("current", it.toJson()) }
        runCatching {
            val tmp = File(file.parentFile, "logbook.json.tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(file)
        }
        lastSave = System.currentTimeMillis()
    }

    private fun addTrip(t: Trip) {
        if (t.km < 0.3f) return
        trips.add(0, t)
        while (trips.size > MAX_TRIPS) trips.removeAt(trips.size - 1)
    }

    // ---------------- التسجيل (من خيط بيانات السيارة، مرة كل ثانية) ----------------

    @Synchronized
    fun onSample(context: Context, s: VehicleState) {
        load(context)
        if (!s.live) return
        val now = System.currentTimeMillis()
        val dt = if (lastSample == 0L) 0L else min(5_000L, now - lastSample)
        lastSample = now

        // الإحصاءات
        var st = stats
        if (s.rpm > 300f) st = st.copy(engineSec = st.engineSec + dt / 1000)
        if (!lastOdo.isNaN() && !s.odometerKm.isNaN()) {
            val d = s.odometerKm - lastOdo
            if (d in 0f..2f) st = st.copy(km = st.km + d)
        }
        if (!s.odometerKm.isNaN()) lastOdo = s.odometerKm
        stats = st.copy(
            maxSpeed = max(st.maxSpeed, s.speed),
            maxRpm = max(st.maxRpm, s.rpm),
            maxCoolant = fmax(st.maxCoolant, s.coolantTemp),
            minOutside = fmin(st.minOutside, s.outsideTemp),
            maxOutside = fmax(st.maxOutside, s.outsideTemp),
        )

        // الرحلة
        val odo = s.odometerKm
        var t = current
        if (t == null && s.speed > START_SPEED && !odo.isNaN()) {
            t = Trip(now, now, odo, odo, s.speed, 0, 0, s.fuelPercent, s.fuelPercent, false)
            idleSince = 0L; engineOffSince = 0L
            current = t
            save()
        }
        if (t != null) {
            val moving = s.speed > 1f
            t = t.copy(
                end = now,
                endOdo = if (odo.isNaN()) t.endOdo else max(t.endOdo, odo),
                maxSpeed = max(t.maxSpeed, s.speed),
                driveSec = t.driveSec + dt / 1000,
                movingSec = t.movingSec + if (moving) dt / 1000 else 0,
                endFuel = if (s.fuelPercent.isNaN()) t.endFuel else s.fuelPercent,
            )
            current = t
            idleSince = if (moving) 0L else if (idleSince == 0L) now else idleSince
            engineOffSince = if (s.rpm >= 100f) 0L else if (engineOffSince == 0L) now else engineOffSince
            val ended = (engineOffSince != 0L && now - engineOffSince >= ENGINE_OFF_MS) ||
                (idleSince != 0L && now - idleSince >= IDLE_MS)
            if (ended) {
                addTrip(t.copy(done = true))
                current = null
                save()
            }
        }

        if (now - lastReminderCheck >= 60_000L) {
            lastReminderCheck = now
            checkReminders(context, odo)
        }
        if (now - lastSave >= 60_000L) save()
    }

    /** يُستدعى من FuelTracker عند رصد تعبئة. */
    @Synchronized
    fun onRefuel(context: Context, odo: Float, from: Float, to: Float, prevConsumption: Float, prevKm: Float) {
        load(context)
        val tank = Prefs(context).tankLiters
        refuels.add(0, Refuel(System.currentTimeMillis(), odo, from, to, (to - from) / 100f * tank, prevConsumption, prevKm))
        while (refuels.size > MAX_REFUELS) refuels.removeAt(refuels.size - 1)
        save()
    }

    // ---------------- التذكيرات ----------------

    private fun checkReminders(context: Context, odo: Float) {
        var changed = false
        reminders.forEachIndexed { i, r ->
            if (!r.notified && r.due(odo)) {
                notify(context, r)
                reminders[i] = r.copy(notified = true)
                changed = true
            }
        }
        if (changed) save()
    }

    private fun notify(context: Context, r: Reminder) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel("reminders") == null) {
            nm.createNotificationChannel(NotificationChannel("reminders", "التذكيرات", NotificationManager.IMPORTANCE_HIGH))
        }
        val open = PendingIntent.getActivity(
            context, 21, Intent(context, LogActivity::class.java).putExtra("tab", 2).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(context, "reminders")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("تذكير")
            .setContentText(r.text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify((r.id % 100000).toInt() + 100, n) }
    }

    // ---------------- القراءة والتعديل من الواجهة ----------------

    @Synchronized fun trips(context: Context): List<Trip> { load(context); return listOfNotNull(current) + trips }
    @Synchronized fun refuels(context: Context): List<Refuel> { load(context); return refuels.toList() }
    @Synchronized fun reminders(context: Context): List<Reminder> { load(context); return reminders.toList() }
    @Synchronized fun stats(context: Context): Stats { load(context); return stats }

    /** التذكيرات المستحقة الآن (لشريط التنبيه في الشاشة الرئيسية). */
    @Synchronized
    fun dueReminders(context: Context): List<Reminder> {
        load(context)
        val odo = VehicleRepository.state.odometerKm
        return reminders.filter { it.due(odo) }
    }

    @Synchronized
    fun saveReminder(context: Context, r: Reminder): Boolean {
        load(context)
        val i = reminders.indexOfFirst { it.id == r.id }
        if (i >= 0) reminders[i] = r else {
            if (reminders.size >= MAX_REMINDERS) return false
            reminders.add(r)
        }
        save()
        return true
    }

    @Synchronized
    fun deleteReminder(context: Context, id: Long) { load(context); reminders.removeAll { it.id == id }; save() }

    @Synchronized
    fun deleteTrip(context: Context, start: Long) {
        load(context)
        if (current?.start == start) current = null
        trips.removeAll { it.start == start }
        save()
    }

    @Synchronized fun clearTrips(context: Context) { load(context); trips.clear(); current = null; save() }
    @Synchronized fun deleteRefuel(context: Context, at: Long) { load(context); refuels.removeAll { it.at == at }; save() }
    @Synchronized fun clearRefuels(context: Context) { load(context); refuels.clear(); save() }
    @Synchronized fun resetStats(context: Context) { load(context); stats = Stats(); save() }
}
