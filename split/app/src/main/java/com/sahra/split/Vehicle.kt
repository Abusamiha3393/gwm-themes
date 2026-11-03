package com.sahra.split

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.max
import kotlin.math.sin

/** حالة السيارة. القيم غير المعروفة تبقى NaN. */
data class VehicleState(
    val speed: Float = 0f,
    val rpm: Float = 0f,
    val fuelPercent: Float = Float.NaN,
    val rangeKm: Float = Float.NaN,
    val odometerKm: Float = Float.NaN,
    val coolantTemp: Float = Float.NaN,
    val outsideTemp: Float = Float.NaN,
    val insideTemp: Float = Float.NaN,
    val batteryVoltage: Float = Float.NaN,
    val steeringAngle: Float = Float.NaN,
    val pitch: Float = Float.NaN,
    val roll: Float = Float.NaN,
    val compass: Float = Float.NaN,
    val altitude: Float = Float.NaN,
    /** قيمة gear_status الخام كما ترسلها السيارة (-1 = غير معروفة)، ويُترجم إلى حرف عبر Gears. */
    val gearRaw: Int = -1,
    /** رقم الغيار الحالي من current_gear (0 عند التوقف، -1 = غير معروف). */
    val gearNo: Int = -1,
    /** ضغط الإطارات بالـ psi وحرارتها بالدرجة المئوية: أمامي يسار، أمامي يمين، خلفي يسار، خلفي يمين. */
    val tirePsi: List<Float> = emptyList(),
    val tireTemp: List<Float> = emptyList(),
    /** مسافة الرحلة الحالية كما تحسبها السيارة، وعداد الرحلة القابل للتصفير. */
    val journeyKm: Float = Float.NaN,
    val tripKm: Float = Float.NaN,
    /** متوسط الاستهلاك لتر/100 كم كما تعرضه السيارة. */
    val avgConsumption: Float = Float.NaN,
    /** نسبة شحن البطارية، والضغط الجوي بالهكتوباسكال. */
    val batteryLevel: Float = Float.NaN,
    val baroHpa: Float = Float.NaN,
    val live: Boolean = false,
    val demo: Boolean = false,
)

/** آخر 10 دقائق من السرعة والدورات، عينة كل ثانية. */
object History {
    const val SIZE = 600
    val speed = FloatArray(SIZE)
    val rpm = FloatArray(SIZE)
    @Volatile var count = 0
        private set
    private var head = 0

    @Synchronized
    fun add(s: VehicleState) {
        speed[head] = s.speed
        rpm[head] = s.rpm
        head = (head + 1) % SIZE
        if (count < SIZE) count++
    }

    /** نسخة مرتبة من الأقدم إلى الأحدث. */
    @Synchronized
    fun snapshot(src: FloatArray): FloatArray {
        val out = FloatArray(count)
        val start = (head - count + SIZE) % SIZE
        for (i in 0 until count) out[i] = src[(start + i) % SIZE]
        return out
    }
}

/**
 * يقرأ بيانات السيارة من خدمة GWM كل ثانية.
 * إذا لم تتوفر الخدمة (هاتف أو محاكي) أو فُعّل الوضع التجريبي، يولّد بيانات وهمية متحركة.
 */
object VehicleRepository {
    private const val TAG = "VehicleRepo"

    fun interface Listener {
        fun onState(state: VehicleState)
    }

    private val listeners = CopyOnWriteArraySet<Listener>()
    private val main = Handler(Looper.getMainLooper())

    @Volatile var state = VehicleState()
        private set

    @Volatile var forceDemo = false
    @Volatile private var running = false
    private var thread: Thread? = null
    private var appContext: Context? = null

    private val KEYS = arrayOf(
        "car.basic.ivivehiclespeed",
        "car.basic.vehicle_speed",
        "car.basic.engine_speed",
        "car.basic.coolant_temp",
        "car.basic.steering_wheel_angle",
        "car.basic.remain_fuel_percentage",
        "car.basic.remain_odometer",
        "car.basic.total_odometer",
        "car.basic.odometer",
        "car.basic.battery_voltage",
        "car.basic.outside_temp",
        "car.basic.inside_temp",
        "car.off_road_info.pitch_angle",
        "car.off_road_info.roll_angle",
        "car.off_road_info.compass_direction",
        "car.off_road_info.altitude",
        // اكتُشفت في هافال H9 بمستكشف المفاتيح (2026-11-04)
        "car.basic.gear_status",
        "car.basic.current_gear",
        "car.basic.tpms_status",
        "car.basic.cur_journey_odometer",
        "car.basic.accumulated_odometer",
        "car.basic.avg_fuel_consumption",
        "car.basic.battery_power_level",
        "car.basic.baropressure",
    )

    fun addListener(l: Listener) { listeners.add(l); l.onState(state) }
    fun removeListener(l: Listener) { listeners.remove(l) }

    @Synchronized
    fun start(context: Context) {
        if (running) return
        appContext = context.applicationContext
        forceDemo = Prefs(context).demo
        running = true
        thread = Thread(::loop, "vehicle-data").apply { isDaemon = true; start() }
    }

    @Synchronized
    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }

    private fun loop() {
        var t = 0.0
        var lastSample = 0L
        while (running) {
            try {
                val ctx = appContext ?: return
                val values = if (forceDemo) null else GwmAdapter.readKeys(ctx.packageName, KEYS)
                val s: VehicleState
                val pause: Long
                if (values != null) {
                    var v = VehicleState(live = true)
                    KEYS.forEachIndexed { i, key ->
                        val raw = values.getOrNull(i)
                        if (!raw.isNullOrBlank() && raw != "null") v = apply(v, key, clean(raw))
                    }
                    s = v
                    pause = 1000
                } else {
                    t += 0.2
                    s = demoState(t)
                    pause = 200
                }
                val now = System.currentTimeMillis()
                if (now - lastSample >= 1000) {
                    lastSample = now
                    History.add(s)
                    FuelTracker.update(ctx, s)
                    Logbook.onSample(ctx, s)
                }
                publish(s)
                Thread.sleep(pause)
            } catch (_: InterruptedException) {
                return
            } catch (e: Throwable) {
                Log.e(TAG, "loop: ${e.message}")
                try { Thread.sleep(1000) } catch (_: InterruptedException) { return }
            }
        }
    }

    private fun clean(raw: String) = raw.trim().removePrefix("[").removeSuffix("]").trim()

    private fun apply(s: VehicleState, key: String, v: String): VehicleState {
        // ثمانية أرقام: ضغط ثم حرارة لكل إطار بالترتيب
        if (key == "car.basic.tpms_status") {
            val n = v.split(',').mapNotNull { it.trim().toFloatOrNull() }
            if (n.size < 8) return s
            return s.copy(
                tirePsi = listOf(n[0], n[2], n[4], n[6]).map { if (it > 0f) it else Float.NaN },
                tireTemp = listOf(n[1], n[3], n[5], n[7]).map { if (it > -40f) it else Float.NaN },
            )
        }
        val f = v.toFloatOrNull() ?: return s
        return when (key) {
            "car.basic.ivivehiclespeed" -> s.copy(speed = max(0f, f))
            "car.basic.vehicle_speed" -> if (s.speed == 0f) s.copy(speed = max(0f, f)) else s
            "car.basic.engine_speed" -> s.copy(rpm = max(0f, f))
            "car.basic.coolant_temp" -> s.copy(coolantTemp = f)
            "car.basic.steering_wheel_angle" -> s.copy(steeringAngle = f)
            "car.basic.remain_fuel_percentage" -> s.copy(fuelPercent = f.coerceIn(0f, 100f))
            "car.basic.remain_odometer" -> s.copy(rangeKm = f)
            "car.basic.total_odometer", "car.basic.odometer" -> if (f > 0f) s.copy(odometerKm = f) else s
            "car.basic.battery_voltage" -> s.copy(batteryVoltage = f)
            "car.basic.outside_temp" -> s.copy(outsideTemp = f)
            "car.basic.inside_temp" -> s.copy(insideTemp = f)
            "car.off_road_info.pitch_angle" -> s.copy(pitch = f)
            "car.off_road_info.roll_angle" -> s.copy(roll = f)
            "car.off_road_info.compass_direction" -> s.copy(compass = f)
            "car.off_road_info.altitude" -> s.copy(altitude = f)
            "car.basic.gear_status" -> s.copy(gearRaw = f.toInt())
            "car.basic.current_gear" -> s.copy(gearNo = f.toInt())
            "car.basic.cur_journey_odometer" -> s.copy(journeyKm = f)
            "car.basic.accumulated_odometer" -> s.copy(tripKm = f)
            "car.basic.avg_fuel_consumption" -> if (f > 0f) s.copy(avgConsumption = f) else s
            "car.basic.battery_power_level" -> if (f in 0f..100f) s.copy(batteryLevel = f) else s
            "car.basic.baropressure" -> if (f > 300f) s.copy(baroHpa = f) else s
            else -> s
        }
    }

    private fun demoState(t: Double): VehicleState {
        val cycle = (sin(t / 6.0) + 1.0) / 2.0
        val speed = (cycle * 140.0).toFloat()
        val rpm = (900 + cycle * 3800 + 300 * sin(t * 1.7)).toFloat()
        return VehicleState(
            speed = speed,
            rpm = rpm,
            fuelPercent = 62f,
            rangeKm = 438f,
            odometerKm = (23518 + t / 10).toFloat(),
            coolantTemp = 88f,
            outsideTemp = 34f,
            insideTemp = 23f,
            batteryVoltage = 14.1f,
            steeringAngle = (25 * sin(t / 2.0)).toFloat(),
            pitch = (12 * sin(t / 3.1)).toFloat(),
            roll = (8 * sin(t / 2.3)).toFloat(),
            compass = ((t * 6.0) % 360.0).toFloat(),
            altitude = (612 + 40 * sin(t / 9.0)).toFloat(),
            gearRaw = 2,
            gearNo = (1 + cycle * 7).toInt(),
            tirePsi = listOf(33f, 33f, 31f, 32.5f),
            tireTemp = listOf(31f, 30f, 29f, 30f),
            journeyKm = (t / 10).toFloat(),
            tripKm = (248 + t / 10).toFloat(),
            avgConsumption = 12.4f,
            batteryLevel = 86f,
            baroHpa = 950f,
            demo = true,
        )
    }

    private fun publish(s: VehicleState) {
        state = s
        main.post { listeners.forEach { it.onState(s) } }
    }
}

/**
 * يرصد التعبئة (ارتفاع نسبة الوقود 5% أو أكثر) ويحسب الاستهلاك منذ آخر تعبئة،
 * ومسافة الرحلة الحالية منذ تشغيل التطبيق.
 */
object FuelTracker {
    @Volatile var tripStartOdo = Float.NaN
        private set
    private var lastFuel = Float.NaN
    private var dropCount = 0
    private var savedFuel = Float.NaN

    fun update(context: Context, s: VehicleState) {
        if (s.demo) return
        val odo = s.odometerKm
        if (!odo.isNaN() && tripStartOdo.isNaN()) tripStartOdo = odo
        val fuel = s.fuelPercent
        if (fuel.isNaN() || odo.isNaN()) return
        val p = Prefs(context)
        if (p.refuelOdo < 0f) {
            p.refuelOdo = odo; p.refuelFuel = fuel
        }
        // آخر نسبة محفوظة تبقى بعد إطفاء السيارة، فنرصد التعبئة التي تمت والتطبيق متوقف
        if (lastFuel.isNaN()) lastFuel = p.lastFuel
        if (!lastFuel.isNaN() && fuel - lastFuel >= 5f) {
            // تأكيد الارتفاع في قراءتين متتاليتين حتى لا تخدعنا قراءة متذبذبة
            dropCount++
            if (dropCount >= 2) {
                // استهلاك الخزان السابق: من آخر تعبئة حتى قبل هذه التعبئة مباشرة
                val prevKm = odo - p.refuelOdo
                val prevLiters = (p.refuelFuel - lastFuel) / 100f * p.tankLiters
                val prevCons = if (prevKm >= 20f && prevLiters > 0f) prevLiters / prevKm * 100f else Float.NaN
                Logbook.onRefuel(context, odo, lastFuel, fuel, prevCons, prevKm)
                p.refuelOdo = odo; p.refuelFuel = fuel; p.refuelAt = System.currentTimeMillis()
                lastFuel = fuel; dropCount = 0
                p.lastFuel = fuel; savedFuel = fuel
            }
            return
        }
        dropCount = 0
        lastFuel = fuel
        if (savedFuel.isNaN() || kotlin.math.abs(fuel - savedFuel) >= 0.5f) { p.lastFuel = fuel; savedFuel = fuel }
    }

    fun tripKm(s: VehicleState): Float =
        if (tripStartOdo.isNaN() || s.odometerKm.isNaN()) Float.NaN else max(0f, s.odometerKm - tripStartOdo)

    /** لتر/100 كم منذ آخر تعبئة، أو NaN إذا كانت المسافة أقصر من 20 كم. */
    fun avgSinceRefuel(context: Context, s: VehicleState): Float {
        val p = Prefs(context)
        if (p.refuelOdo < 0f || s.odometerKm.isNaN() || s.fuelPercent.isNaN()) return Float.NaN
        val km = s.odometerKm - p.refuelOdo
        if (km < 20f) return Float.NaN
        val liters = (p.refuelFuel - s.fuelPercent) / 100f * p.tankLiters
        return if (liters <= 0f) Float.NaN else liters / km * 100f
    }

    fun kmSinceRefuel(context: Context, s: VehicleState): Float {
        val p = Prefs(context)
        return if (p.refuelOdo < 0f || s.odometerKm.isNaN()) Float.NaN else max(0f, s.odometerKm - p.refuelOdo)
    }
}
